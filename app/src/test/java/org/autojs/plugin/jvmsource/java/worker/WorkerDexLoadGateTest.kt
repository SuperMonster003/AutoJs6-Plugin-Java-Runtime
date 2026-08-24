package org.autojs.plugin.jvmsource.java.worker

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.java.JavaProviderFailure
import org.autojs.plugin.jvmsource.java.DexArtifactPayload
import org.autojs.plugin.jvmsource.java.ProviderDexSetIdentity
import org.autojs.plugin.jvmsource.java.ProviderFileIdentity
import org.autojs.plugin.jvmsource.java.ValidatedDexArtifact
import org.autojs.plugin.jvmsource.java.ValidatedDexArtifactSet
import org.autojs.plugin.jvmsource.java.WorkerDexLoaderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkerDexLoadGateTest {
    @Test
    fun structureValidationStateDoesNotClaimArtLoadSuccess() {
        val bytes = byteArrayOf(1)
        val identity = ProviderFileIdentity("classes.dex", 1L, JvmSha256.digest(bytes))
        val validated = StructurallyValidatedWorkerDexSet(
            payloads = listOf(DexArtifactPayload(identity, bytes)),
            artifacts = ValidatedDexArtifactSet(
                ProviderDexSetIdentity.of(listOf(identity)),
                listOf(
                    ValidatedDexArtifact(
                        sizeBytes = 1L,
                        sha256 = identity.sha256,
                        version = "037",
                        classDescriptors = setOf("LMain;"),
                        requestMinApi = 24,
                        deviceApi = 24,
                        loaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
                    ),
                ),
            ),
        )

        assertEquals(WorkerDexLoadStage.STRUCTURE_VALIDATED, validated.stage)
    }

    @Test
    fun artEntryLoadFailureHasASeparateStableCodeAndDoesNotInitializeParentMain() {
        System.clearProperty(INITIALIZATION_PROPERTY)
        val failure = assertThrows(JavaProviderFailure::class.java) {
            WorkerEntryFactory.loadFromArt(object : ClassLoader(javaClass.classLoader) {})
        }

        assertEquals(JvmSourceErrorCode.CLASS_LOADING_FAILED, failure.code)
        assertEquals(JvmSourceFailurePhase.WORKER_START, failure.phase)
        assertNull(System.getProperty(INITIALIZATION_PROPERTY))
    }

    @Test
    fun actualClassLoaderBranchMustMatchTheValidatedArtifactPolicy() {
        val identity = ProviderFileIdentity("classes.dex", 1L, JvmSha256.digest(byteArrayOf(1)))
        val artifact = ValidatedDexArtifactSet(
            ProviderDexSetIdentity.of(listOf(identity)),
            listOf(
                ValidatedDexArtifact(
                    sizeBytes = 1L,
                    sha256 = identity.sha256,
                    version = "037",
                    classDescriptors = setOf("LMain;"),
                    requestMinApi = 24,
                    deviceApi = 24,
                    loaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            LoadedDex(
                classLoader = javaClass.classLoader!!,
                validatedArtifacts = artifact,
                actualLoaderKind = WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            )
        }
    }

    private companion object {
        const val INITIALIZATION_PROPERTY = "autojs.jvm.source.test.main.initialized"
    }
}
