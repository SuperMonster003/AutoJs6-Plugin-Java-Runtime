package org.autojs.plugin.jvmsource.java

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class AndroidCompilationCacheAuthenticatorInstrumentedTest {
    @Test
    fun keystoreHmacIsNonExportableAndStableAcrossFreshLoads() {
        val alias = AndroidCompilationCacheAuthenticatorProvider.KEY_ALIAS + ".instrumentation"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(alias)
        try {
            val input = "cache-manifest".toByteArray(Charsets.US_ASCII)
            val first = AndroidCompilationCacheAuthenticatorProvider.loadOrCreate(alias)
            val firstAuthenticator = first.authenticate(input)
            val second = AndroidCompilationCacheAuthenticatorProvider.loadOrCreate(alias)
            val secondAuthenticator = second.authenticate(input)
            val key = keyStore.getKey(alias, null) as SecretKey

            assertArrayEquals(firstAuthenticator, secondAuthenticator)
            assertEquals(CompilationCacheAuthenticators.AUTHENTICATOR_BYTES, firstAuthenticator.size)
            assertEquals(CompilationCacheAuthenticators.HMAC_ALGORITHM, key.algorithm)
            assertNull(key.format)
            assertNull(key.encoded)
        } finally {
            keyStore.deleteEntry(alias)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
