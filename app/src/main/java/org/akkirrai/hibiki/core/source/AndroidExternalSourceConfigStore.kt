package org.akkirrai.hibiki.core.source

import android.content.Context
import android.content.SharedPreferences
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.core.account.AndroidKeystoreStringStore

/** Persistent per-source configuration; secret values are encrypted by Android Keystore. */
internal class AndroidExternalSourceConfigStore(
    context: Context,
    private val valuesPreferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        VALUES_PREFERENCES,
        Context.MODE_PRIVATE,
    ),
    private val secretKeysPreferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        SECRET_KEYS_PREFERENCES,
        Context.MODE_PRIVATE,
    ),
    private val secretStore: AndroidKeystoreStringStore = AndroidKeystoreStringStore(
        context = context.applicationContext,
        prefsName = SECRETS_PREFERENCES,
        keyAlias = SECRETS_KEY_ALIAS,
    ),
) {
    fun load(sourceId: SourceId): SourceConfig {
        val prefix = valuePrefix(sourceId)
        val values = valuesPreferences.all.mapNotNull { (key, value) ->
            key.removePrefix(prefix).takeIf { key.startsWith(prefix) && value is String }
                ?.let { configKey -> configKey to value as String }
        }.toMap()
        val secrets = secretKeysPreferences.getStringSet(sourceId.value, emptySet()).orEmpty()
            .mapNotNull { key -> secretStore.get(secretKey(sourceId, key))?.let { key to it } }
            .toMap()
        return MapSourceConfig(values = values, secrets = secrets)
    }

    fun saveValue(sourceId: SourceId, key: String, value: String) {
        requireConfigKey(key)
        check(valuesPreferences.edit().putString(valueKey(sourceId, key), value).commit()) {
            "Could not persist external source config"
        }
    }

    fun clearValue(sourceId: SourceId, key: String) {
        requireConfigKey(key)
        check(valuesPreferences.edit().remove(valueKey(sourceId, key)).commit()) {
            "Could not clear external source config"
        }
    }

    fun saveSecret(sourceId: SourceId, key: String, value: String) {
        requireConfigKey(key)
        secretStore.save(secretKey(sourceId, key), value)
        updateSecretKeys(sourceId) { it + key }
    }

    fun clearSecret(sourceId: SourceId, key: String) {
        requireConfigKey(key)
        secretStore.clear(secretKey(sourceId, key))
        updateSecretKeys(sourceId) { it - key }
    }

    private fun updateSecretKeys(sourceId: SourceId, update: (Set<String>) -> Set<String>) {
        val current = secretKeysPreferences.getStringSet(sourceId.value, emptySet()).orEmpty()
        check(secretKeysPreferences.edit().putStringSet(sourceId.value, update(current)).commit()) {
            "Could not persist external source secret index"
        }
    }

    private fun valuePrefix(sourceId: SourceId): String = "${sourceId.value}:"

    private fun valueKey(sourceId: SourceId, key: String): String = valuePrefix(sourceId) + key

    private fun secretKey(sourceId: SourceId, key: String): String = valuePrefix(sourceId) + key

    private companion object {
        const val VALUES_PREFERENCES = "beakokit_external_source_config"
        const val SECRET_KEYS_PREFERENCES = "beakokit_external_source_secret_keys"
        const val SECRETS_PREFERENCES = "beakokit_external_source_secrets"
        const val SECRETS_KEY_ALIAS = "beakokit_external_source_secrets"

        fun requireConfigKey(key: String) {
            require(Regex("[a-z][a-z0-9_]*").matches(key)) {
                "Invalid external source config key: $key"
            }
        }
    }
}
