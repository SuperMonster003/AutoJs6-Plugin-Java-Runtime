package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import java.io.File

/**
 * Ordered identity of one R4 DEX set.
 *
 * A single file preserves the legacy raw-file size/hash exposed through Protocol 1.1. Multiple
 * files expose their total byte size and a domain-separated digest over every canonical file
 * identity, so the existing result fields can pin the whole private compiler-to-worker artifact.
 */
@ConsistentCopyVisibility
internal data class ProviderDexSetIdentity private constructor(
    val files: List<ProviderFileIdentity>,
) {
    val sizeBytes: Long = files.fold(0L) { total, identity ->
        Math.addExact(total, identity.sizeBytes)
    }
    val sha256: JvmSha256 = if (files.size == 1) {
        files.single().sha256
    } else {
        ProviderDigests.combine(DEX_SET_DIGEST_DOMAIN, files)
    }

    init {
        JavaDexOutputPolicy.requireCanonicalDexNames(files.map(ProviderFileIdentity::name))
        require(files.all { it.sizeBytes > 0L }) { "DEX artifact is empty" }
        require(sizeBytes <= JvmSourceContract.MAX_DEX_ARTIFACT_BYTES) {
            "DEX artifact set exceeds its total byte limit"
        }
    }

    fun flattenedSha256(): ByteArray = ByteArray(files.size * JvmSha256.BYTE_COUNT).also { flattened ->
        files.forEachIndexed { index, identity ->
            identity.sha256.toByteArray().copyInto(flattened, index * JvmSha256.BYTE_COUNT)
        }
    }

    companion object {
        const val DEX_SET_DIGEST_DOMAIN = "autojs6-java-provider-dex-set-r4-v1"

        fun of(files: List<ProviderFileIdentity>): ProviderDexSetIdentity =
            ProviderDexSetIdentity(files.toList())

        fun fromFiles(
            files: Collection<File>,
            maximumBytes: Long = JvmSourceContract.MAX_DEX_ARTIFACT_BYTES,
        ): ProviderDexSetIdentity {
            require(maximumBytes in 1L..JvmSourceContract.MAX_DEX_ARTIFACT_BYTES)
            var consumed = 0L
            val identities = JavaDexOutputPolicy.requireDexFiles(files).map { file ->
                val identity = ProviderDigests.file(file, maximumBytes - consumed)
                consumed = Math.addExact(consumed, identity.sizeBytes)
                identity
            }
            return of(identities).also { require(it.sizeBytes <= maximumBytes) }
        }

        fun fromTransport(
            names: Array<out String>,
            sizes: LongArray,
            flattenedSha256: ByteArray,
        ): ProviderDexSetIdentity {
            require(names.size == sizes.size) { "DEX transport metadata lengths differ" }
            require(flattenedSha256.size == Math.multiplyExact(names.size, JvmSha256.BYTE_COUNT)) {
                "DEX transport digest framing is invalid"
            }
            val identities = names.indices.map { index ->
                val start = index * JvmSha256.BYTE_COUNT
                ProviderFileIdentity(
                    name = names[index],
                    sizeBytes = sizes[index],
                    sha256 = JvmSha256.fromBytes(
                        flattenedSha256.copyOfRange(start, start + JvmSha256.BYTE_COUNT),
                    ),
                )
            }
            return of(identities)
        }
    }
}
