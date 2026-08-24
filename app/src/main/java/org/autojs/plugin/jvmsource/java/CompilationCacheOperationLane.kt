package org.autojs.plugin.jvmsource.java

import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal enum class CompilationCacheOperationFailure {
    REJECTED,
    TIMED_OUT,
    INTERRUPTED,
    FAILED,
    POISONED,
    CLOSED,
}

internal sealed interface CompilationCacheOperationResult<out T> {
    data class Success<T>(val value: T) : CompilationCacheOperationResult<T>
    data class Unavailable(
        val failure: CompilationCacheOperationFailure,
        val cause: Throwable? = null,
    ) : CompilationCacheOperationResult<Nothing>
}

/**
 * One active daemon thread and one admitted operation, with no pending-operation queue.
 *
 * A timed-out or caller-interrupted operation may be stuck in uninterruptible platform I/O. The
 * first poisoned worker may be rebuilt once, but only after a cooldown and proof that the old task
 * and thread have both stopped. A second poison is permanent. This preserves one-thread/one-slot
 * cache access without accumulating tasks or overlapping an abandoned I/O operation.
 */
internal class CompilationCacheOperationLane internal constructor(
    timeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
    threadName: String = "jvm-source-compilation-cache",
    recoveryCooldownMillis: Long = DEFAULT_RECOVERY_COOLDOWN_MILLIS,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : Closeable {
    private val timeoutMillis = timeoutMillis.also { require(it > 0L) }
    private val workerName = threadName.also { require(it.isNotBlank()) }
    private val recoveryCooldownNanos = TimeUnit.MILLISECONDS.toNanos(recoveryCooldownMillis).also {
        require(it > 0L) { "Recovery cooldown must be positive" }
    }
    private val admissionLock = ReentrantLock()
    private val signal = Semaphore(0)
    private val occupied = AtomicBoolean(false)
    private val poisoned = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var recoveryUsed = false
    private var poisonedAtNanos: Long? = null

    private val pending = AtomicReference<FutureTask<*>?>(null)
    private val active = AtomicReference<FutureTask<*>?>(null)
    private val worker = AtomicReference<Thread>()

    init {
        newWorker().also { initial ->
            worker.set(initial)
            initial.start()
        }
    }

    fun <T> execute(operation: () -> T): CompilationCacheOperationResult<T> {
        val task = FutureTask<T> {
            try {
                operation()
            } finally {
                // Release before FutureTask publishes completion so a sequential caller cannot
                // observe a spurious busy result between two completed cache operations.
                occupied.set(false)
            }
        }
        admissionLock.withLock {
            if (closed.get()) return unavailable(CompilationCacheOperationFailure.CLOSED)
            if (poisoned.get() && !tryRecoverLocked()) {
                return unavailable(CompilationCacheOperationFailure.POISONED)
            }
            if (!occupied.compareAndSet(false, true)) {
                return unavailable(CompilationCacheOperationFailure.REJECTED)
            }
            check(pending.compareAndSet(null, task)) {
                "Compilation cache lane admitted more than one operation"
            }
            signal.release()
        }

        return try {
            CompilationCacheOperationResult.Success(task.get(timeoutMillis, TimeUnit.MILLISECONDS))
        } catch (_: TimeoutException) {
            poisonAndCancel(task)
            unavailable(CompilationCacheOperationFailure.TIMED_OUT)
        } catch (_: InterruptedException) {
            poisonAndCancel(task)
            Thread.currentThread().interrupt()
            unavailable(CompilationCacheOperationFailure.INTERRUPTED)
        } catch (_: CancellationException) {
            unavailable(terminalFailure())
        } catch (error: ExecutionException) {
            CompilationCacheOperationResult.Unavailable(
                if (closed.get() || poisoned.get()) terminalFailure()
                else CompilationCacheOperationFailure.FAILED,
                error.cause ?: error,
            )
        }
    }

    override fun close() {
        admissionLock.withLock {
            if (!closed.compareAndSet(false, true)) return
            poisoned.set(true)
            pending.getAndSet(null)?.cancel(true)
            active.get()?.cancel(true)
            signal.release()
        }
        worker.get().interrupt()
    }

    internal fun isPoisonedForTest(): Boolean = poisoned.get()
    internal fun recoveryUsedForTest(): Boolean = admissionLock.withLock { recoveryUsed }
    internal fun hasPendingForTest(): Boolean = pending.get() != null
    internal fun workerForTest(): Thread = worker.get()

    private fun runWorker() {
        while (!closed.get() && !poisoned.get()) {
            try {
                signal.acquire()
            } catch (_: InterruptedException) {
                if (closed.get() || poisoned.get()) return
                continue
            }
            if (closed.get() || poisoned.get()) return
            val task = admissionLock.withLock {
                if (closed.get() || poisoned.get()) return
                pending.getAndSet(null)?.also { admitted -> active.set(admitted) }
            } ?: continue
            try {
                task.run()
            } finally {
                active.compareAndSet(task, null)
            }
        }
    }

    private fun poisonAndCancel(task: FutureTask<*>) {
        val workerToInterrupt = admissionLock.withLock {
            poisoned.set(true)
            poisonedAtNanos = monotonicNanos()
            if (pending.compareAndSet(task, null)) occupied.set(false)
            task.cancel(true)
            worker.get()
        }
        workerToInterrupt.interrupt()
    }

    /** Called with [admissionLock] held. Recovery never overlaps the previous worker generation. */
    private fun tryRecoverLocked(): Boolean {
        if (recoveryUsed) return false
        val poisonedAt = poisonedAtNanos ?: return false
        if (monotonicNanos() - poisonedAt < recoveryCooldownNanos) return false
        val previous = worker.get()
        if (occupied.get() || pending.get() != null || active.get() != null || previous.isAlive) return false

        recoveryUsed = true
        poisonedAtNanos = null
        signal.drainPermits()
        val replacement = newWorker()
        worker.set(replacement)
        poisoned.set(false)
        replacement.start()
        return true
    }

    private fun newWorker(): Thread = Thread(::runWorker, workerName).apply { isDaemon = true }

    private fun terminalFailure(): CompilationCacheOperationFailure =
        if (closed.get()) CompilationCacheOperationFailure.CLOSED
        else CompilationCacheOperationFailure.POISONED

    private fun unavailable(failure: CompilationCacheOperationFailure) =
        CompilationCacheOperationResult.Unavailable(failure)

    companion object {
        internal const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 1_000L
        internal const val DEFAULT_RECOVERY_COOLDOWN_MILLIS = 5_000L
    }
}
