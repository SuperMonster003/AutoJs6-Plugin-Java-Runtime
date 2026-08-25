package org.autojs.plugin.jvmsource.java

internal enum class CompilationCacheEnablement {
    ENABLED,
    DISABLED_SECURITY_POLICY,
    DISABLED_AUTHENTICATOR_UNAVAILABLE,
}

/** Cache authentication is admitted only for a release-like, non-dumpable compiler process. */
internal object CompilationCacheEnablementPolicy {
    fun evaluate(providerDebuggable: Boolean, compilerNonDumpable: Boolean): CompilationCacheEnablement =
        if (!providerDebuggable && compilerNonDumpable) {
            CompilationCacheEnablement.ENABLED
        } else {
            CompilationCacheEnablement.DISABLED_SECURITY_POLICY
        }
}

internal data class CompilationCacheAuthenticatorProvisioning(
    val enablement: CompilationCacheEnablement,
    val authenticator: CompilationCacheAuthenticator?,
) {
    init {
        require((enablement == CompilationCacheEnablement.ENABLED) == (authenticator != null))
    }

    companion object {
        fun provision(
            policyDecision: CompilationCacheEnablement,
            factory: () -> CompilationCacheAuthenticator,
        ): CompilationCacheAuthenticatorProvisioning {
            if (policyDecision != CompilationCacheEnablement.ENABLED) {
                return CompilationCacheAuthenticatorProvisioning(policyDecision, authenticator = null)
            }
            return try {
                CompilationCacheAuthenticatorProvisioning(
                    CompilationCacheEnablement.ENABLED,
                    factory(),
                )
            } catch (_: Exception) {
                CompilationCacheAuthenticatorProvisioning(
                    CompilationCacheEnablement.DISABLED_AUTHENTICATOR_UNAVAILABLE,
                    authenticator = null,
                )
            }
        }
    }
}
