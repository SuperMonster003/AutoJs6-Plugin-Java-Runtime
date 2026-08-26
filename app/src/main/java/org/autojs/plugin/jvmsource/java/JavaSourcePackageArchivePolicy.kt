package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourcePackageCodec
import org.autojs.plugin.jvmsource.api.JvmSourcePackagePathPolicy
import org.autojs.plugin.jvmsource.api.JvmSourceRequest

internal data class ValidatedJavaPackageSource(
    val sourcePath: String,
    val normalizedBytes: ByteArray,
)

internal data class ValidatedJavaSourcePackage(
    val entrySourcePath: String,
    val sources: List<ValidatedJavaPackageSource>,
    val normalizedPackageSha256: JvmSha256,
)

/** Validates the complete archive in memory before any path is materialized in the workspace. */
internal object JavaSourcePackageArchivePolicy {
    fun validate(archiveBytes: ByteArray, request: JvmSourceRequest): ValidatedJavaSourcePackage {
        val decoded = try {
            JvmSourcePackageCodec.decode(archiveBytes)
        } catch (error: Throwable) {
            throw artifactInvalid("Java source archive failed canonical framing or manifest validation", error)
        }
        if (decoded.sourceFileCount != request.sourceFileCount ||
            decoded.totalSourceBytes != request.sourceContentBytes
        ) {
            throw artifactInvalid("Java source archive differs from its request count or content-size claims")
        }
        val expectedEntryPath = try {
            JvmSourcePackagePathPolicy.sourcePathForClassName(request.entryClassName)
        } catch (error: Throwable) {
            throw artifactInvalid("Java source archive entry class cannot form a canonical path", error)
        }
        if (decoded.entrySourcePath != expectedEntryPath) {
            throw artifactInvalid("Java source archive manifest entry differs from the requested entry class")
        }
        val normalized = decoded.sourcePaths.map { path ->
            val text = JavaSourcePolicy.decodeAndValidatePackageFile(
                bytes = decoded.sourceBytes(path),
                sourcePath = path,
                expectedEntryClassName = request.entryClassName.takeIf { path == expectedEntryPath },
            )
            ValidatedJavaPackageSource(path, text.toByteArray(Charsets.UTF_8))
        }
        val normalizedArchive = try {
            JvmSourcePackageCodec.encode(
                expectedEntryPath,
                normalized.associateTo(linkedMapOf()) { it.sourcePath to it.normalizedBytes },
            )
        } catch (error: Throwable) {
            throw artifactInvalid("Normalized Java source package exceeds the canonical profile", error)
        }
        return ValidatedJavaSourcePackage(
            entrySourcePath = expectedEntryPath,
            sources = normalized,
            normalizedPackageSha256 = JvmSha256.digest(normalizedArchive),
        )
    }

    private fun artifactInvalid(message: String, cause: Throwable? = null) = JavaProviderFailure(
        JvmSourceErrorCode.ARTIFACT_INVALID,
        JvmSourceFailurePhase.INPUT,
        message,
        cause,
    )
}
