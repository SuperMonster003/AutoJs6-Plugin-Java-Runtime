package org.autojs.plugin.jvmsource.java

import java.util.concurrent.atomic.AtomicReference

internal class SingleActiveSessionGate<T : Any> {
    private val active = AtomicReference<T?>()

    fun tryAcquire(session: T): Boolean = active.compareAndSet(null, session)

    fun release(session: T): Boolean = active.compareAndSet(session, null)
}
