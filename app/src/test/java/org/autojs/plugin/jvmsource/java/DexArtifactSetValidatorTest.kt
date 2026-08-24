package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DexArtifactSetValidatorTest {
    @Test
    fun validatesEachDexThenRequiresExactUnionOfEcjClasses() {
        val payloads = payloads("LMain;", "LHelper;")
        val identity = ProviderDexSetIdentity.of(payloads.map(DexArtifactPayload::identity))

        val validated = DexArtifactSetValidator.validate(
            payloads,
            identity,
            requestMinApi = 24,
            deviceApi = 24,
            expectedClassDescriptors = setOf("LMain;", "LHelper;"),
        )

        assertEquals(listOf("classes.dex", "classes2.dex"), validated.artifacts.map { it.name })
        assertEquals(setOf("LMain;", "LHelper;"), validated.classDescriptors)
        assertEquals(identity, validated.identity)
        assertEquals("035", validated.version)
        assertEquals(WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER, validated.loaderKind)
    }

    @Test
    fun rejectsMissingUnexpectedAndDuplicateDescriptorsAcrossTheSet() {
        val distinct = payloads("LMain;", "LHelper;")
        assertInvalid {
            DexArtifactSetValidator.validate(
                distinct,
                ProviderDexSetIdentity.of(distinct.map(DexArtifactPayload::identity)),
                24,
                24,
                setOf("LMain;", "LMissing;"),
            )
        }

        val duplicate = payloads("LMain;", "LMain;")
        assertInvalid {
            DexArtifactSetValidator.validate(
                duplicate,
                ProviderDexSetIdentity.of(duplicate.map(DexArtifactPayload::identity)),
                24,
                24,
                setOf("LMain;"),
            )
        }
    }

    @Test
    fun rejectsPayloadIdentityDriftBeforeUnionValidation() {
        val payloads = payloads("LMain;", "LHelper;")
        val admitted = ProviderDexSetIdentity.of(payloads.map(DexArtifactPayload::identity))
        val changed = payloads.toMutableList().also { values ->
            values[1] = values[1].copy(bytes = values[1].bytes + 0)
        }

        assertInvalid {
            DexArtifactSetValidator.validate(changed, admitted, 24, 24, setOf("LMain;", "LHelper;"))
        }
    }

    @Test
    fun api26RejectsMultipleDexFilesBecauseItsInMemoryLoaderHasNoArrayConstructor() {
        val payloads = payloads("LMain;", "LHelper;")

        assertInvalid {
            DexArtifactSetValidator.validate(
                payloads,
                ProviderDexSetIdentity.of(payloads.map(DexArtifactPayload::identity)),
                requestMinApi = 24,
                deviceApi = 26,
                expectedClassDescriptors = setOf("LMain;", "LHelper;"),
            )
        }
    }

    private fun payloads(vararg descriptors: String): List<DexArtifactPayload> = descriptors.mapIndexed { index, value ->
        val bytes = CacheTestArtifacts.minimalDex(value)
        val name = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
        DexArtifactPayload(
            ProviderFileIdentity(name, bytes.size.toLong(), org.autojs.plugin.jvmsource.api.JvmSha256.digest(bytes)),
            bytes,
        )
    }

    private fun assertInvalid(block: () -> Unit) {
        val failure = assertThrows(JavaProviderFailure::class.java, block)
        assertEquals(JvmSourceErrorCode.ARTIFACT_INVALID, failure.code)
    }
}
