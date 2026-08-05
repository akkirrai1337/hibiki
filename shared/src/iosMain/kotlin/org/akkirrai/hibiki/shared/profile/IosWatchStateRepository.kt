package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.PlaybackSelection
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSUserDefaults

/** Reads the iOS playback records written by the shared player adapter. */
internal class IosWatchStateRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : LocalWatchStateRepository, PlaybackProgressRepository {
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

    override fun getPlaybackProgress(
        titleId: String,
        episodeId: String,
    ): EpisodeWatchProgress? = getAllEpisodeProgress()
        .firstOrNull { it.titleId == titleId && it.episodeId == episodeId }

    override fun getAllPlaybackProgress(): List<EpisodeWatchProgress> = getAllEpisodeProgress()

    override fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    ) {
        val previous = getPlaybackProgress(context.titleId, context.episodeId)
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        val encoded = listOf(
            context.episodeNumber.toString(),
            context.sourceId,
            context.sourceId,
            context.sourceTitle,
            playback.qualityLabel.orEmpty(),
            safePosition.toString(),
            safeDuration.toString(),
            updatedAt.toString(),
        ).joinToString(RECORD_SEPARATOR.toString())
        defaults.setObject(
            encoded,
            forKey = "$PROGRESS_PREFIX${context.titleId}$EPISODE_KEY_SEPARATOR${context.episodeId}",
        )
        recordActivity(
            titleId = context.titleId,
            episodeId = context.episodeId,
            previousPositionMs = previous?.positionMs ?: 0L,
            positionMs = safePosition,
            durationMs = safeDuration,
            updatedAt = updatedAt,
        )
    }

    internal fun savePlaybackSelection(selection: PlaybackSelection) {
        defaults.setObject(selection.sourceId, forKey = "${SELECTION_PREFIX}${selection.titleId}_source")
        defaults.setObject(selection.sourceTitle, forKey = "${SELECTION_PREFIX}${selection.titleId}_source_title")
        defaults.setObject(selection.quality, forKey = "${SELECTION_PREFIX}${selection.titleId}_quality")
        defaults.setObject(selection.playerName, forKey = "${SELECTION_PREFIX}${selection.titleId}_player")
    }

    internal fun loadPlaybackSelection(titleId: String): PlaybackSelection? {
        val sourceId = defaults.stringForKey("${SELECTION_PREFIX}${titleId}_source") ?: return null
        return PlaybackSelection(
            titleId = titleId,
            sourceId = sourceId,
            sourceTitle = defaults.stringForKey("${SELECTION_PREFIX}${titleId}_source_title").orEmpty(),
            quality = defaults.stringForKey("${SELECTION_PREFIX}${titleId}_quality"),
            playerName = defaults.stringForKey("${SELECTION_PREFIX}${titleId}_player"),
        )
    }

    private fun recordActivity(
        titleId: String,
        episodeId: String,
        previousPositionMs: Long,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
    ) {
        val deltaMs = (positionMs - previousPositionMs).coerceAtLeast(0L).coerceAtMost(durationMs)
        val completed = durationMs > 0L && positionMs >= durationMs * COMPLETION_THRESHOLD_PERCENT / 100L
        if (deltaMs == 0L && !completed) return

        val date = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
            .date
            .toString()
        val completedKey = "$ACTIVITY_COMPLETED_PREFIX$date"
        val completedEpisodes = defaults.arrayForKey(completedKey)
            ?.filterIsInstance<String>()
            ?.toMutableSet()
            ?: mutableSetOf()
        if (completed) completedEpisodes += "$titleId:$episodeId"
        defaults.setObject(
            (defaults.objectForKey("$ACTIVITY_WATCHED_PREFIX$date") as? Number ?: 0L).toLong() + deltaMs,
            forKey = "$ACTIVITY_WATCHED_PREFIX$date",
        )
        defaults.setObject(completedEpisodes.toList(), forKey = completedKey)
    }

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
        const val SELECTION_PREFIX = "playback_selection_"
        const val EPISODE_KEY_SEPARATOR = "|episode|"
        const val RECORD_SEPARATOR = '\u001F'
        const val ACTIVITY_WATCHED_PREFIX = "activity_watched_"
        const val ACTIVITY_COMPLETED_PREFIX = "activity_completed_"
        const val COMPLETION_THRESHOLD_PERCENT = 90L
    }
}
