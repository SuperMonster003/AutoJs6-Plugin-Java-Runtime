package org.autojs.plugin.jvmsource.java

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class CompilationCacheOperationLaneTest {
    @Test
    fun completedAndFailedOperationsReleaseTheSingleSlot() {
        CompilationCacheOperationLane(timeoutMillis = 1_000L).use { lane ->
            assertEquals(7, (lane.execute { 7 } as CompilationCacheOperationResult.Success).value)
            val failed = lane.execute<Int> { error("expected") }
            assertTrue(failed is CompilationCacheOperationResult.Unavailable)
            assertEquals(
                CompilationCacheOperationFailure.FAILED,
                (failed as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertEquals(9, (lane.execute { 9 } as CompilationCacheOperationResult.Success).value)
        }
    }

    @Test
    fun busyLaneRejectsWithoutQueueingASecondOperation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicReference<CompilationCacheOperationResult<Int>>()
        CompilationCacheOperationLane(timeoutMillis = 2_000L).use { lane ->
            val caller = Thread {
                first.set(
                    lane.execute {
                        entered.countDown()
                        check(release.await(1, TimeUnit.SECONDS))
                        1
                    },
                )
            }
            caller.start()
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val started = System.nanoTime()
            val rejected = lane.execute { 2 }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(
                CompilationCacheOperationFailure.REJECTED,
                (rejected as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertTrue("busy rejection took $elapsedMillis ms", elapsedMillis < 500L)
            release.countDown()
            caller.join(1_000L)
            assertEquals(1, (first.get() as CompilationCacheOperationResult.Success).value)
        }
    }

    @Test
    fun timeoutPoisonsTheWorkerAndRequestsFailFastDuringCooldown() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CompilationCacheOperationLane(timeoutMillis = 50L).use { lane ->
            val worker = lane.workerForTest()
            val timedOut = lane.execute {
                entered.countDown()
                while (true) {
                    try {
                        if (release.await(10, TimeUnit.MILLISECONDS)) break
                    } catch (_: InterruptedException) {
                        // Model platform I/O that does not cooperate with interruption.
                    }
                }
                1
            }
            assertTrue(entered.count == 0L)
            assertEquals(
                CompilationCacheOperationFailure.TIMED_OUT,
                (timedOut as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertTrue(lane.isPoisonedForTest())
            assertTrue(!lane.hasPendingForTest())
            val started = System.nanoTime()
            val afterTimeout = lane.execute { 2 }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(
                CompilationCacheOperationFailure.POISONED,
                (afterTimeout as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertTrue("poisoned rejection took $elapsedMillis ms", elapsedMillis < 500L)
            assertSame(worker, lane.workerForTest())
            release.countDown()
        }
    }

    @Test
    fun timeoutThenCooldownRebuildsOnceAndBecomesUsable() {
        val clockNanos = AtomicLong(0L)
        CompilationCacheOperationLane(
            timeoutMillis = 50L,
            recoveryCooldownMillis = 100L,
            monotonicNanos = clockNanos::get,
        ).use { lane ->
            val originalWorker = lane.workerForTest()

            assertTimeout(lane)
            originalWorker.join(1_000L)
            assertTrue("Original cache worker did not stop", !originalWorker.isAlive)
            assertEquals(
                CompilationCacheOperationFailure.POISONED,
                (lane.execute { 1 } as CompilationCacheOperationResult.Unavailable).failure,
            )

            clockNanos.set(TimeUnit.MILLISECONDS.toNanos(100L))
            assertEquals(2, (lane.execute { 2 } as CompilationCacheOperationResult.Success).value)
            assertNotSame(originalWorker, lane.workerForTest())
            assertTrue(lane.recoveryUsedForTest())
            assertTrue(!lane.isPoisonedForTest())
        }
    }

    @Test
    fun elapsedCooldownCannotOverlapAnUninterruptibleOldWorker() {
        val clockNanos = AtomicLong(0L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CompilationCacheOperationLane(
            timeoutMillis = 50L,
            recoveryCooldownMillis = 100L,
            monotonicNanos = clockNanos::get,
        ).use { lane ->
            val originalWorker = lane.workerForTest()
            val timedOut = lane.execute {
                entered.countDown()
                while (true) {
                    try {
                        if (release.await(10L, TimeUnit.MILLISECONDS)) break
                    } catch (_: InterruptedException) {
                        // Model platform I/O that remains live beyond interruption and cooldown.
                    }
                }
                1
            }
            assertTrue(entered.count == 0L)
            assertEquals(
                CompilationCacheOperationFailure.TIMED_OUT,
                (timedOut as CompilationCacheOperationResult.Unavailable).failure,
            )

            clockNanos.set(TimeUnit.MILLISECONDS.toNanos(100L))
            val blockedRecovery = lane.execute { 2 }
            assertEquals(
                CompilationCacheOperationFailure.POISONED,
                (blockedRecovery as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertSame(originalWorker, lane.workerForTest())
            assertTrue(!lane.recoveryUsedForTest())

            release.countDown()
            originalWorker.join(1_000L)
            assertTrue("Original cache worker did not stop after release", !originalWorker.isAlive)
            assertEquals(3, (lane.execute { 3 } as CompilationCacheOperationResult.Success).value)
            assertNotSame(originalWorker, lane.workerForTest())
        }
    }

    @Test
    fun secondTimeoutAfterRecoveryPermanentlyPoisonsTheLane() {
        val clockNanos = AtomicLong(0L)
        CompilationCacheOperationLane(
            timeoutMillis = 50L,
            recoveryCooldownMillis = 100L,
            monotonicNanos = clockNanos::get,
        ).use { lane ->
            val originalWorker = lane.workerForTest()
            assertTimeout(lane)
            originalWorker.join(1_000L)
            assertTrue("Original cache worker did not stop", !originalWorker.isAlive)

            clockNanos.set(TimeUnit.MILLISECONDS.toNanos(100L))
            assertEquals(1, (lane.execute { 1 } as CompilationCacheOperationResult.Success).value)
            val replacementWorker = lane.workerForTest()
            assertNotSame(originalWorker, replacementWorker)

            assertTimeout(lane)
            replacementWorker.join(1_000L)
            assertTrue("Replacement cache worker did not stop", !replacementWorker.isAlive)
            clockNanos.set(TimeUnit.SECONDS.toNanos(10L))

            val permanentlyPoisoned = lane.execute { 2 }
            assertEquals(
                CompilationCacheOperationFailure.POISONED,
                (permanentlyPoisoned as CompilationCacheOperationResult.Unavailable).failure,
            )
            assertSame(replacementWorker, lane.workerForTest())
            assertTrue(lane.isPoisonedForTest())
        }
    }

    @Test
    fun closeBeforeAdmissionReturnsClosedWithoutRunningTheOperation() {
        val lane = CompilationCacheOperationLane(timeoutMillis = 5_000L)
        lane.close()
        var invoked = false

        val result = lane.execute { invoked = true }

        assertEquals(
            CompilationCacheOperationFailure.CLOSED,
            (result as CompilationCacheOperationResult.Unavailable).failure,
        )
        assertTrue(!invoked)
    }

    @Test
    fun closeWhileActiveCancelsTheTrackedTaskAndUnblocksTheCaller() {
        val entered = CountDownLatch(1)
        val result = AtomicReference<CompilationCacheOperationResult<Int>>()
        val lane = CompilationCacheOperationLane(timeoutMillis = 5_000L)
        val caller = Thread {
            result.set(
                lane.execute {
                    entered.countDown()
                    CountDownLatch(1).await()
                    1
                },
            )
        }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        lane.close()
        assertTrue(!lane.hasPendingForTest())
        caller.join(1_000L)

        assertTrue(!caller.isAlive)
        assertEquals(
            CompilationCacheOperationFailure.CLOSED,
            (result.get() as CompilationCacheOperationResult.Unavailable).failure,
        )
    }

    private fun assertTimeout(lane: CompilationCacheOperationLane) {
        val entered = CountDownLatch(1)
        val result = lane.execute {
            entered.countDown()
            try {
                CountDownLatch(1).await()
            } catch (_: InterruptedException) {
                // Cooperative stand-in for a cache operation that stops after cancellation.
            }
            1
        }
        assertTrue(entered.count == 0L)
        assertEquals(
            CompilationCacheOperationFailure.TIMED_OUT,
            (result as CompilationCacheOperationResult.Unavailable).failure,
        )
    }
}
