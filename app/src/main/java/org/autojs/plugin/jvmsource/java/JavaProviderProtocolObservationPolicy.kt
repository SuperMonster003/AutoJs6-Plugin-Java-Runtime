package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmObservationCacheMissReason
import org.autojs.plugin.jvmsource.api.JvmObservationCacheOutcome
import org.autojs.plugin.jvmsource.api.JvmObservationPhase
import org.autojs.plugin.jvmsource.api.JvmObservationProcess
import org.autojs.plugin.jvmsource.api.JvmObservationStartProfile
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourceResourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourceValidation

/**
 * Release-safe Protocol 1.5 projection. It maps a fixed set of numeric provider observations and
 * cannot export the debug channel's global cache counters or any path/process/package identity.
 */
internal object JavaProviderProtocolObservationPolicy {
    fun compose(
        requestId: JvmRequestId,
        compiler: JavaProviderObservation,
        worker: JavaProviderObservation?,
        sessionElapsedMillis: Long,
        workerStartupDurationMillis: Long?,
    ): JvmSourceObservation {
        val boundedSessionElapsed = sessionElapsedMillis.coerceIn(
            0L,
            JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS,
        )
        val resources = (compiler.resources.asSequence() + worker?.resources.orEmpty().asSequence())
            .mapNotNull(::projectResource)
            .distinctBy { resource -> resource.process to resource.phase }
            .take(JvmSourceContract.MAX_OBSERVATION_RESOURCE_SAMPLES)
            .toList()
        val projectedCacheOutcome = projectCacheOutcome(compiler.cacheOutcome)
        val projectedCacheMissReason = compiler.cacheMissReason?.let(::projectCacheMissReason)
        val cacheOutcome = if (
            projectedCacheOutcome == JvmObservationCacheOutcome.MISS && projectedCacheMissReason == null
        ) {
            JvmObservationCacheOutcome.NOT_EVALUATED
        } else {
            projectedCacheOutcome
        }
        val cacheMissReason = projectedCacheMissReason.takeIf {
            cacheOutcome == JvmObservationCacheOutcome.MISS
        }
        return JvmSourceObservation(
            requestId = requestId,
            sessionElapsedMillis = boundedSessionElapsed,
            workerStartupDurationMillis = workerStartupDurationMillis?.coerceIn(0L, boundedSessionElapsed),
            compilerStartProfile = projectStartProfile(compiler.startProfile),
            workerStartProfile = worker?.startProfile?.let(::projectStartProfile),
            compileDurationMillis = compiler.compileDurationMillis
                .takeUnless { cacheOutcome == JvmObservationCacheOutcome.HIT }
                .boundedPhaseDuration(),
            d8DurationMillis = compiler.d8DurationMillis
                .takeUnless { cacheOutcome == JvmObservationCacheOutcome.HIT }
                .boundedPhaseDuration(),
            loadDurationMillis = worker?.loadDurationMillis.boundedPhaseDuration(),
            runDurationMillis = worker?.runDurationMillis.boundedPhaseDuration(),
            terminationDurationMillis = sumPresentDurations(
                compiler.terminationDurationMillis,
                worker?.terminationDurationMillis,
            ),
            cacheOutcome = cacheOutcome,
            cacheMissReason = cacheMissReason,
            resources = resources,
        ).also(JvmSourceValidation::validateObservation)
    }

    private fun projectResource(value: JavaProviderResourceObservation): JvmSourceResourceObservation? {
        val process = when (value.process) {
            JavaProviderObservedProcess.HOST -> return null
            JavaProviderObservedProcess.COMPILER -> JvmObservationProcess.COMPILER
            JavaProviderObservedProcess.WORKER -> JvmObservationProcess.WORKER
        }
        val phase = when (value.phase) {
            JavaProviderObservedPhase.COMPILE -> JvmObservationPhase.COMPILE
            JavaProviderObservedPhase.D8 -> JvmObservationPhase.D8
            JavaProviderObservedPhase.LOAD -> JvmObservationPhase.LOAD
            JavaProviderObservedPhase.RUN -> JvmObservationPhase.RUN
            JavaProviderObservedPhase.TERMINATION -> JvmObservationPhase.TERMINATION
        }
        val legal = when (process) {
            JvmObservationProcess.COMPILER -> phase in setOf(
                JvmObservationPhase.COMPILE,
                JvmObservationPhase.D8,
                JvmObservationPhase.TERMINATION,
            )
            JvmObservationProcess.WORKER -> phase in setOf(
                JvmObservationPhase.LOAD,
                JvmObservationPhase.RUN,
                JvmObservationPhase.TERMINATION,
            )
        }
        if (!legal) return null
        return JvmSourceResourceObservation(
            process = process,
            phase = phase,
            rssBytes = value.rssBytes?.coerceIn(0L, JvmSourceContract.MAX_OBSERVATION_RSS_BYTES),
            openFileDescriptorCount = value.openFileDescriptorCount?.coerceIn(
                0,
                JvmSourceContract.MAX_OBSERVATION_OPEN_FILE_DESCRIPTORS,
            ),
            temporaryStorageBytes = value.temporaryStorageBytes?.coerceIn(
                0L,
                JvmSourceContract.MAX_OBSERVATION_TEMPORARY_STORAGE_BYTES,
            ),
            outputBytes = value.outputBytes?.coerceIn(
                0L,
                JvmSourceContract.MAX_OBSERVATION_OUTPUT_BYTES,
            ),
        ).takeIf { resource ->
            resource.rssBytes != null ||
                resource.openFileDescriptorCount != null ||
                resource.temporaryStorageBytes != null ||
                resource.outputBytes != null
        }
    }

    private fun projectStartProfile(value: JavaProviderStartProfile): JvmObservationStartProfile = when (value) {
        JavaProviderStartProfile.COLD -> JvmObservationStartProfile.COLD
        JavaProviderStartProfile.WARM -> JvmObservationStartProfile.WARM
    }

    private fun projectCacheOutcome(value: JavaProviderCacheOutcome): JvmObservationCacheOutcome = when (value) {
        JavaProviderCacheOutcome.NOT_EVALUATED -> JvmObservationCacheOutcome.NOT_EVALUATED
        JavaProviderCacheOutcome.HIT -> JvmObservationCacheOutcome.HIT
        JavaProviderCacheOutcome.MISS -> JvmObservationCacheOutcome.MISS
    }

    private fun projectCacheMissReason(
        value: JavaProviderCacheMissReason,
    ): JvmObservationCacheMissReason = when (value) {
        JavaProviderCacheMissReason.NOT_FOUND -> JvmObservationCacheMissReason.NOT_FOUND
        JavaProviderCacheMissReason.INVALID_OR_EXPIRED -> JvmObservationCacheMissReason.INVALID_OR_EXPIRED
        JavaProviderCacheMissReason.CACHE_UNAVAILABLE -> JvmObservationCacheMissReason.CACHE_UNAVAILABLE
        JavaProviderCacheMissReason.MATERIALIZATION_FAILED -> JvmObservationCacheMissReason.MATERIALIZATION_FAILED
        JavaProviderCacheMissReason.CACHE_DISABLED -> JvmObservationCacheMissReason.CACHE_DISABLED
        JavaProviderCacheMissReason.PROVIDER_IDENTITY_UNAVAILABLE ->
            JvmObservationCacheMissReason.PROVIDER_IDENTITY_UNAVAILABLE
        JavaProviderCacheMissReason.PROVIDER_IDENTITY_DRIFTED ->
            JvmObservationCacheMissReason.PROVIDER_IDENTITY_DRIFTED
    }

    private fun Long?.boundedPhaseDuration(): Long? = this?.coerceIn(
        0L,
        JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS,
    )

    private fun sumPresentDurations(first: Long?, second: Long?): Long? {
        if (first == null && second == null) return null
        val firstBounded = first.boundedPhaseDuration() ?: 0L
        val secondBounded = second.boundedPhaseDuration() ?: 0L
        return (firstBounded + secondBounded).coerceAtMost(
            JvmSourceContract.MAX_OBSERVATION_TERMINATION_DURATION_MILLIS,
        )
    }
}
