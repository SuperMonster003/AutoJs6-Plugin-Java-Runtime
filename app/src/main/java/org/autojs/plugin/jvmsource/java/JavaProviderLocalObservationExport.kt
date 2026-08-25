package org.autojs.plugin.jvmsource.java

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * One path-free session row for the debug-only local observation channel. The model deliberately
 * contains no request, source, artifact, process-identity, package, component, or Binder fields.
 */
internal data class JavaProviderLocalObservationRecord(
    val sessionElapsedMillis: Long,
    val workerStartupDurationMillis: Long?,
    val compilerStartProfile: JavaProviderStartProfile,
    val workerStartProfile: JavaProviderStartProfile?,
    val compileDurationMillis: Long?,
    val d8DurationMillis: Long?,
    val loadDurationMillis: Long?,
    val runDurationMillis: Long?,
    val terminationDurationMillis: Long?,
    val cacheOutcome: JavaProviderCacheOutcome,
    val cacheMissReason: JavaProviderCacheMissReason?,
    val cacheTelemetry: CompilationCacheTelemetry.Snapshot,
) {
    init {
        require(sessionElapsedMillis >= 0L)
        require(workerStartupDurationMillis == null || workerStartupDurationMillis in 0L..sessionElapsedMillis)
        require((cacheOutcome == JavaProviderCacheOutcome.MISS) == (cacheMissReason != null))
        listOf(
            compileDurationMillis,
            d8DurationMillis,
            loadDurationMillis,
            runDurationMillis,
            terminationDurationMillis,
        ).forEach { duration -> require(duration == null || duration >= 0L) }
        require(cacheTelemetry.hits >= 0L)
        require(cacheTelemetry.misses >= 0L)
        require(cacheTelemetry.publications >= 0L)
        require(cacheTelemetry.publicationFailures >= 0L)
        require(
            CompilationCacheMissReason.entries.all { reason ->
                cacheTelemetry.missesByReason[reason]?.let { it >= 0L } == true
            },
        )
    }

    companion object {
        fun compose(
            compiler: JavaProviderObservation,
            worker: JavaProviderObservation?,
            sessionElapsedMillis: Long,
            workerStartupDurationMillis: Long?,
            cacheTelemetry: CompilationCacheTelemetry.Snapshot,
        ): JavaProviderLocalObservationRecord = JavaProviderLocalObservationRecord(
            sessionElapsedMillis = sessionElapsedMillis.coerceAtLeast(0L),
            workerStartupDurationMillis = workerStartupDurationMillis
                ?.coerceIn(0L, sessionElapsedMillis.coerceAtLeast(0L)),
            compilerStartProfile = compiler.startProfile,
            workerStartProfile = worker?.startProfile,
            compileDurationMillis = compiler.compileDurationMillis,
            d8DurationMillis = compiler.d8DurationMillis,
            loadDurationMillis = worker?.loadDurationMillis,
            runDurationMillis = worker?.runDurationMillis,
            terminationDurationMillis = sumPresentDurations(
                compiler.terminationDurationMillis,
                worker?.terminationDurationMillis,
            ),
            cacheOutcome = compiler.cacheOutcome,
            cacheMissReason = compiler.cacheMissReason,
            cacheTelemetry = cacheTelemetry,
        )

        private fun sumPresentDurations(first: Long?, second: Long?): Long? = when {
            first == null -> second
            second == null -> first
            Long.MAX_VALUE - first < second -> Long.MAX_VALUE
            else -> first + second
        }
    }
}

internal fun interface JavaProviderLocalObservationExporter {
    /** Returns true only when the row was durably written to the local debug artifact. */
    fun export(value: JavaProviderLocalObservationRecord): Boolean
}

internal object JavaProviderLocalObservationExporterFactory {
    const val DIRECTORY_NAME = "debug-observations"
    const val FILE_NAME = "java-provider-observations-v1.jsonl"
    const val DEFAULT_MAX_FILE_BYTES = 256L * 1024L

    fun create(
        debugBuild: Boolean,
        cachePersistenceBenchmarkBuild: Boolean = false,
        providerDebuggable: Boolean,
        filesDirectory: File,
        maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    ): JavaProviderLocalObservationExporter {
        require(maxFileBytes > 0L)
        val admittedDebugBuild =
            debugBuild && !cachePersistenceBenchmarkBuild && providerDebuggable
        val admittedCacheBenchmarkBuild =
            !debugBuild && cachePersistenceBenchmarkBuild && !providerDebuggable
        return if (admittedDebugBuild || admittedCacheBenchmarkBuild) {
            JsonLinesJavaProviderLocalObservationExporter(
                directory = File(filesDirectory, DIRECTORY_NAME),
                maxFileBytes = maxFileBytes,
            )
        } else {
            DisabledJavaProviderLocalObservationExporter
        }
    }
}

private object DisabledJavaProviderLocalObservationExporter : JavaProviderLocalObservationExporter {
    override fun export(value: JavaProviderLocalObservationRecord): Boolean = false
}

private class JsonLinesJavaProviderLocalObservationExporter(
    private val directory: File,
    private val maxFileBytes: Long,
) : JavaProviderLocalObservationExporter {
    @Synchronized
    override fun export(value: JavaProviderLocalObservationRecord): Boolean = runCatching {
        val line = (JavaProviderLocalObservationJson.encode(value) + '\n').toByteArray(Charsets.UTF_8)
        if (line.size.toLong() > maxFileBytes) return false
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val outputFile = File(directory, JavaProviderLocalObservationExporterFactory.FILE_NAME)
        if (outputFile.exists() && !outputFile.isFile) return false
        val currentSize = outputFile.takeIf(File::isFile)?.length() ?: 0L
        val completeCurrentFile = currentSize == 0L || endsWithNewline(outputFile)
        val append = completeCurrentFile && currentSize <= maxFileBytes - line.size.toLong()
        FileOutputStream(outputFile, append).use { output ->
            output.write(line)
            output.fd.sync()
        }
        true
    }.getOrDefault(false)

    private fun endsWithNewline(file: File): Boolean = RandomAccessFile(file, "r").use { input ->
        if (input.length() == 0L) return true
        input.seek(input.length() - 1L)
        input.read() == '\n'.code
    }
}

internal object JavaProviderLocalObservationJson {
    const val SCHEMA_VERSION = 1

    fun encode(value: JavaProviderLocalObservationRecord): String = buildString(768) {
        append("{\"schemaVersion\":").append(SCHEMA_VERSION)
        append(",\"sessionElapsedMillis\":").append(value.sessionElapsedMillis)
        append(",\"workerStartupDurationMillis\":")
        appendNullable(value.workerStartupDurationMillis)
        append(",\"startProfile\":{\"compiler\":")
        appendEnum(value.compilerStartProfile)
        append(",\"worker\":")
        appendEnum(value.workerStartProfile)
        append("},\"phasesMillis\":{\"COMPILE\":")
        appendNullable(value.compileDurationMillis)
        append(",\"D8\":")
        appendNullable(value.d8DurationMillis)
        append(",\"LOAD\":")
        appendNullable(value.loadDurationMillis)
        append(",\"RUN\":")
        appendNullable(value.runDurationMillis)
        append(",\"TERMINATION\":")
        appendNullable(value.terminationDurationMillis)
        append("},\"cache\":{\"outcome\":")
        appendEnum(value.cacheOutcome)
        append(",\"missReason\":")
        appendEnum(value.cacheMissReason)
        append(",\"hits\":").append(value.cacheTelemetry.hits)
        append(",\"misses\":").append(value.cacheTelemetry.misses)
        append(",\"publications\":").append(value.cacheTelemetry.publications)
        append(",\"publicationFailures\":").append(value.cacheTelemetry.publicationFailures)
        append(",\"missesByReason\":{")
        CompilationCacheMissReason.entries.forEachIndexed { index, reason ->
            if (index > 0) append(',')
            append('"').append(reason.name).append("\":")
            append(checkNotNull(value.cacheTelemetry.missesByReason[reason]))
        }
        append("}}}")
    }

    private fun StringBuilder.appendNullable(value: Long?) {
        if (value == null) append("null") else append(value)
    }

    private fun StringBuilder.appendEnum(value: Enum<*>?) {
        if (value == null) append("null") else append('"').append(value.name).append('"')
    }
}
