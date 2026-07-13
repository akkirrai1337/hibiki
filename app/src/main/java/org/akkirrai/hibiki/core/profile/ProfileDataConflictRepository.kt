package org.akkirrai.hibiki.core.profile

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import org.akkirrai.hibiki.core.source.LibraryCategory

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

    fun recordLibraryStatusConflict(
        animeId: String,
        title: String,
        localCategory: LibraryCategory,
        remoteCategory: LibraryCategory,
    ) {
        val key = libraryKey(animeId)
        val localValue = localCategory.syncValue() ?: return
        val remoteValue = remoteCategory.syncValue() ?: return
        val current = get(key)
        if (
            current?.field == ProfileDataField.LibraryStatus &&
            current.localValue == localValue &&
            current.remoteValue == remoteValue
        ) {
            return
        }
        save(
            ProfileDataConflict(
                key = key,
                field = ProfileDataField.LibraryStatus,
                title = title,
                localValue = localValue,
                remoteValue = remoteValue,
                detectedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun pendingLibraryStatusConflicts(): List<LibraryStatusConflict> = conflictKeys()
        .mapNotNull(::get)
        .filter { it.field == ProfileDataField.LibraryStatus && it.resolution == null }
        .mapNotNull { conflict ->
            val localCategory = LibraryCategory.fromSyncValue(conflict.localValue) ?: return@mapNotNull null
            val remoteCategory = LibraryCategory.fromSyncValue(conflict.remoteValue) ?: return@mapNotNull null
            LibraryStatusConflict(
                key = conflict.key,
                animeId = conflict.key.removePrefix(LIBRARY_KEY_PREFIX),
                title = conflict.title.orEmpty(),
                localCategory = localCategory,
                remoteCategory = remoteCategory,
                detectedAt = conflict.detectedAt,
            )
        }
        .sortedByDescending(LibraryStatusConflict::detectedAt)

    fun clearLibraryStatusConflict(key: String) {
        preferences.edit()
            .remove(conflictStorageKey(key))
            .putStringSet(KEY_CONFLICTS, conflictKeys() - key)
            .apply()
    }

    fun clearLibraryStatusConflictForAnime(animeId: String) {
        clearLibraryStatusConflict(libraryKey(animeId))
    }

    fun retainLibraryStatusConflicts(animeIds: Set<String>) {
        pendingLibraryStatusConflicts()
            .filter { it.animeId !in animeIds }
            .forEach { clearLibraryStatusConflict(it.key) }
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
        conflict.title?.let { put("title", it) }
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
            title = json.optString("title").takeIf(String::isNotBlank),
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
    private fun libraryKey(animeId: String): String = "$LIBRARY_KEY_PREFIX$animeId"
    private fun conflictStorageKey(key: String): String = "conflict_$key"

    private companion object {
        const val PREFS_NAME = "hibiki_profile_conflicts"
        const val KEY_CONFLICTS = "keys"
        const val ACTIVITY_KEY_PREFIX = "activity:"
        const val LIBRARY_KEY_PREFIX = "library:"
    }
}

data class ProfileDataConflict(
    val key: String,
    val field: ProfileDataField,
    val title: String? = null,
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

data class LibraryStatusConflict(
    val key: String,
    val animeId: String,
    val title: String,
    val localCategory: LibraryCategory,
    val remoteCategory: LibraryCategory,
    val detectedAt: Long,
)

private fun LibraryCategory.syncValue(): Long? = when (this) {
    LibraryCategory.Watching -> 0L
    LibraryCategory.Planned -> 1L
    LibraryCategory.Completed -> 2L
    LibraryCategory.Dropped -> 3L
    LibraryCategory.OnHold -> 5L
    LibraryCategory.Favorite, LibraryCategory.Saved -> null
}

private fun LibraryCategory.Companion.fromSyncValue(value: Long): LibraryCategory? = when (value) {
    0L -> LibraryCategory.Watching
    1L -> LibraryCategory.Planned
    2L -> LibraryCategory.Completed
    3L -> LibraryCategory.Dropped
    5L -> LibraryCategory.OnHold
    else -> null
}

enum class ProfileDataField { ActivityEpisodeCount, LibraryStatus }
enum class ProfileDataSource { Local, Remote, Custom }
