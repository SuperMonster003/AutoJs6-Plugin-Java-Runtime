package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmJavaSourceLayoutPolicy
import org.autojs.plugin.jvmsource.api.JvmSourcePackagePathPolicy

internal object JavaSourcePolicy {
    const val CACHE_CHARSET_POLICY = "UTF-8-strict-report-v1"
    const val CACHE_NORMALIZATION_POLICY = "strip-leading-bom-and-reencode-UTF-8-per-file-v2"

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

    fun decodeAndValidatePackageFile(
        bytes: ByteArray,
        sourcePath: String,
        expectedEntryClassName: String? = null,
    ): String {
        try {
            JvmSourcePackagePathPolicy.requireSourcePath(sourcePath)
        } catch (error: Throwable) {
            throw invalid("Java source package path is invalid", error)
        }
        val sourceFileName = sourcePath.substringAfterLast('/')
        val simpleName = sourceFileName.removeSuffix(".java")
        val inspection = try {
            JvmJavaSourceLayoutPolicy.inspect(bytes, simpleName)
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.INVALID_REQUEST,
                org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.INPUT,
                "Java source failed lexical package validation",
                error,
            )
        }
        val expectedPath = try {
            JvmSourcePackagePathPolicy.sourcePathForClassName(inspection.layout.entryClassName)
        } catch (error: Throwable) {
            throw invalid("Java source package declaration exceeds the path profile", error)
        }
        if (sourcePath != expectedPath) {
            throw invalid("Java source package path does not match its declared package")
        }
        if (expectedEntryClassName != null && inspection.layout.entryClassName != expectedEntryClassName) {
            throw invalid("Java source package entry does not match the requested entry class")
        }
        return inspection.normalizedSource
    }

    private fun invalid(message: String, cause: Throwable? = null): JavaProviderFailure = JavaProviderFailure(
        org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.INVALID_REQUEST,
        org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.INPUT,
        message,
        cause,
    )

}
