package org.akkirrai.hibiki.shared.source

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.SourceConfigState
import org.akkirrai.beakokit.api.SourceConfigStateException
import org.akkirrai.beakokit.api.SourceConfigStore
import org.akkirrai.beakokit.api.SourceId

/** Android source configuration storage kept separate from package activation metadata. */
class AndroidSourceConfigStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ),
) : SourceConfigStore {
    override fun load(sourceId: SourceId): SourceConfigState {
        val raw = preferences.getString(key(sourceId), null) ?: return SourceConfigState()
        return try {
            json.decodeFromString(raw)
        } catch (error: Exception) {
            throw SourceConfigStateException(
                message = "Source config state is corrupted: $sourceId",
                cause = error,
            )
        }
    }

    override fun persistAtomically(sourceId: SourceId, state: SourceConfigState) {
        check(
            preferences.edit()
                .putString(key(sourceId), json.encodeToString(state))
                .commit(),
        ) { "Could not persist source config state: $sourceId" }
    }

    override fun remove(sourceId: SourceId) {
        check(
            preferences.edit()
                .remove(key(sourceId))
                .commit(),
        ) { "Could not remove source config state: $sourceId" }
    }

    private fun key(sourceId: SourceId): String = "$CONFIG_KEY_PREFIX${sourceId.value}"

    private companion object {
        const val PREFERENCES_NAME = "beakokit_external_sources"
        const val CONFIG_KEY_PREFIX = "config."
    }
}
