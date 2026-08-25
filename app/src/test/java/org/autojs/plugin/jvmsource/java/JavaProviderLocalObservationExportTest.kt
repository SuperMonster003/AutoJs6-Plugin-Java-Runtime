package org.autojs.plugin.jvmsource.java

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JavaProviderLocalObservationExportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun composedJsonContainsFiveOrderedPhasesWorkerStartupAndCacheCounters() {
        val record = completeRecord()

        assertEquals(7L, record.terminationDurationMillis)
        assertEquals(
            """{"schemaVersion":1,"sessionElapsedMillis":180,"workerStartupDurationMillis":25,"startProfile":{"compiler":"COLD","worker":"COLD"},"phasesMillis":{"COMPILE":10,"D8":20,"LOAD":30,"RUN":40,"TERMINATION":7},"cache":{"outcome":"MISS","missReason":"NOT_FOUND","hits":2,"misses":1,"publications":1,"publicationFailures":1,"missesByReason":{"NOT_FOUND":1,"INVALID_OR_EXPIRED":0,"CACHE_UNAVAILABLE":0,"MATERIALIZATION_FAILED":0,"CACHE_DISABLED":0,"PROVIDER_IDENTITY_UNAVAILABLE":0,"PROVIDER_IDENTITY_DRIFTED":0}}}""",
            JavaProviderLocalObservationJson.encode(record),
        )
    }

    @Test
    fun debugExporterAppendsCompleteJsonLinesAndRestartsAtTheSizeBound() {
        val filesDirectory = temporaryFolder.newFolder("debug-files")
        val record = completeRecord()
        val expectedLine = JavaProviderLocalObservationJson.encode(record)
        val encodedLineBytes = (expectedLine + '\n').toByteArray(Charsets.UTF_8).size.toLong()
        val exporter = JavaProviderLocalObservationExporterFactory.create(
            debugBuild = true,
            providerDebuggable = true,
            filesDirectory = filesDirectory,
            maxFileBytes = encodedLineBytes * 2L,
        )

        assertTrue(exporter.export(record))
        assertTrue(exporter.export(record))
        assertEquals(listOf(expectedLine, expectedLine), outputFile(filesDirectory).readLines())

        assertTrue(exporter.export(record))
        assertEquals(listOf(expectedLine), outputFile(filesDirectory).readLines())
    }

    @Test
    fun releaseAndNonDebuggableConfigurationsExportNothingAndCreateNoArtifacts() {
        val releaseFiles = temporaryFolder.newFolder("release-files")
        val releaseExporter = JavaProviderLocalObservationExporterFactory.create(
            debugBuild = false,
            providerDebuggable = true,
            filesDirectory = releaseFiles,
        )
        val nonDebuggableFiles = temporaryFolder.newFolder("non-debuggable-files")
        val nonDebuggableExporter = JavaProviderLocalObservationExporterFactory.create(
            debugBuild = true,
            providerDebuggable = false,
            filesDirectory = nonDebuggableFiles,
        )

        assertFalse(releaseExporter.export(completeRecord()))
        assertFalse(nonDebuggableExporter.export(completeRecord()))
        assertTrue(releaseFiles.listFiles().orEmpty().isEmpty())
        assertTrue(nonDebuggableFiles.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun activeBuildVariantConstantControlsWhetherAnArtifactCanBeCreated() {
        val filesDirectory = temporaryFolder.newFolder("active-build-files")
        val exporter = JavaProviderLocalObservationExporterFactory.create(
            debugBuild = BuildConfig.DEBUG,
            providerDebuggable = BuildConfig.DEBUG,
            filesDirectory = filesDirectory,
        )

        assertEquals(BuildConfig.DEBUG, exporter.export(completeRecord()))
        assertEquals(BuildConfig.DEBUG, outputFile(filesDirectory).isFile)
    }

    @Test
    fun localSchemaCannotRepresentRequestOrProcessIdentityMetadata() {
        val fieldNames = JavaProviderLocalObservationRecord::class.java.declaredFields
            .map { field -> field.name.lowercase() }
        val json = JavaProviderLocalObservationJson.encode(completeRecord()).lowercase()

        listOf(
            "request",
            "path",
            "source",
            "artifact",
            "binder",
            "uid",
            "pid",
            "package",
            "component",
            "signature",
            "digest",
        ).forEach { forbidden ->
            assertTrue(fieldNames.none { name -> forbidden in name })
            assertFalse(json.contains("\"$forbidden"))
        }
    }

    private fun completeRecord(): JavaProviderLocalObservationRecord {
        val compiler = JavaProviderObservationCollector(JavaProviderStartProfile.COLD).apply {
            recordDuration(JavaProviderObservedPhase.COMPILE, 10L)
            recordDuration(JavaProviderObservedPhase.D8, 20L)
            recordDuration(JavaProviderObservedPhase.TERMINATION, 3L)
            recordCache(JavaProviderCacheOutcome.MISS, JavaProviderCacheMissReason.NOT_FOUND)
        }.snapshot()
        val worker = JavaProviderObservationCollector(JavaProviderStartProfile.COLD).apply {
            recordDuration(JavaProviderObservedPhase.LOAD, 30L)
            recordDuration(JavaProviderObservedPhase.RUN, 40L)
            recordDuration(JavaProviderObservedPhase.TERMINATION, 4L)
        }.snapshot()
        val cacheTelemetry = CompilationCacheTelemetry().apply {
            repeat(2) { recordHit() }
            recordMiss(CompilationCacheMissReason.NOT_FOUND)
            recordPublication()
            recordPublicationFailure()
        }.snapshot()
        return JavaProviderLocalObservationRecord.compose(
            compiler = compiler,
            worker = worker,
            sessionElapsedMillis = 180L,
            workerStartupDurationMillis = 25L,
            cacheTelemetry = cacheTelemetry,
        )
    }

    private fun outputFile(filesDirectory: File): File = File(
        File(filesDirectory, JavaProviderLocalObservationExporterFactory.DIRECTORY_NAME),
        JavaProviderLocalObservationExporterFactory.FILE_NAME,
    )
}
