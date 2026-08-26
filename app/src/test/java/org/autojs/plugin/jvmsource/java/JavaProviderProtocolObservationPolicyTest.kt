package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmObservationCacheMissReason
import org.autojs.plugin.jvmsource.api.JvmObservationCacheOutcome
import org.autojs.plugin.jvmsource.api.JvmObservationPhase
import org.autojs.plugin.jvmsource.api.JvmObservationProcess
import org.autojs.plugin.jvmsource.api.JvmObservationStartProfile
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaProviderProtocolObservationPolicyTest {
    private val requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT) { it.toByte() })

    @Test
    fun composesAllNegotiatedFieldsAndSixReleaseSafeResourceSamples() {
        val compiler = observation(
            startProfile = JavaProviderStartProfile.WARM,
            compileDurationMillis = 224L,
            d8DurationMillis = 480L,
            terminationDurationMillis = 8L,
            cacheOutcome = JavaProviderCacheOutcome.MISS,
            cacheMissReason = JavaProviderCacheMissReason.CACHE_DISABLED,
            resources = listOf(
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.COMPILE),
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.D8),
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.TERMINATION),
                sample(JavaProviderObservedProcess.HOST, JavaProviderObservedPhase.RUN),
            ),
        )
        val worker = observation(
            startProfile = JavaProviderStartProfile.COLD,
            loadDurationMillis = 5L,
            runDurationMillis = 2L,
            terminationDurationMillis = 10L,
            resources = listOf(
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.LOAD),
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.RUN),
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.TERMINATION),
            ),
        )

        val projected = JavaProviderProtocolObservationPolicy.compose(
            requestId = requestId,
            compiler = compiler,
            worker = worker,
            sessionElapsedMillis = 1_209L,
            workerStartupDurationMillis = 283L,
        )

        assertEquals(requestId, projected.requestId)
        assertEquals(1_209L, projected.sessionElapsedMillis)
        assertEquals(283L, projected.workerStartupDurationMillis)
        assertEquals(JvmObservationStartProfile.WARM, projected.compilerStartProfile)
        assertEquals(JvmObservationStartProfile.COLD, projected.workerStartProfile)
        assertEquals(224L, projected.compileDurationMillis)
        assertEquals(480L, projected.d8DurationMillis)
        assertEquals(5L, projected.loadDurationMillis)
        assertEquals(2L, projected.runDurationMillis)
        assertEquals(18L, projected.terminationDurationMillis)
        assertEquals(JvmObservationCacheOutcome.MISS, projected.cacheOutcome)
        assertEquals(JvmObservationCacheMissReason.CACHE_DISABLED, projected.cacheMissReason)
        assertEquals(6, projected.resources.size)
        assertFalse(projected.resources.any { it.process.name == "HOST" })
        JvmSourceValidation.validateObservation(projected)
        val encoded = JvmSourceCodec.encodeObservation(projected)
        assertTrue(encoded.size <= JvmSourceContract.MAX_OBSERVATION_BYTES)
        assertEquals(projected, JvmSourceCodec.decodeObservation(encoded))
    }

    @Test
    fun saturatesTheSessionEnvelopeAndKeepsUnavailableFieldsAbsent() {
        val projected = JavaProviderProtocolObservationPolicy.compose(
            requestId = requestId,
            compiler = observation(startProfile = JavaProviderStartProfile.COLD),
            worker = null,
            sessionElapsedMillis = Long.MAX_VALUE,
            workerStartupDurationMillis = Long.MAX_VALUE,
        )

        assertEquals(JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS, projected.sessionElapsedMillis)
        assertEquals(projected.sessionElapsedMillis, projected.workerStartupDurationMillis)
        assertNull(projected.workerStartProfile)
        assertNull(projected.loadDurationMillis)
        assertNull(projected.runDurationMillis)
        assertEquals(JvmObservationCacheOutcome.NOT_EVALUATED, projected.cacheOutcome)
        assertNull(projected.cacheMissReason)
    }

    @Test
    fun saturatesEveryReleaseNumericBoundaryWithoutOverflowingTerminalComposition() {
        val compiler = observation(
            startProfile = JavaProviderStartProfile.COLD,
            compileDurationMillis = Long.MAX_VALUE,
            d8DurationMillis = Long.MAX_VALUE,
            terminationDurationMillis = Long.MAX_VALUE,
            cacheOutcome = JavaProviderCacheOutcome.MISS,
            cacheMissReason = JavaProviderCacheMissReason.NOT_FOUND,
            resources = listOf(
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.D8).copy(
                    rssBytes = Long.MAX_VALUE,
                    openFileDescriptorCount = Int.MAX_VALUE,
                    temporaryStorageBytes = Long.MAX_VALUE,
                    outputBytes = Long.MAX_VALUE,
                ),
            ),
        )
        val worker = observation(
            startProfile = JavaProviderStartProfile.COLD,
            loadDurationMillis = Long.MAX_VALUE,
            runDurationMillis = Long.MAX_VALUE,
            terminationDurationMillis = Long.MAX_VALUE,
            resources = listOf(
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.RUN).copy(
                    rssBytes = Long.MIN_VALUE,
                    openFileDescriptorCount = Int.MIN_VALUE,
                    temporaryStorageBytes = Long.MIN_VALUE,
                    outputBytes = Long.MIN_VALUE,
                ),
            ),
        )

        val projected = JavaProviderProtocolObservationPolicy.compose(
            requestId,
            compiler,
            worker,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
        )

        assertEquals(JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS, projected.sessionElapsedMillis)
        assertEquals(JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS, projected.compileDurationMillis)
        assertEquals(JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS, projected.d8DurationMillis)
        assertEquals(JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS, projected.loadDurationMillis)
        assertEquals(JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS, projected.runDurationMillis)
        assertEquals(
            JvmSourceContract.MAX_OBSERVATION_TERMINATION_DURATION_MILLIS,
            projected.terminationDurationMillis,
        )
        assertEquals(JvmSourceContract.MAX_OBSERVATION_RSS_BYTES, projected.resources[0].rssBytes)
        assertEquals(
            JvmSourceContract.MAX_OBSERVATION_OPEN_FILE_DESCRIPTORS,
            projected.resources[0].openFileDescriptorCount,
        )
        assertEquals(JvmSourceContract.MAX_OBSERVATION_TEMPORARY_STORAGE_BYTES, projected.resources[0].temporaryStorageBytes)
        assertEquals(JvmSourceContract.MAX_OBSERVATION_OUTPUT_BYTES, projected.resources[0].outputBytes)
        assertEquals(0L, projected.resources[1].rssBytes)
        assertEquals(0, projected.resources[1].openFileDescriptorCount)
        assertEquals(0L, projected.resources[1].temporaryStorageBytes)
        assertEquals(0L, projected.resources[1].outputBytes)
        JvmSourceValidation.validateObservation(projected)
    }

    @Test
    fun dropsHitCompilationDurationsAndResourceSamplesWithNoAvailableMetric() {
        val hit = JavaProviderProtocolObservationPolicy.compose(
            requestId = requestId,
            compiler = observation(
                startProfile = JavaProviderStartProfile.WARM,
                compileDurationMillis = 1L,
                d8DurationMillis = 2L,
                cacheOutcome = JavaProviderCacheOutcome.HIT,
                resources = listOf(
                    JavaProviderResourceObservation(
                        JavaProviderObservedProcess.COMPILER,
                        JavaProviderObservedPhase.COMPILE,
                        rssBytes = null,
                        openFileDescriptorCount = null,
                        temporaryStorageBytes = null,
                        outputBytes = null,
                    ),
                ),
            ),
            worker = null,
            sessionElapsedMillis = 3L,
            workerStartupDurationMillis = null,
        )

        assertEquals(JvmObservationCacheOutcome.HIT, hit.cacheOutcome)
        assertNull(hit.compileDurationMillis)
        assertNull(hit.d8DurationMillis)
        assertNull(hit.cacheMissReason)
        assertTrue(hit.resources.isEmpty())
    }

    @Test
    fun mapsEveryBoundedCacheMissReasonWithoutCumulativeTelemetry() {
        JavaProviderCacheMissReason.entries.forEach { reason ->
            val projected = JavaProviderProtocolObservationPolicy.compose(
                requestId = requestId,
                compiler = observation(
                    startProfile = JavaProviderStartProfile.COLD,
                    cacheOutcome = JavaProviderCacheOutcome.MISS,
                    cacheMissReason = reason,
                ),
                worker = null,
                sessionElapsedMillis = 1L,
                workerStartupDurationMillis = null,
            )

            assertEquals(reason.name, projected.cacheMissReason?.name)
        }
    }

    @Test
    fun admitsExactResourceAndDurationBoundariesInReleaseProjection() {
        val phaseMaximum = JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS
        val compiler = observation(
            startProfile = JavaProviderStartProfile.COLD,
            compileDurationMillis = phaseMaximum,
            d8DurationMillis = phaseMaximum,
            terminationDurationMillis = phaseMaximum,
            cacheOutcome = JavaProviderCacheOutcome.MISS,
            cacheMissReason = JavaProviderCacheMissReason.NOT_FOUND,
            resources = listOf(
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.COMPILE),
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.D8),
                sample(JavaProviderObservedProcess.COMPILER, JavaProviderObservedPhase.TERMINATION),
            ),
        )
        val worker = observation(
            startProfile = JavaProviderStartProfile.COLD,
            loadDurationMillis = phaseMaximum,
            runDurationMillis = phaseMaximum,
            terminationDurationMillis = phaseMaximum,
            resources = listOf(
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.LOAD),
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.RUN),
                sample(JavaProviderObservedProcess.WORKER, JavaProviderObservedPhase.TERMINATION),
            ),
        )

        val projected = JavaProviderProtocolObservationPolicy.compose(
            requestId,
            compiler,
            worker,
            JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS,
            JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS,
        )

        assertEquals(
            JvmSourceContract.MAX_OBSERVATION_TERMINATION_DURATION_MILLIS,
            projected.terminationDurationMillis,
        )
        assertEquals(6, projected.resources.size)
        JvmSourceValidation.validateObservation(projected)
    }

    private fun observation(
        startProfile: JavaProviderStartProfile,
        compileDurationMillis: Long? = null,
        d8DurationMillis: Long? = null,
        loadDurationMillis: Long? = null,
        runDurationMillis: Long? = null,
        terminationDurationMillis: Long? = null,
        cacheOutcome: JavaProviderCacheOutcome = JavaProviderCacheOutcome.NOT_EVALUATED,
        cacheMissReason: JavaProviderCacheMissReason? = null,
        resources: List<JavaProviderResourceObservation> = emptyList(),
    ) = JavaProviderObservation(
        startProfile,
        compileDurationMillis,
        d8DurationMillis,
        loadDurationMillis,
        runDurationMillis,
        terminationDurationMillis,
        cacheOutcome,
        cacheMissReason,
        resources,
    )

    private fun sample(
        process: JavaProviderObservedProcess,
        phase: JavaProviderObservedPhase,
    ) = JavaProviderResourceObservation(
        process = process,
        phase = phase,
        rssBytes = JvmSourceContract.MAX_OBSERVATION_RSS_BYTES,
        openFileDescriptorCount = JvmSourceContract.MAX_OBSERVATION_OPEN_FILE_DESCRIPTORS,
        temporaryStorageBytes = JvmSourceContract.MAX_OBSERVATION_TEMPORARY_STORAGE_BYTES,
        outputBytes = JvmSourceContract.MAX_OBSERVATION_OUTPUT_BYTES,
    )
}
