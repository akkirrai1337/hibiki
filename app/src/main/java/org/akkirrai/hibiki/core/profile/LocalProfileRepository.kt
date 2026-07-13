package org.akkirrai.hibiki.core.profile

import android.content.Context
import java.time.LocalDate
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

/** Local, source-agnostic data used by the profile analytics. */
class LocalProfileRepository(
    context: Context,
    private val watchStateRepository: WatchStateRepository = WatchStateRepository(context.applicationContext),
    private val libraryRepository: LibraryRepository = LibraryRepository(context.applicationContext),
    private val conflictRepository: ProfileDataConflictRepository = ProfileDataConflictRepository(context.applicationContext),
) {
    fun getData(): LocalProfileData {
        val entries = libraryRepository.getLibraryEntries()
        val library = entries
            .groupBy { it.anime.id }
            .map { (id, sameTitleEntries) ->
                LocalLibraryItem(
                    id = id,
                    anime = sameTitleEntries.first().anime,
                    categories = sameTitleEntries.mapTo(linkedSetOf()) { it.category },
                    addedAt = sameTitleEntries.mapNotNull { it.addedAt }.minOrNull(),
                )
            }

        return LocalProfileData(
            episodeProgress = watchStateRepository.getAllEpisodeProgress(),
            library = library,
            importedActivityCounts = getImportedActivityCounts(),
            activityOverrides = conflictRepository.resolvedActivityCounts(),
        )
    }

    /**
     * Persists historical activity received from a remote profile only for days that
     * do not have local playback records. Local playback always wins for its day.
     */
    fun mergeRemoteActivity(remoteActivityCounts: Map<LocalDate, Int>) {
        val localActivityCounts = watchStateRepository.getAllEpisodeProgress()
            .filter { it.positionMs > 0L }
            .groupBy { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.updatedAt), java.time.ZoneId.systemDefault()) }
            .mapValues { (_, values) -> values.distinctBy { "${it.titleId}:${it.episodeId}" }.size }
        val merged = getImportedActivityCounts().toMutableMap()
        remoteActivityCounts.forEach { (date, episodeCount) ->
            val localValue = localActivityCounts[date]
            if (localValue == null && episodeCount > 0) {
                merged[date] = episodeCount
            } else if (localValue != null) {
                merged.remove(date)
                if (localValue != episodeCount) {
                    conflictRepository.recordActivityConflict(date, localValue, episodeCount)
                }
            }
        }
        preferences.edit()
            .putStringSet(
                KEY_IMPORTED_ACTIVITY,
                merged.mapTo(linkedSetOf()) { (date, count) -> "$date$ACTIVITY_SEPARATOR$count" },
            )
            .apply()
    }

    private fun getImportedActivityCounts(): Map<LocalDate, Int> = preferences
        .getStringSet(KEY_IMPORTED_ACTIVITY, emptySet()).orEmpty()
        .mapNotNull { encoded ->
            val parts = encoded.split(ACTIVITY_SEPARATOR, limit = 2)
            val date = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (date != null && count != null && count > 0) date to count else null
        }
        .toMap()

    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "hibiki_local_profile"
        const val KEY_IMPORTED_ACTIVITY = "imported_activity"
        const val ACTIVITY_SEPARATOR = '|'
    }
}

data class LocalProfileData(
    val episodeProgress: List<EpisodeWatchProgress> = emptyList(),
    val library: List<LocalLibraryItem> = emptyList(),
    val importedActivityCounts: Map<LocalDate, Int> = emptyMap(),
    val activityOverrides: Map<LocalDate, Int> = emptyMap(),
)

data class LocalLibraryItem(
    val id: String,
    val anime: Anime,
    val categories: Set<LibraryCategory>,
    val addedAt: Long?,
)
