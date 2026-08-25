package org.autojs.plugin.jvmsource.java

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Installation-scoped HMAC key whose material never leaves Android Keystore. */
internal object AndroidCompilationCacheAuthenticatorProvider {
    internal const val KEY_ALIAS = "org.autojs.jvm-source.java-compilation-cache-hmac-v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_SIZE_BITS = 256
    private val SELF_TEST_DOMAIN =
        "org.autojs.jvm-source.java-compilation-cache-hmac-self-test.v1".toByteArray(Charsets.US_ASCII)

    @Synchronized
    fun loadOrCreate(alias: String = KEY_ALIAS): CompilationCacheAuthenticator {
        require(alias.isNotBlank())
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = load(keyStore, alias) ?: generateThenReload(keyStore, alias)
        require(key.algorithm.equals(CompilationCacheAuthenticators.HMAC_ALGORITHM, ignoreCase = true)) {
            "Compilation cache Keystore alias has the wrong algorithm"
        }
        require(key.format == null && key.encoded == null) {
            "Compilation cache Keystore key material must not be exportable"
        }
        return CompilationCacheAuthenticators.hmacSha256(key).also { authenticator ->
            require(
                authenticator.authenticate(SELF_TEST_DOMAIN).size ==
                    CompilationCacheAuthenticators.AUTHENTICATOR_BYTES,
            ) { "Compilation cache Keystore key self-test failed" }
        }
    }

    private fun generateThenReload(keyStore: KeyStore, alias: String): SecretKey {
        try {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                ANDROID_KEYSTORE,
            )
            generator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(KEY_SIZE_BITS)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generator.generateKey()
        } catch (error: Exception) {
            // A second provider process could have won alias creation between load and generate.
            return load(keyStore, alias) ?: throw error
        }
        return checkNotNull(load(keyStore, alias)) { "Generated compilation cache key is unavailable" }
    }

    private fun load(keyStore: KeyStore, alias: String): SecretKey? {
        val key = keyStore.getKey(alias, null) ?: return null
        return key as? SecretKey
            ?: throw IllegalStateException("Compilation cache Keystore alias is not a secret key")
    }
}
