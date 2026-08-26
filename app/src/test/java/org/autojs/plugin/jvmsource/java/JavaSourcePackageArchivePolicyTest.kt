package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourcePackageCodec
import org.autojs.plugin.jvmsource.api.JvmSourcePayloadKind
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class JavaSourcePackageArchivePolicyTest {
    private val sources = linkedMapOf(
        "demo/Main.java" to "\uFEFFpackage demo; public final class Main { Helper value; }".toByteArray(),
        "demo/Helper.java" to "package demo; final class Helper {}".toByteArray(),
    )

    @Test
    fun validatesEverySourceAndBuildsOneNormalizedPackageIdentity() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        val validated = JavaSourcePackageArchivePolicy.validate(archive, request(archive))

        assertEquals("demo/Main.java", validated.entrySourcePath)
        assertEquals(listOf("demo/Helper.java", "demo/Main.java"), validated.sources.map { it.sourcePath })
        assertArrayEquals(
            "package demo; public final class Main { Helper value; }".toByteArray(),
            validated.sources.single { it.sourcePath == "demo/Main.java" }.normalizedBytes,
        )
        val normalizedArchive = JvmSourcePackageCodec.encode(
            validated.entrySourcePath,
            validated.sources.associateTo(linkedMapOf()) { it.sourcePath to it.normalizedBytes },
        )
        assertEquals(JvmSha256.digest(normalizedArchive), validated.normalizedPackageSha256)
    }

    @Test
    fun rejectsRequestCountTotalAndEntryClaimsThatDifferFromManifest() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        listOf(
            request(archive).copy(sourceFileCount = 3),
            request(archive).copy(sourceContentBytes = sources.values.sumOf { it.size.toLong() } - 1L),
            request(archive).copy(entryClassName = "other.Main"),
        ).forEach { invalid ->
            val failure = assertThrows(JavaProviderFailure::class.java) {
                JavaSourcePackageArchivePolicy.validate(archive, invalid)
            }
            assertEquals(JvmSourceErrorCode.ARTIFACT_INVALID, failure.code)
            assertEquals(JvmSourceFailurePhase.INPUT, failure.phase)
        }
    }

    @Test
    fun rejectsTraversalDuplicateAndCompressedBombArchivesBeforeWorkspaceExtraction() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        val traversal = archive.copyOf().also {
            it.patchZipNames("demo/Helper.java", "../evil/Bad.java")
        }
        assertArtifactRejected(traversal)

        val twinSources = linkedMapOf(
            "demo/Main.java" to sources.getValue("demo/Main.java"),
            "demo/Twin.java" to sources.getValue("demo/Helper.java"),
        )
        val duplicate = JvmSourcePackageCodec.encode("demo/Main.java", twinSources).copyOf().also {
            it.patchZipNames("demo/Twin.java", "demo/Main.java")
        }
        assertArtifactRejected(duplicate, sourceContentBytes = twinSources.values.sumOf { it.size.toLong() })

        val compressedBomb = archive.copyOf().also { bytes ->
            val occurrences = bytes.asciiOccurrences("demo/Helper.java")
            val localHeader = occurrences[1] - 30
            val centralHeader = occurrences[2] - 46
            bytes.putU16(localHeader + 8, 8)
            bytes.putU16(centralHeader + 10, 8)
            bytes.putU32(localHeader + 22, 0xffff_ffffL)
            bytes.putU32(centralHeader + 24, 0xffff_ffffL)
        }
        assertArtifactRejected(compressedBomb)
    }

    @Test
    fun rejectsACompilationUnitWhoseLogicalPathDisagreesWithItsPackage() {
        val mismatchedSources = linkedMapOf(
            "demo/Main.java" to "package demo; public final class Main {}".toByteArray(),
            "demo/Helper.java" to "package other; final class Helper {}".toByteArray(),
        )
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", mismatchedSources)
        val failure = assertThrows(JavaProviderFailure::class.java) {
            JavaSourcePackageArchivePolicy.validate(
                archive,
                request(archive, sourceContentBytes = mismatchedSources.values.sumOf { it.size.toLong() }),
            )
        }
        assertEquals(JvmSourceErrorCode.INVALID_REQUEST, failure.code)
        assertEquals(JvmSourceFailurePhase.INPUT, failure.phase)
    }

    private fun assertArtifactRejected(
        archive: ByteArray,
        sourceContentBytes: Long = sources.values.sumOf { it.size.toLong() },
    ) {
        val failure = assertThrows(JavaProviderFailure::class.java) {
            JavaSourcePackageArchivePolicy.validate(
                archive,
                request(archive, sourceContentBytes = sourceContentBytes),
            )
        }
        assertEquals(JvmSourceErrorCode.ARTIFACT_INVALID, failure.code)
        assertEquals(JvmSourceFailurePhase.INPUT, failure.phase)
    }

    private fun request(
        archive: ByteArray,
        sourceContentBytes: Long = sources.values.sumOf { it.size.toLong() },
    ) = JvmSourceRequest(
        requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT) { it.toByte() }),
        protocolVersion = JvmSourceContract.SOURCE_PACKAGE_PROTOCOL_VERSION,
        language = JvmSourceLanguage.JAVA,
        sourceFileName = "Main.java",
        sourceSizeBytes = archive.size.toLong(),
        sourceSha256 = JvmSha256.digest(archive),
        expectedToolchainFingerprint = JvmSha256.digest("toolchain".toByteArray()),
        entryClassName = "demo.Main",
        minApi = JvmSourceContract.MIN_ANDROID_API,
        timeoutMillis = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
        maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
        maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
        diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
        sourcePayloadKind = JvmSourcePayloadKind.SOURCE_ARCHIVE,
        sourceFileCount = 2,
        sourceContentBytes = sourceContentBytes,
    )

    private fun ByteArray.patchZipNames(from: String, to: String) {
        require(from.length == to.length)
        val occurrences = asciiOccurrences(from)
        require(occurrences.size == 3)
        val replacement = to.toByteArray(StandardCharsets.US_ASCII)
        occurrences.drop(1).forEach { offset -> replacement.copyInto(this, offset) }
    }

    private fun ByteArray.asciiOccurrences(value: String): List<Int> {
        val needle = value.toByteArray(StandardCharsets.US_ASCII)
        return indices.filter { start ->
            start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value and 0xff).toByte()
        this[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Long) {
        putU16(offset, (value and 0xffff).toInt())
        putU16(offset + 2, ((value ushr 16) and 0xffff).toInt())
    }
}
