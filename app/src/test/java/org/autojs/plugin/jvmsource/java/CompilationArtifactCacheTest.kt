package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class CompilationArtifactCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun publishesThenRevalidatesBothArtifactsOnEveryHit() {
        val fixture = fixture("hit")
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })

        val published = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        assertEquals(fixture.classIdentity, published.classIdentity)
        assertNotNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
        assertTrue(checkNotNull(published.programJar.parentFile).listFiles().orEmpty().all { !it.canWrite() })

        val materializedRoot = temporaryFolder.newFolder("materialized-hit")
        val materialized = cache.materialize(
            cached = published,
            destinationProgramJar = materializedRoot.resolve("program.jar"),
            destinationDexFile = materializedRoot.resolve("classes.dex"),
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        assertEquals(fixture.classIdentity, materialized.classIdentity)
        assertEquals(fixture.dexIdentity, materialized.dexIdentity)

        assertTrue(published.programJar.setWritable(true, true))
        published.programJar.appendBytes(byteArrayOf(1))
        assertNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
        assertFalse(checkNotNull(published.programJar.parentFile).exists())
    }

    @Test
    fun qualifiedNonMainEntrySurvivesPublicationLookupAndMaterialization() {
        val entryClassName = "com.example.scripts.ScriptEntry"
        val fixture = fixture("packaged-hit", entryClassName = entryClassName)
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })

        val published = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            entryClassName = entryClassName,
            ensureActive = {},
        )
        val hit = checkNotNull(cache.lookup(fixture.key, 24, 24, entryClassName))
        val destination = temporaryFolder.newFolder("packaged-materialized")
        val materialized = cache.materialize(
            cached = hit,
            destinationProgramJar = destination.resolve("program.jar"),
            destinationDexFile = destination.resolve("classes.dex"),
            requestMinApi = 24,
            deviceApi = 24,
            entryClassName = entryClassName,
            ensureActive = {},
        )

        assertEquals(published.classSummary, materialized.classSummary)
        assertTrue("Lcom/example/scripts/ScriptEntry;" in materialized.classSummary.dexDescriptors)
    }

    @Test
    fun multiDexSetIsAuthenticatedMaterializedAndInvalidatedAsOneEntry() {
        val fixture = fixture("multi-dex-hit")
        val secondDex = checkNotNull(fixture.dexFile.parentFile).resolve("classes2.dex").apply {
            writeBytes(CacheTestArtifacts.minimalDex("LMain\$\$ExternalSyntheticHelper;"))
        }
        val sourceDexFiles = listOf(fixture.dexFile, secondDex)
        val sourceDexIdentity = ProviderDexSetIdentity.fromFiles(sourceDexFiles)
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })

        val published = cache.publishSet(
            fixture.key,
            fixture.programJar,
            sourceDexFiles,
            fixture.summary,
            fixture.classIdentity,
            sourceDexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        assertEquals(listOf("classes.dex", "classes2.dex"), published.dexFiles.map(File::getName))
        assertEquals(sourceDexIdentity, published.dexSetIdentity)

        val hit = checkNotNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
        val destination = temporaryFolder.newFolder("multi-dex-materialized")
        val materialized = cache.materializeSet(
            cached = hit,
            destinationProgramJar = destination.resolve("program.jar"),
            destinationDexDirectory = destination,
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        assertEquals(sourceDexIdentity, materialized.dexSetIdentity)
        assertEquals(listOf("classes.dex", "classes2.dex"), materialized.dexFiles.map(File::getName))

        val cachedSecondDex = published.dexFiles[1]
        assertTrue(cachedSecondDex.setWritable(true, true))
        cachedSecondDex.appendBytes(byteArrayOf(1))
        assertNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
        assertFalse(checkNotNull(published.programJar.parentFile).exists())
    }

    @Test
    fun expirationAndRuntimeAdmissionAreFailClosedMisses() {
        var now = 10_000L
        val fixture = fixture("expiry")
        val cache = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { now },
            ttlMillis = 100L,
        )
        cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            requestMinApi = 24,
            deviceApi = 24,
            ensureActive = {},
        )
        now += 101L
        assertNull(cache.lookup(fixture.key, requestMinApi = 24, deviceApi = 24))
    }

    @Test
    fun interruptedPublicationLeavesNeitherEntryNorStagingDirectory() {
        val fixture = fixture("cancel")
        val cache = CompilationArtifactCache(fixture.cacheRoot, clockMillis = { 1_000L })
        var checks = 0

        assertThrows(Stopped::class.java) {
            cache.publish(
                fixture.key,
                fixture.programJar,
                fixture.dexFile,
                fixture.summary,
                fixture.classIdentity,
                fixture.dexIdentity,
                requestMinApi = 24,
                deviceApi = 24,
                ensureActive = { if (++checks >= 3) throw Stopped() },
            )
        }
        assertTrue(fixture.cacheRoot.list().orEmpty().isEmpty())
    }

    @Test
    fun staleSymlinkCleanupNeverFollowsItsTargetWhenSupported() {
        val fixture = fixture("symlink")
        assertTrue(fixture.cacheRoot.mkdirs())
        val outside = temporaryFolder.newFolder("outside-cache-target")
        val protected = outside.resolve("protected.txt").apply { writeText("keep") }
        val link = fixture.cacheRoot.resolve(".publish-00000000-0000-0000-0000-000000000001")
        if (runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isFailure) return

        assertNull(CompilationArtifactCache(fixture.cacheRoot).lookup(fixture.key, 24, 24))

        assertTrue(protected.isFile)
        assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun nestedLinkMakesFlatCleanupFailSafeWithoutFollowingOrRecursing() {
        val fixture = fixture("nested-cleanup-link")
        assertTrue(fixture.cacheRoot.mkdirs())
        val staging = fixture.cacheRoot.resolve(".publish-00000000-0000-0000-0000-000000000002")
            .apply { check(mkdir()) }
        val outside = temporaryFolder.newFolder("outside-nested-cleanup")
        val protected = outside.resolve("protected.txt").apply { writeText("keep") }
        val nested = staging.resolve("program.jar")
        if (runCatching { Files.createSymbolicLink(nested.toPath(), outside.toPath()) }.isFailure) return

        assertNull(CompilationArtifactCache(fixture.cacheRoot).lookup(fixture.key, 24, 24))

        assertTrue(protected.isFile)
        assertTrue(Files.isSymbolicLink(nested.toPath()))
        assertTrue(staging.isDirectory)
    }

    @Test
    fun ordinaryDigestCannotForgeCompilerProcessHmac() {
        val fixture = fixture("hmac-forgery")
        val cache = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_000L },
            authenticationKey = ByteArray(32) { 1 },
        )
        val published = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        val manifest = checkNotNull(published.programJar.parentFile).resolve("manifest.bin")
        assertTrue(manifest.setWritable(true, true))
        manifest.appendBytes(byteArrayOf(1))
        val ordinaryDigest = MessageDigest.getInstance("SHA-256").digest(manifest.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val completion = checkNotNull(manifest.parentFile).resolve("complete.hmac")
        assertTrue(completion.setWritable(true, true))
        completion.writeText(ordinaryDigest, Charsets.US_ASCII)
        assertTrue(manifest.setReadOnly())
        assertTrue(completion.setReadOnly())

        assertNull(cache.lookup(fixture.key, 24, 24))
    }

    @Test
    fun newCompilerProcessEpochRejectsOldEntries() {
        val fixture = fixture("process-epoch")
        val first = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_000L },
            authenticationKey = ByteArray(32) { 1 },
        )
        first.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )

        val restarted = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_001L },
            authenticationKey = ByteArray(32) { 2 },
        )
        assertNull(restarted.lookup(fixture.key, 24, 24))
        assertTrue(fixture.cacheRoot.list().orEmpty().isEmpty())
    }

    @Test
    fun enforcesCreationOrderEntryQuotaAndHardByteQuota() {
        var now = 1_000L
        val sharedRoot = temporaryFolder.newFolder("quota-root")
        val firstFixture = fixture("quota-first", sharedRoot)
        val secondFixture = fixture("quota-second", sharedRoot)
        val entryBounded = CompilationArtifactCache(
            sharedRoot,
            clockMillis = { now },
            maximumEntries = 1,
        )
        entryBounded.publish(
            firstFixture.key,
            firstFixture.programJar,
            firstFixture.dexFile,
            firstFixture.summary,
            firstFixture.classIdentity,
            firstFixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        now++
        entryBounded.publish(
            secondFixture.key,
            secondFixture.programJar,
            secondFixture.dexFile,
            secondFixture.summary,
            secondFixture.classIdentity,
            secondFixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        assertNull(entryBounded.lookup(firstFixture.key, 24, 24))
        assertNotNull(entryBounded.lookup(secondFixture.key, 24, 24))

        val byteRoot = temporaryFolder.newFolder("byte-quota-root")
        val byteFixture = fixture("byte-quota", byteRoot)
        val byteBounded = CompilationArtifactCache(byteRoot, maximumBytes = 1L)
        assertThrows(IllegalArgumentException::class.java) {
            byteBounded.publish(
                byteFixture.key,
                byteFixture.programJar,
                byteFixture.dexFile,
                byteFixture.summary,
                byteFixture.classIdentity,
                byteFixture.dexIdentity,
                24,
                24,
                ensureActive = {},
            )
        }
        assertTrue(byteRoot.list().orEmpty().isEmpty())
    }

    @Test
    fun unauthenticatedFakeEntriesNeverConsumeQuotaOrEvictAuthenticEntry() {
        val fixture = fixture("fake-quota")
        val cache = CompilationArtifactCache(
            fixture.cacheRoot,
            clockMillis = { 1_000L },
            maximumEntries = 1,
            authenticationKey = ByteArray(32) { 1 },
        )
        val authentic = cache.publish(
            fixture.key,
            fixture.programJar,
            fixture.dexFile,
            fixture.summary,
            fixture.classIdentity,
            fixture.dexIdentity,
            24,
            24,
            ensureActive = {},
        )
        val fakeKey = CompilationArtifactCacheKey(JvmSha256.digest("fake-key".toByteArray()))
        val fake = fixture.cacheRoot.resolve("entry-${fakeKey.hex}")
        assertTrue(checkNotNull(authentic.programJar.parentFile).copyRecursively(fake))
        fake.listFiles().orEmpty().forEach { assertTrue(it.setReadOnly()) }

        assertNotNull(cache.lookup(fixture.key, 24, 24))
        assertFalse(fake.exists())
    }

    private fun fixture(
        name: String,
        cacheRoot: File? = null,
        entryClassName: String = "Main",
    ): Fixture {
        val root = temporaryFolder.newFolder(name)
        val artifacts = root.resolve("artifacts").apply { check(mkdir()) }
        val programJar = artifacts.resolve("program.jar")
        val packageName = entryClassName.substringBeforeLast('.', missingDelimiterValue = "")
            .ifEmpty { null }
        val entrySimpleName = entryClassName.substringAfterLast('.')
        val entryClass = CacheTestArtifacts.java8EntryClass(
            root.resolve("compile").apply { check(mkdir()) },
            entrySimpleName = entrySimpleName,
            packageName = packageName,
        )
        val classEntry = entryClassName.replace('.', '/') + ".class"
        JarOutputStream(programJar.outputStream().buffered()).use { output ->
            output.putNextEntry(JarEntry(classEntry).apply { time = 0L })
            output.write(entryClass)
            output.closeEntry()
        }
        val descriptor = "L${entryClassName.replace('.', '/')};"
        val dexFile = artifacts.resolve("classes.dex").apply {
            writeBytes(CacheTestArtifacts.minimalDex(descriptor))
        }
        val classValidation = UserClassJarValidator.validate(programJar, entryClassName)
        return Fixture(
            cacheRoot = cacheRoot ?: root.resolve("cache"),
            key = CompilationArtifactCacheKey(JvmSha256.digest(name.toByteArray())),
            programJar = programJar,
            dexFile = dexFile,
            summary = classValidation.second,
            classIdentity = classValidation.first,
            dexIdentity = ProviderDigests.file(dexFile),
        )
    }

    private data class Fixture(
        val cacheRoot: File,
        val key: CompilationArtifactCacheKey,
        val programJar: File,
        val dexFile: File,
        val summary: UserClassJarSummary,
        val classIdentity: ProviderFileIdentity,
        val dexIdentity: ProviderFileIdentity,
    )

    private class Stopped : RuntimeException()
}
