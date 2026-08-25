package org.autojs.plugin.jvmsource.java.service

import android.system.Os
import android.system.OsConstants

/** Defense in depth for compiler-only use of the installation-scoped cache HMAC key. */
internal object CompilerProcessMemoryIsolation {
    @Volatile
    private var nonDumpableEnforced = false

    fun enforceNonDumpable() {
        val setResult = Os.prctl(OsConstants.PR_SET_DUMPABLE, 0L, 0L, 0L, 0L)
        val observedDumpable = Os.prctl(OsConstants.PR_GET_DUMPABLE, 0L, 0L, 0L, 0L)
        CompilerProcessMemoryIsolationPolicy.requireDisabled(setResult, observedDumpable)
        nonDumpableEnforced = true
    }

    fun wasNonDumpableEnforced(): Boolean = nonDumpableEnforced
}
