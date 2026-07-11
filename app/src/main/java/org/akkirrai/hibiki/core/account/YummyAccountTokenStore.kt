package org.akkirrai.hibiki.core.account

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface YummyAccountTokenStore {
    fun getAccessToken(): String?
    fun saveAccessToken(token: String)
    fun clearAccessToken()
    fun hasAccessToken(): Boolean = !getAccessToken().isNullOrBlank()
}

interface YummyApplicationTokenStore {
    fun getApplicationToken(): String?
    fun getEffectiveApplicationToken(): String =
        getApplicationToken()?.takeIf(String::isNotBlank) ?: DEFAULT_YUMMY_APPLICATION_TOKEN
    fun saveApplicationToken(token: String)
    fun clearApplicationToken()

    companion object {
        // This header is required for stable YummyAnime API access across all sources.
        const val DEFAULT_YUMMY_APPLICATION_TOKEN = "wawegr8j13it4rdw"
    }
}

class AndroidKeystoreYummyAccountTokenStore(
    context: Context,
) : YummyAccountTokenStore {
    private val secureStore = AndroidKeystoreStringStore(
        context = context,
        prefsName = PREFS_NAME,
        keyAlias = KEY_ALIAS,
    )

    override fun getAccessToken(): String? = secureStore.get(KEY_ACCESS_TOKEN)

    override fun saveAccessToken(token: String) {
        val normalized = token.trim()
        require(normalized.isNotBlank()) { "YummyAnime access token is blank" }
        secureStore.save(KEY_ACCESS_TOKEN, normalized)
    }

    override fun clearAccessToken() {
        secureStore.clear(KEY_ACCESS_TOKEN)
    }

    private companion object {
        const val PREFS_NAME = "hibiki_yummy_account_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ALIAS = "hibiki_yummy_account_access_token"
    }
}

class AndroidKeystoreYummyApplicationTokenStore(
    context: Context,
) : YummyApplicationTokenStore {
    private val appContext = context.applicationContext
    private val secureStore = AndroidKeystoreStringStore(
        context = appContext,
        prefsName = PREFS_NAME,
        keyAlias = KEY_ALIAS,
    )

    override fun getApplicationToken(): String? {
        return secureStore.get(KEY_APPLICATION_TOKEN)
    }

    override fun saveApplicationToken(token: String) {
        val normalized = token.trim()
        require(normalized.isNotBlank()) { "YummyAnime application token is blank" }
        secureStore.save(KEY_APPLICATION_TOKEN, normalized)
    }

    override fun clearApplicationToken() {
        secureStore.clear(KEY_APPLICATION_TOKEN)
    }

    private companion object {
        const val PREFS_NAME = "hibiki_yummy_application_token"
        const val KEY_APPLICATION_TOKEN = "application_token"
        const val KEY_ALIAS = "hibiki_yummy_application_token"
    }
}

private class AndroidKeystoreStringStore(
    context: Context,
    prefsName: String,
    private val keyAlias: String,
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE,
    )

    fun get(key: String): String? {
        val encryptedValue = prefs.getString(valueKey(key), null)?.takeIf(String::isNotBlank) ?: return null
        val iv = prefs.getString(ivKey(key), null)?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP))
                .decodeToString()
                .takeIf(String::isNotBlank)
        }.getOrNull()
    }

    fun save(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        prefs.edit()
            .putString(valueKey(key), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivKey(key), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clear(key: String) {
        prefs.edit()
            .remove(valueKey(key))
            .remove(ivKey(key))
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun valueKey(key: String): String = "${key}_value"

    private fun ivKey(key: String): String = "${key}_iv"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
