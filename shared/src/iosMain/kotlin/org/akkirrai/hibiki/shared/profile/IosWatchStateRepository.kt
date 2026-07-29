package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import platform.Foundation.NSUserDefaults

/** Reads the iOS playback records written by the shared player adapter. */
internal class IosWatchStateRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : LocalWatchStateRepository {
    override fun getAllEpisodeProgress(): List<EpisodeWatchProgress> = defaults
        .dictionaryRepresentation()
        .asSequence()
        .mapNotNull { (key, value) ->
            val storageKey = key as? String ?: return@mapNotNull null
            val encoded = value as? String ?: return@mapNotNull null
            if (!storageKey.startsWith(PROGRESS_PREFIX)) return@mapNotNull null
            val payload = storageKey.removePrefix(PROGRESS_PREFIX)
            val separatorIndex = payload.indexOf(EPISODE_KEY_SEPARATOR)
            if (separatorIndex <= 0) return@mapNotNull null
            parseProgress(
                titleId = payload.substring(0, separatorIndex),
                episodeId = payload.substring(separatorIndex + EPISODE_KEY_SEPARATOR.length),
                encoded = encoded,
            )
        }
        .distinctBy { it.titleId to it.episodeId }
        .toList()

    override fun getDailyWatchActivity(): List<DailyWatchActivity> = defaults
        .dictionaryRepresentation()
        .asSequence()
        .mapNotNull { (key, value) ->
            val storageKey = key as? String ?: return@mapNotNull null
            if (!storageKey.startsWith(ACTIVITY_WATCHED_PREFIX)) return@mapNotNull null
            val date = storageKey.removePrefix(ACTIVITY_WATCHED_PREFIX)
            val watchedMs = (value as? Number)?.toLong() ?: return@mapNotNull null
            val completed = defaults.arrayForKey("$ACTIVITY_COMPLETED_PREFIX$date")
                ?.filterIsInstance<String>()
                ?.size
                ?: 0
            DailyWatchActivity(date = date, watchedMs = watchedMs, completedEpisodes = completed)
        }
        .sortedBy(DailyWatchActivity::date)
        .toList()

    private fun parseProgress(titleId: String, episodeId: String, encoded: String): EpisodeWatchProgress? {
        val parts = encoded.split(RECORD_SEPARATOR)
        if (parts.size < 8) return null
        return EpisodeWatchProgress(
            titleId = titleId,
            episodeId = episodeId,
            episodeNumber = parts[0].toDoubleOrNull() ?: return null,
            sourceId = parts[1],
            voiceoverId = parts[2],
            sourceTitle = parts[3],
            quality = parts[4].ifBlank { null },
            positionMs = parts[5].toLongOrNull() ?: 0L,
            durationMs = parts[6].toLongOrNull() ?: 0L,
            updatedAt = parts[7].toLongOrNull() ?: 0L,
        )
    }

    private companion object {
        const val PROGRESS_PREFIX = "progress_"
        const val EPISODE_KEY_SEPARATOR = "|episode|"
        const val RECORD_SEPARATOR = '\u001F'
        const val ACTIVITY_WATCHED_PREFIX = "activity_watched_"
        const val ACTIVITY_COMPLETED_PREFIX = "activity_completed_"
    }
}
