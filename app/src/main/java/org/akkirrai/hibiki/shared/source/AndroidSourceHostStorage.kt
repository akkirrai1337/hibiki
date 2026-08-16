package org.akkirrai.hibiki.shared.source

import android.content.Context
import android.content.SharedPreferences
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceHostStorage
import org.akkirrai.beakokit.api.SourceId

/** Android persistent storage isolated by the external source ID. */
internal class AndroidSourceHostStorage(
    context: Context,
    sourceId: SourceId,
    override val requirements: SourceHostRequirements,
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "beakokit_source_storage_${sourceId.value}",
        Context.MODE_PRIVATE,
    ),
) : SourceHostStorage() {
    override suspend fun readValue(key: String): String? = preferences.getString(key, null)

    override suspend fun writeValue(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Could not persist source storage value"
        }
    }

    override suspend fun removeValue(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Could not remove source storage value"
        }
    }

    override suspend fun storedSizeBytes(): Long = preferences.stringValues().sumOf { value ->
        value.encodeToByteArray().size.toLong()
    }

    override suspend fun storedEntryCount(): Int = preferences.stringValues().size

    private fun SharedPreferences.stringValues(): List<String> = all.values.mapNotNull { value ->
        value as? String
    }
}
