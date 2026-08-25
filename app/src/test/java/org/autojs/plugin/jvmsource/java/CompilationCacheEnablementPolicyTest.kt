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
            CompilationCacheEnablement.DISABLED_SECURITY_POLICY,
            CompilationCacheEnablementPolicy.evaluate(
                providerDebuggable = true,
                compilerNonDumpable = true,
            ),
        )
        assertEquals(
            CompilationCacheEnablement.DISABLED_SECURITY_POLICY,
            CompilationCacheEnablementPolicy.evaluate(
                providerDebuggable = false,
                compilerNonDumpable = false,
            ),
        )
    }

    @Test
    fun authenticatorProvisioningIsSkippedByPolicyAndDegradesFailuresToDisabledCache() {
        var factoryCalls = 0
        val policyDisabled = CompilationCacheAuthenticatorProvisioning.provision(
            CompilationCacheEnablement.DISABLED_SECURITY_POLICY,
        ) {
            factoryCalls++
            CompilationCacheAuthenticators.processEpoch()
        }
        assertEquals(0, factoryCalls)
        assertEquals(CompilationCacheEnablement.DISABLED_SECURITY_POLICY, policyDisabled.enablement)
        assertEquals(null, policyDisabled.authenticator)

        val expectedAuthenticator = CompilationCacheAuthenticators.processEpoch()
        val enabled = CompilationCacheAuthenticatorProvisioning.provision(
            CompilationCacheEnablement.ENABLED,
        ) {
            factoryCalls++
            expectedAuthenticator
        }
        assertEquals(1, factoryCalls)
        assertEquals(CompilationCacheEnablement.ENABLED, enabled.enablement)
        assertEquals(expectedAuthenticator, enabled.authenticator)

        val unavailable = CompilationCacheAuthenticatorProvisioning.provision(
            CompilationCacheEnablement.ENABLED,
        ) {
            factoryCalls++
            throw java.security.GeneralSecurityException("Keystore unavailable")
        }
        assertEquals(2, factoryCalls)
        assertEquals(
            CompilationCacheEnablement.DISABLED_AUTHENTICATOR_UNAVAILABLE,
            unavailable.enablement,
        )
        assertEquals(null, unavailable.authenticator)
    }
}
