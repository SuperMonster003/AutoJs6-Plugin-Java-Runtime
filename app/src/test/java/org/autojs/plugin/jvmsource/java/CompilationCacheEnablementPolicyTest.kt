package org.autojs.plugin.jvmsource.java

import org.junit.Assert.assertEquals
import org.junit.Test

class CompilationCacheEnablementPolicyTest {
    @Test
    fun enablesOnlyNonDebuggableNonDumpableCompilerProcess() {
        assertEquals(
            CompilationCacheEnablement.ENABLED,
            CompilationCacheEnablementPolicy.evaluate(
                providerDebuggable = false,
                compilerNonDumpable = true,
            ),
        )
        assertEquals(
            CompilationCacheEnablement.DISABLED,
            CompilationCacheEnablementPolicy.evaluate(
                providerDebuggable = true,
                compilerNonDumpable = true,
            ),
        )
        assertEquals(
            CompilationCacheEnablement.DISABLED,
            CompilationCacheEnablementPolicy.evaluate(
                providerDebuggable = false,
                compilerNonDumpable = false,
            ),
        )
    }
}
