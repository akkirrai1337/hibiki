package org.akkirrai.hibiki.core.profile

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Persistent, UI-independent queue of values where a remote profile and the device
 * disagree. A future settings/profile screen can present [pendingConflicts] and call
 * [resolve] without knowing anything about SharedPreferences.
 */
class ProfileDataConflictRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordActivityConflict(date: LocalDate, localValue: Int, remoteValue: Int) {
        val key = activityKey(date)
        val current = get(key)
        if (current?.resolution != null) return
        save(
            ProfileDataConflict(
                key = key,
                field = ProfileDataField.ActivityEpisodeCount,
                localValue = localValue.toLong(),
                remoteValue = remoteValue.toLong(),
                detectedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun pendingConflicts(): List<ProfileDataConflict> = conflictKeys()
        .mapNotNull(::get)
        .filter { it.resolution == null }
        .sortedByDescending(ProfileDataConflict::detectedAt)

    fun resolve(
        key: String,
        source: ProfileDataSource,
        replacementValue: Long? = null,
    ) {
        val conflict = get(key) ?: return
        require(source != ProfileDataSource.Custom || replacementValue != null) {
            "A custom conflict resolution requires a replacement value."
        }
        save(
            conflict.copy(
                resolution = ProfileDataResolution(
                    source = source,
                    replacementValue = replacementValue,
                    resolvedAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    fun clearResolution(key: String) {
        get(key)?.let { save(it.copy(resolution = null)) }
    }

    fun resolvedActivityCounts(): Map<LocalDate, Int> = conflictKeys()
        .mapNotNull(::get)
        .filter { it.field == ProfileDataField.ActivityEpisodeCount && it.resolution != null }
        .mapNotNull { conflict ->
            val date = conflict.key.removePrefix(ACTIVITY_KEY_PREFIX).let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val value = when (conflict.resolution?.source) {
                ProfileDataSource.Local -> conflict.localValue
                ProfileDataSource.Remote -> conflict.remoteValue
                ProfileDataSource.Custom -> conflict.resolution.replacementValue
                null -> null
            }
            if (date != null && value != null) date to value.toInt().coerceAtLeast(0) else null
        }
        .toMap()

    private fun get(key: String): ProfileDataConflict? {
        val raw = preferences.getString(conflictStorageKey(key), null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    private fun save(conflict: ProfileDataConflict) {
        preferences.edit()
            .putString(conflictStorageKey(conflict.key), encode(conflict))
            .putStringSet(KEY_CONFLICTS, conflictKeys() + conflict.key)
            .apply()
    }

    private fun conflictKeys(): Set<String> = preferences.getStringSet(KEY_CONFLICTS, emptySet()).orEmpty()

    private fun encode(conflict: ProfileDataConflict): String = JSONObject().apply {
        put("key", conflict.key)
        put("field", conflict.field.name)
        put("localValue", conflict.localValue)
        put("remoteValue", conflict.remoteValue)
        put("detectedAt", conflict.detectedAt)
        conflict.resolution?.let { resolution ->
            put("resolutionSource", resolution.source.name)
            put("replacementValue", resolution.replacementValue)
            put("resolvedAt", resolution.resolvedAt)
        }
    }.toString()

    private fun decode(raw: String): ProfileDataConflict {
        val json = JSONObject(raw)
        val resolutionSource = json.optString("resolutionSource").takeIf(String::isNotBlank)
            ?.let(ProfileDataSource::valueOf)
        return ProfileDataConflict(
            key = json.getString("key"),
            field = ProfileDataField.valueOf(json.getString("field")),
            localValue = json.getLong("localValue"),
            remoteValue = json.getLong("remoteValue"),
            detectedAt = json.getLong("detectedAt"),
            resolution = resolutionSource?.let { source ->
                ProfileDataResolution(
                    source = source,
                    replacementValue = json.takeIf { it.has("replacementValue") && !it.isNull("replacementValue") }?.getLong("replacementValue"),
                    resolvedAt = json.optLong("resolvedAt"),
                )
            },
        )
    }

    private fun activityKey(date: LocalDate): String = "$ACTIVITY_KEY_PREFIX$date"
    private fun conflictStorageKey(key: String): String = "conflict_$key"

    private companion object {
        const val PREFS_NAME = "hibiki_profile_conflicts"
        const val KEY_CONFLICTS = "keys"
        const val ACTIVITY_KEY_PREFIX = "activity:"
    }
}

data class ProfileDataConflict(
    val key: String,
    val field: ProfileDataField,
    val localValue: Long,
    val remoteValue: Long,
    val detectedAt: Long,
    val resolution: ProfileDataResolution? = null,
)

data class ProfileDataResolution(
    val source: ProfileDataSource,
    val replacementValue: Long? = null,
    val resolvedAt: Long,
)

enum class ProfileDataField { ActivityEpisodeCount }
enum class ProfileDataSource { Local, Remote, Custom }
