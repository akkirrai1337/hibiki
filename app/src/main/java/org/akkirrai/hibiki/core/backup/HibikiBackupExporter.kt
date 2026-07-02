package org.akkirrai.hibiki.core.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.account.AndroidKeystoreYummyAccountTokenStore
import org.akkirrai.hibiki.core.account.AndroidKeystoreYummyApplicationTokenStore
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class HibikiBackupSelection(
    val settings: Boolean = false,
    val library: Boolean = false,
    val watchProgress: Boolean = false,
    val account: Boolean = false,
) {
    val hasAnySelection: Boolean
        get() = settings || library || watchProgress || account
}

data class HibikiBackupExportResult(
    val recoveryKey: String? = null,
)

data class HibikiBackupImportPreview(
    val selection: HibikiBackupSelection,
    val requiresRecoveryKey: Boolean,
)

class MissingAccountSessionException : IllegalStateException("No saved YummyAnime account session")
class InvalidBackupException(message: String) : IllegalStateException(message)
class InvalidBackupRecoveryKeyException : IllegalStateException("Invalid backup recovery key")

class HibikiBackupExporter(context: Context) {
    private val appContext = context.applicationContext
    private val random = SecureRandom()

    suspend fun export(
        destination: Uri,
        selection: HibikiBackupSelection,
    ): HibikiBackupExportResult = withContext(Dispatchers.IO) {
        require(selection.hasAnySelection) { "Backup selection is empty" }

        val encryptedAccount = if (selection.account) {
            encryptAccountSession()
        } else {
            null
        }
        appContext.contentResolver.openOutputStream(destination)?.use { outputStream ->
            ZipOutputStream(outputStream.buffered()).use { zip ->
                zip.writeJsonEntry(
                    path = MANIFEST_PATH,
                    json = buildManifest(selection),
                )

                if (selection.settings) {
                    zip.writeJsonEntry(
                        path = "preferences/settings.json",
                        json = dumpPreferences(AppPreferences.PREFS_NAME),
                    )
                }

                if (selection.library) {
                    zip.writeJsonEntry(
                        path = "preferences/library.json",
                        json = dumpPreferences(LibraryRepository.PREFS_NAME),
                    )
                    zip.writeJsonEntry(
                        path = "preferences/offline_title_metadata.json",
                        json = dumpPreferences(OfflineTitleMetadataRepository.PREFS_NAME),
                    )
                }

                if (selection.watchProgress) {
                    zip.writeJsonEntry(
                        path = "preferences/watch_progress.json",
                        json = dumpPreferences(WatchStateRepository.PREFS_NAME),
                    )
                }

                if (encryptedAccount != null) {
                    zip.writeJsonEntry(
                        path = "account/session.enc.json",
                        json = encryptedAccount.payload,
                    )
                }
            }
        } ?: error("Could not open backup file")

        HibikiBackupExportResult(recoveryKey = encryptedAccount?.recoveryKey)
    }

    suspend fun inspect(source: Uri): HibikiBackupImportPreview = withContext(Dispatchers.IO) {
        val entries = readZipEntries(source)
        val manifest = entries[MANIFEST_PATH]?.let(::JSONObject)
            ?: throw InvalidBackupException("Backup manifest is missing")
        validateManifest(manifest)
        HibikiBackupImportPreview(
            selection = manifest.optJSONObject("selection").toSelection(),
            requiresRecoveryKey = entries.containsKey(ACCOUNT_SESSION_PATH),
        )
    }

    suspend fun importBackup(
        source: Uri,
        recoveryKey: String? = null,
    ): HibikiBackupImportPreview = withContext(Dispatchers.IO) {
        val entries = readZipEntries(source)
        val manifest = entries[MANIFEST_PATH]?.let(::JSONObject)
            ?: throw InvalidBackupException("Backup manifest is missing")
        validateManifest(manifest)

        val selection = manifest.optJSONObject("selection").toSelection()
        if (selection.settings) {
            entries["preferences/settings.json"]?.let(::JSONObject)
                ?.let(::restorePreferences)
        }
        if (selection.library) {
            entries["preferences/library.json"]?.let(::JSONObject)
                ?.let(::restorePreferences)
            entries["preferences/offline_title_metadata.json"]?.let(::JSONObject)
                ?.let(::restorePreferences)
        }
        if (selection.watchProgress) {
            entries["preferences/watch_progress.json"]?.let(::JSONObject)
                ?.let(::restorePreferences)
        }
        if (entries.containsKey(ACCOUNT_SESSION_PATH)) {
            val key = recoveryKey?.takeIf(String::isNotBlank)
                ?: throw InvalidBackupRecoveryKeyException()
            restoreAccountSession(JSONObject(entries.getValue(ACCOUNT_SESSION_PATH)), key)
        }

        HibikiBackupImportPreview(
            selection = selection,
            requiresRecoveryKey = entries.containsKey(ACCOUNT_SESSION_PATH),
        )
    }

    private fun buildManifest(selection: HibikiBackupSelection): JSONObject {
        return JSONObject().apply {
            put("format", FORMAT_NAME)
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put(
                "selection",
                JSONObject().apply {
                    put("settings", selection.settings)
                    put("library", selection.library)
                    put("watchProgress", selection.watchProgress)
                    put("account", selection.account)
                },
            )
        }
    }

    private fun validateManifest(manifest: JSONObject) {
        if (manifest.optString("format") != FORMAT_NAME) {
            throw InvalidBackupException("Unsupported backup format")
        }
        if (manifest.optInt("formatVersion") != FORMAT_VERSION) {
            throw InvalidBackupException("Unsupported backup version")
        }
    }

    private fun JSONObject?.toSelection(): HibikiBackupSelection {
        return HibikiBackupSelection(
            settings = this?.optBoolean("settings", false) == true,
            library = this?.optBoolean("library", false) == true,
            watchProgress = this?.optBoolean("watchProgress", false) == true,
            account = this?.optBoolean("account", false) == true,
        )
    }

    private fun dumpPreferences(name: String): JSONObject {
        val preferences = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("name", name)
            put("entries", JSONObject().apply {
                preferences.all.toSortedMap().forEach { (key, value) ->
                    put(key, encodePreferenceValue(value))
                }
            })
        }
    }

    private fun encodePreferenceValue(value: Any?): JSONObject {
        return JSONObject().apply {
            when (value) {
                is String -> {
                    put("type", "string")
                    put("value", value)
                }
                is Boolean -> {
                    put("type", "boolean")
                    put("value", value)
                }
                is Int -> {
                    put("type", "int")
                    put("value", value)
                }
                is Long -> {
                    put("type", "long")
                    put("value", value)
                }
                is Float -> {
                    put("type", "float")
                    put("value", value.toDouble())
                }
                is Set<*> -> {
                    put("type", "string_set")
                    put(
                        "value",
                        JSONArray(value.filterIsInstance<String>().sorted()),
                    )
                }
                else -> {
                    put("type", "unsupported")
                }
            }
        }
    }

    private fun restorePreferences(json: JSONObject) {
        val name = json.getString("name")
        val entries = json.optJSONObject("entries") ?: JSONObject()
        val editor = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()

        entries.keys().forEach { key ->
            val encodedValue = entries.optJSONObject(key) ?: return@forEach
            editor.putEncodedPreferenceValue(key, encodedValue)
        }
        editor.commit()
    }

    private fun SharedPreferences.Editor.putEncodedPreferenceValue(
        key: String,
        encodedValue: JSONObject,
    ) {
        when (encodedValue.optString("type")) {
            "string" -> putString(key, encodedValue.optString("value"))
            "boolean" -> putBoolean(key, encodedValue.optBoolean("value"))
            "int" -> putInt(key, encodedValue.optInt("value"))
            "long" -> putLong(key, encodedValue.optLong("value"))
            "float" -> putFloat(key, encodedValue.optDouble("value").toFloat())
            "string_set" -> putStringSet(
                key,
                encodedValue.optJSONArray("value").toStringSet(),
            )
        }
    }

    private fun encryptAccountSession(): EncryptedAccountSession {
        val repository = YummyAccountRepository(appContext)
        val accountJson = try {
            val accessToken = repository.getStoredToken()
                ?.takeIf(String::isNotBlank)
                ?: throw MissingAccountSessionException()

            JSONObject().apply {
                put("accessToken", accessToken)
                put("applicationToken", repository.getApplicationToken())
                put("applicationTokenEnabled", repository.isApplicationTokenEnabled())
                put("exportedAt", System.currentTimeMillis())
            }
        } finally {
            repository.close()
        }

        val recoveryKey = randomBytes(RECOVERY_KEY_SIZE_BYTES).toUrlBase64()
        val salt = randomBytes(SALT_SIZE_BYTES)
        val iv = randomBytes(GCM_IV_SIZE_BYTES)
        val keySpec = PBEKeySpec(
            recoveryKey.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            AES_KEY_SIZE_BITS,
        )
        val derivedKey = SecretKeyFactory
            .getInstance(PBKDF2_ALGORITHM)
            .generateSecret(keySpec)
            .encoded
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(derivedKey, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, iv),
        )
        val encrypted = cipher.doFinal(accountJson.toString().encodeToByteArray())

        return EncryptedAccountSession(
            recoveryKey = recoveryKey,
            payload = JSONObject().apply {
                put("version", 1)
                put("algorithm", AES_GCM_TRANSFORMATION)
                put("kdf", PBKDF2_ALGORITHM)
                put("iterations", PBKDF2_ITERATIONS)
                put("salt", salt.toUrlBase64())
                put("iv", iv.toUrlBase64())
                put("ciphertext", encrypted.toUrlBase64())
            },
        )
    }

    private fun restoreAccountSession(
        encryptedPayload: JSONObject,
        recoveryKey: String,
    ) {
        val accountJson = decryptAccountSession(encryptedPayload, recoveryKey)
        val accessToken = accountJson.optString("accessToken").takeIf(String::isNotBlank)
            ?: throw InvalidBackupException("Account token is missing")
        val applicationToken = accountJson.optString("applicationToken").takeIf(String::isNotBlank)
        val applicationTokenEnabled = accountJson.optBoolean("applicationTokenEnabled", false)

        AndroidKeystoreYummyAccountTokenStore(appContext).saveAccessToken(accessToken)
        AndroidKeystoreYummyApplicationTokenStore(appContext).apply {
            if (applicationToken.isNullOrBlank()) {
                clearApplicationToken()
            } else {
                saveApplicationToken(applicationToken)
                setApplicationTokenEnabled(applicationTokenEnabled)
            }
        }
    }

    private fun decryptAccountSession(
        encryptedPayload: JSONObject,
        recoveryKey: String,
    ): JSONObject {
        return try {
            val salt = encryptedPayload.getString("salt").fromUrlBase64()
            val iv = encryptedPayload.getString("iv").fromUrlBase64()
            val encrypted = encryptedPayload.getString("ciphertext").fromUrlBase64()
            val keySpec = PBEKeySpec(
                recoveryKey.toCharArray(),
                salt,
                encryptedPayload.optInt("iterations", PBKDF2_ITERATIONS),
                AES_KEY_SIZE_BITS,
            )
            val derivedKey = SecretKeyFactory
                .getInstance(encryptedPayload.optString("kdf", PBKDF2_ALGORITHM))
                .generateSecret(keySpec)
                .encoded
            val cipher = Cipher.getInstance(encryptedPayload.optString("algorithm", AES_GCM_TRANSFORMATION))
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derivedKey, AES_ALGORITHM),
                GCMParameterSpec(GCM_TAG_SIZE_BITS, iv),
            )
            JSONObject(cipher.doFinal(encrypted).decodeToString())
        } catch (_: AEADBadTagException) {
            throw InvalidBackupRecoveryKeyException()
        } catch (_: IllegalArgumentException) {
            throw InvalidBackupRecoveryKeyException()
        }
    }

    private fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also(random::nextBytes)
    }

    private fun ZipOutputStream.writeJsonEntry(
        path: String,
        json: JSONObject,
    ) {
        putNextEntry(ZipEntry(path))
        write(json.toString(2).encodeToByteArray())
        closeEntry()
    }

    private fun readZipEntries(source: Uri): Map<String, String> {
        return appContext.contentResolver.openInputStream(source)?.use { inputStream ->
            ZipInputStream(inputStream.buffered()).use { zip ->
                buildMap {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory) {
                            put(entry.name, zip.readBytes().decodeToString())
                        }
                        zip.closeEntry()
                    }
                }
            }
        } ?: throw InvalidBackupException("Could not open backup file")
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun ByteArray.toUrlBase64(): String {
        return Base64.encodeToString(
            this,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun String.fromUrlBase64(): ByteArray {
        return Base64.decode(
            this,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private data class EncryptedAccountSession(
        val recoveryKey: String,
        val payload: JSONObject,
    )

    private companion object {
        const val FORMAT_NAME = "hibiki-backup"
        const val FORMAT_VERSION = 1
        const val MANIFEST_PATH = "manifest.json"
        const val ACCOUNT_SESSION_PATH = "account/session.enc.json"
        const val RECOVERY_KEY_SIZE_BYTES = 32
        const val SALT_SIZE_BYTES = 16
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
        const val AES_KEY_SIZE_BITS = 256
        const val PBKDF2_ITERATIONS = 120_000
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
