package org.autojs.plugin.jvmsource.java

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Computes the cache-manifest authenticator without exposing its key material. */
internal fun interface CompilationCacheAuthenticator {
    fun authenticate(bytes: ByteArray): ByteArray
}

internal object CompilationCacheAuthenticators {
    const val HMAC_ALGORITHM = "HmacSHA256"
    const val AUTHENTICATOR_BYTES = 32
    private const val KEY_BYTES = 32

    fun processEpoch(): CompilationCacheAuthenticator =
        hmacSha256(ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes))

    fun hmacSha256(keyBytes: ByteArray): CompilationCacheAuthenticator {
        require(keyBytes.size >= KEY_BYTES)
        return hmacSha256(SecretKeySpec(keyBytes.copyOf(), HMAC_ALGORITHM))
    }

    fun hmacSha256(key: SecretKey): CompilationCacheAuthenticator =
        CompilationCacheAuthenticator { bytes ->
            Mac.getInstance(HMAC_ALGORITHM).run {
                init(key)
                doFinal(bytes)
            }.also { authenticator ->
                require(authenticator.size == AUTHENTICATOR_BYTES) {
                    "Compilation cache authenticator has an unexpected size"
                }
            }
        }
}
