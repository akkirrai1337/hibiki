package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.shared.security.hibiki_keychain_delete
import org.akkirrai.hibiki.shared.security.hibiki_keychain_read
import org.akkirrai.hibiki.shared.security.hibiki_keychain_write

/** iOS external-source configuration with Keychain-backed secret values. */
@OptIn(ExperimentalForeignApi::class)
internal class IosExternalSourceConfigStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    data class Draft(
        val values: Map<String, String>,
        val secrets: Map<String, String>,
    )

    fun loadDraft(sourceId: SourceId): Draft {
        val prefix = valuePrefix(sourceId)
        val values = defaults.dictionaryRepresentation().entries.mapNotNull { (key, value) ->
            val stringKey = key as? String ?: return@mapNotNull null
            val stringValue = value as? String ?: return@mapNotNull null
            stringKey.removePrefix(prefix)
                .takeIf { stringKey.startsWith(prefix) }
                ?.let { it to stringValue }
        }.toMap()
        val secrets = secretKeys(sourceId).mapNotNull { key ->
            readSecret(secretKey(sourceId, key))?.let { key to it }
        }.toMap()
        return Draft(values = values, secrets = secrets)
    }

    fun load(sourceId: SourceId): SourceConfig {
        val draft = loadDraft(sourceId)
        return MapSourceConfig(values = draft.values, secrets = draft.secrets)
    }

    fun saveValue(sourceId: SourceId, key: String, value: String) {
        requireConfigKey(key)
        if (value.isBlank()) clearValue(sourceId, key)
        else defaults.setObject(value, forKey = valueKey(sourceId, key))
    }

    fun clearValue(sourceId: SourceId, key: String) {
        requireConfigKey(key)
        defaults.removeObjectForKey(valueKey(sourceId, key))
    }

    fun saveSecret(sourceId: SourceId, key: String, value: String) {
        requireConfigKey(key)
        if (value.isBlank()) {
            clearSecret(sourceId, key)
            return
        }
        val bytes = value.encodeToByteArray()
        val saved = bytes.usePinned { pinned ->
            hibiki_keychain_write(
                KEYCHAIN_SERVICE,
                secretKey(sourceId, key),
                if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret<UByteVar>(),
                bytes.size.toULong(),
            )
        }
        check(saved) { "Could not persist external source secret in Keychain" }
        updateSecretKeys(sourceId) { it + key }
    }

    fun clearSecret(sourceId: SourceId, key: String) {
        requireConfigKey(key)
        check(hibiki_keychain_delete(KEYCHAIN_SERVICE, secretKey(sourceId, key))) {
            "Could not remove external source secret from Keychain"
        }
        updateSecretKeys(sourceId) { it - key }
    }

    private fun readSecret(account: String): String? =
        hibiki_keychain_read(KEYCHAIN_SERVICE, account)?.toByteArray()?.decodeToString()

    private fun updateSecretKeys(sourceId: SourceId, update: (Set<String>) -> Set<String>) {
        defaults.setObject(
            update(secretKeys(sourceId)).sorted().joinToString(KEY_SEPARATOR),
            forKey = secretKeysKey(sourceId),
        )
    }

    private fun secretKeys(sourceId: SourceId): Set<String> =
        defaults.stringForKey(secretKeysKey(sourceId))
            ?.split(KEY_SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty()

    private fun valuePrefix(sourceId: SourceId): String = "beakokit.source.${sourceId.value}.config."
    private fun valueKey(sourceId: SourceId, key: String): String = valuePrefix(sourceId) + key
    private fun secretKeysKey(sourceId: SourceId): String =
        "beakokit.source.${sourceId.value}.config.secret-keys"
    private fun secretKey(sourceId: SourceId, key: String): String =
        "beakokit.source.${sourceId.value}.config.$key"

    private companion object {
        const val KEYCHAIN_SERVICE = "org.akkirrai.hibiki.external-source-config"
        const val KEY_SEPARATOR = "\u0000"

        fun requireConfigKey(key: String) {
            require(Regex("[a-z][a-z0-9_]*").matches(key)) {
                "Invalid external source config key: $key"
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
}
