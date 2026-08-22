package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayoutPolicy

internal object JavaSourcePolicy {
    const val CACHE_CHARSET_POLICY = "UTF-8-strict-report-v1"
    const val CACHE_NORMALIZATION_POLICY = "strip-leading-bom-and-reencode-UTF-8-v1"

    fun decodeAndValidate(
        bytes: ByteArray,
        sourceFileName: String = "Main.java",
        entryClassName: String = "Main",
    ): String {
        val entrySimpleName = entryClassName.substringAfterLast('.')
        if (sourceFileName != "$entrySimpleName.java") {
            throw invalid("Java source file name does not match the requested entry class")
        }
        val inspection = try {
            JvmJavaSourceLayoutPolicy.inspect(bytes, entrySimpleName)
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.INVALID_REQUEST,
                org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.INPUT,
                "Java source failed lexical package validation",
                error,
            )
        }
        if (inspection.layout.sourceFileName != sourceFileName ||
            inspection.layout.entryClassName != entryClassName
        ) {
            throw invalid("Java package does not match the requested entry class")
        }
        return inspection.normalizedSource
    }

    private fun invalid(message: String): JavaProviderFailure = JavaProviderFailure(
        org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.INVALID_REQUEST,
        org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.INPUT,
        message,
    )

}
