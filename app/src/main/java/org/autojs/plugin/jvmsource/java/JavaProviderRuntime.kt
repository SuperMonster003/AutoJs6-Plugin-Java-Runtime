package org.autojs.plugin.jvmsource.java

import android.content.Context
import org.autojs.plugin.jvmsource.api.JvmIsolationCapability
import org.autojs.plugin.jvmsource.api.JvmProviderInfo
import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSourceCapabilities
import org.autojs.plugin.jvmsource.api.JvmSourceCompilerFamily
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmToolchainFingerprint
import org.autojs.plugin.jvmsource.java.BuildConfig

internal object JavaProviderRuntime {
    const val PROVIDER_ID = "ecj-java"
    val PROVIDER_VERSION_NAME: String
        get() = BuildConfig.VERSION_NAME
    val PROVIDER_VERSION_CODE: Long
        get() = BuildConfig.VERSION_CODE.toLong()
    val protocolVersion = JvmProtocolVersion(
        JvmSourceContract.PROTOCOL_MAJOR,
        JvmSourceContract.PROTOCOL_MINOR,
    )

    fun providerInfo(): JvmProviderInfo = JvmProviderInfo(
        protocolMin = protocolVersion,
        protocolMax = protocolVersion,
        providerId = PROVIDER_ID,
        providerVersionName = PROVIDER_VERSION_NAME,
        providerVersionCode = PROVIDER_VERSION_CODE,
        entryApiVersion = JvmSourceContract.ENTRY_API_VERSION,
        minHostVersionCode = BuildConfig.MIN_HOST_VERSION_CODE,
    )

    fun capabilities(context: Context): JvmSourceCapabilities {
        val environment = JavaProviderEnvironment.get(context)
        val toolchainFingerprint = JvmToolchainFingerprint.compute(
            language = JvmSourceLanguage.JAVA,
            sourceCompilerFamily = JvmSourceCompilerFamily.ECJ,
            sourceCompilerVersion = BuildConfig.ECJ_VERSION,
            d8Version = BuildConfig.D8_VERSION,
            runtimeLibraryFingerprint = environment.runtimeLibraryFingerprint,
        )
        return JvmSourceCapabilities(
            languages = listOf(JvmSourceLanguage.JAVA),
            isolationCapabilities = JvmIsolationCapability.entries,
            sourceCompilerFamily = JvmSourceCompilerFamily.ECJ,
            sourceCompilerVersion = BuildConfig.ECJ_VERSION,
            d8Version = BuildConfig.D8_VERSION,
            runtimeLibraryFingerprint = environment.runtimeLibraryFingerprint,
            toolchainFingerprint = toolchainFingerprint,
            maxSourceBytes = JvmSourceContract.MAX_SOURCE_PAYLOAD_BYTES,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            maxDiagnosticBytes = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            maxTimeoutMillis = JvmSourceContract.MAX_TIMEOUT_MILLIS,
            maxConcurrentSessions = JvmSourceContract.MAX_CONCURRENT_SESSIONS,
            scriptCapabilities = JvmScriptCapability.entries,
        )
    }
}
