package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderDexSetIdentityTest {
    @Test
    fun singleDexPreservesLegacySizeAndRawDigest() {
        val file = identity("classes.dex", 112L, 1)

        val set = ProviderDexSetIdentity.of(listOf(file))

        assertEquals(112L, set.sizeBytes)
        assertEquals(file.sha256, set.sha256)
    }

    @Test
    fun multiDexUsesTotalSizeAndDomainSeparatedOrderedIdentity() {
        val files = listOf(identity("classes.dex", 112L, 1), identity("classes2.dex", 224L, 2))

        val set = ProviderDexSetIdentity.of(files)

        assertEquals(336L, set.sizeBytes)
        assertEquals(
            ProviderDigests.combine(ProviderDexSetIdentity.DEX_SET_DIGEST_DOMAIN, files),
            set.sha256,
        )
        assertEquals(set, ProviderDexSetIdentity.fromTransport(
            names = files.map(ProviderFileIdentity::name).toTypedArray(),
            sizes = files.map(ProviderFileIdentity::sizeBytes).toLongArray(),
            flattenedSha256 = set.flattenedSha256(),
        ))
    }

    @Test
    fun transportRejectsReorderingLengthMismatchAndTotalOverflow() {
        val first = identity("classes.dex", 112L, 1)
        val second = identity("classes2.dex", 112L, 2)
        val hashes = ProviderDexSetIdentity.of(listOf(first, second)).flattenedSha256()

        assertThrows(IllegalArgumentException::class.java) {
            ProviderDexSetIdentity.fromTransport(
                arrayOf("classes2.dex", "classes.dex"),
                longArrayOf(112L, 112L),
                hashes,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderDexSetIdentity.fromTransport(arrayOf("classes.dex"), longArrayOf(112L, 112L), hashes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderDexSetIdentity.fromTransport(arrayOf("classes.dex"), longArrayOf(112L), ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderDexSetIdentity.of(
                listOf(identity("classes.dex", JvmSourceContract.MAX_DEX_ARTIFACT_BYTES, 1), second),
            )
        }
    }

    private fun identity(name: String, size: Long, seed: Int) = ProviderFileIdentity(
        name,
        size,
        JvmSha256.digest(byteArrayOf(seed.toByte())),
    )
}
