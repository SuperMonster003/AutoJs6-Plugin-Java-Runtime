package org.autojs.plugin.jvmsource.java.service

/** Prevents worker death from acknowledging retirement before provider resources are closed. */
internal class ProviderResourceCleanupBarrier {
    enum class State {
        ACTIVE,
        CLOSING,
        RESOURCES_READY,
    }

    private var state = State.ACTIVE
    private var workerDeathObserved = false
    private var finalizationClaimed = false

    @Synchronized
    fun beginCleanup(): Boolean {
        if (state != State.ACTIVE) return false
        state = State.CLOSING
        return true
    }

    /** Claims finalization once, only after resource cleanup was published as complete. */
    @Synchronized
    fun observeWorkerDeath(): Boolean {
        workerDeathObserved = true
        return state == State.RESOURCES_READY && claimFinalization()
    }

    /**
     * Publishes descriptor/workspace closure. Returns whether exact-session finalization may run.
     */
    @Synchronized
    fun resourcesClosed(waitForWorkerDeath: Boolean): Boolean {
        check(state == State.CLOSING)
        state = State.RESOURCES_READY
        return (!waitForWorkerDeath || workerDeathObserved) && claimFinalization()
    }

    /** Called under the same monitor by both resource closure and worker-death callbacks. */
    private fun claimFinalization(): Boolean {
        if (finalizationClaimed) return false
        finalizationClaimed = true
        return true
    }

    @Synchronized
    fun snapshot(): State = state
}
