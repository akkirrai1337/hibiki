package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceHostStorage
import org.akkirrai.beakokit.api.SourceId

/** iOS persistent storage isolated by the external source ID. */
@OptIn(ExperimentalForeignApi::class)
internal class IosSourceHostStorage(
    sourceId: SourceId,
    override val requirements: SourceHostRequirements,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SourceHostStorage() {
    private val keyPrefix = "beakokit.source.${sourceId.value}.storage."

    override suspend fun readValue(key: String): String? =
        defaults.stringForKey(storageKey(key))

    override suspend fun writeValue(key: String, value: String) {
        defaults.setObject(value, forKey = storageKey(key))
    }

    override suspend fun removeValue(key: String) {
        defaults.removeObjectForKey(storageKey(key))
    }

    override suspend fun storedSizeBytes(): Long = storageEntries().values.sumOf { value ->
        value.encodeToByteArray().size.toLong()
    }

    override suspend fun storedEntryCount(): Int = storageEntries().size

    private fun storageKey(key: String): String = keyPrefix + key

    @Suppress("UNCHECKED_CAST")
    private fun storageEntries(): Map<String, String> =
        defaults.dictionaryRepresentation().entries.mapNotNull { (key, value) ->
            val stringKey = key as? String ?: return@mapNotNull null
            val stringValue = value as? String ?: return@mapNotNull null
            if (stringKey.startsWith(keyPrefix)) {
                stringKey.removePrefix(keyPrefix) to stringValue
            } else {
                null
            }
        }.toMap()
}
