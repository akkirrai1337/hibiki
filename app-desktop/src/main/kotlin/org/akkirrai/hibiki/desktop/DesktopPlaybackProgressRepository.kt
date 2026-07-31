package org.akkirrai.hibiki.desktop

import java.util.Base64
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.profile.DailyWatchActivity
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository

internal class DesktopPlaybackProgressRepository(
    private val preferences: Preferences = Preferences.userNodeForPackage(DesktopPlaybackProgressRepository::class.java),
) : PlaybackProgressRepository {

    override fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 0L) return
        val previous = getPlaybackProgress(context.titleId, context.episodeId)
        val updatedAt = System.currentTimeMillis()
        val key = encode("${context.titleId}|${context.episodeId}")
        val value = listOf(
            context.episodeNumber,
            context.sourceId,
            context.sourceTitle,
            playback.qualityLabel.orEmpty(),
            positionMs.coerceAtLeast(0L),
            durationMs.coerceAtLeast(0L),
            updatedAt,
        ).joinToString(RECORD_SEPARATOR)
        preferences.put("$PROGRESS_PREFIX$key", value)
        recordActivity(
            titleId = context.titleId,
            episodeId = context.episodeId,
            previousPositionMs = previous?.positionMs ?: 0L,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = updatedAt,
        )
        preferences.flush()
    }

    fun getDailyWatchActivity(): List<DailyWatchActivity> {
        val cutoffDate = activityCutoffDate()
        pruneActivityBefore(cutoffDate)
        return preferences.keys()
            .asSequence()
            .filter { it.startsWith(ACTIVITY_WATCHED_PREFIX) }
            .mapNotNull { key ->
                val date = runCatching {
                    LocalDate.parse(key.removePrefix(ACTIVITY_WATCHED_PREFIX))
                }.getOrNull() ?: return@mapNotNull null
                DailyWatchActivity(
                    date = date.toString(),
                    watchedMs = preferences.getLong(key, 0L).coerceAtLeast(0L),
                    completedEpisodes = preferences.get(
                        completedActivityKey(date),
                        "",
                    ).split(RECORD_SEPARATOR)
                        .count { it.isNotBlank() },
                )
            }
            .sortedBy(DailyWatchActivity::date)
            .toList()
    }

    override fun getPlaybackProgress(titleId: String, episodeId: String): EpisodeWatchProgress? {
        val key = encode("$titleId|$episodeId")
        val parts = preferences.get("$PROGRESS_PREFIX$key", null)
            ?.split(RECORD_SEPARATOR)
            ?: return null
        if (parts.size != 7) return null
        return EpisodeWatchProgress(
            titleId = titleId,
            episodeId = episodeId,
            episodeNumber = parts[0].toDoubleOrNull() ?: return null,
            sourceId = parts[1],
            voiceoverId = parts[1],
            sourceTitle = parts[2],
            quality = parts[3].ifBlank { null },
            positionMs = parts[4].toLongOrNull() ?: return null,
            durationMs = parts[5].toLongOrNull() ?: return null,
            updatedAt = parts[6].toLongOrNull() ?: return null,
        )
    }

    override fun getAllPlaybackProgress(): List<EpisodeWatchProgress> = preferences.keys()
        .asSequence()
        .filter { it.startsWith(PROGRESS_PREFIX) }
        .mapNotNull { key ->
            val identity = runCatching {
                String(Base64.getUrlDecoder().decode(key.removePrefix(PROGRESS_PREFIX)), Charsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            val separator = identity.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            getPlaybackProgress(identity.substring(0, separator), identity.substring(separator + 1))
        }
        .toList()

    private fun recordActivity(
        titleId: String,
        episodeId: String,
        previousPositionMs: Long,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
    ) {
        val deltaMs = (positionMs - previousPositionMs)
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.coerceAtLeast(0L))
        val completed = durationMs > 0L && positionMs >= durationMs * COMPLETION_THRESHOLD_PERCENT / 100L
        if (deltaMs == 0L && !completed) return

        val date = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val cutoffDate = activityCutoffDate()
        pruneActivityBefore(cutoffDate)
        if (date.isBefore(cutoffDate)) return

        val completedKey = completedActivityKey(date)
        val completedEpisodes = preferences.get(completedKey, "")
            .split(RECORD_SEPARATOR)
            .filter { it.isNotBlank() }
            .toMutableSet()
        if (completed) completedEpisodes += "$titleId:$episodeId"
        preferences.putLong(
            activityWatchedKey(date),
            preferences.getLong(activityWatchedKey(date), 0L) + deltaMs,
        )
        preferences.put(completedKey, completedEpisodes.joinToString(RECORD_SEPARATOR))
    }

    private fun activityWatchedKey(date: LocalDate): String = "$ACTIVITY_WATCHED_PREFIX$date"

    private fun completedActivityKey(date: LocalDate): String = "$ACTIVITY_COMPLETED_PREFIX$date"

    private fun activityCutoffDate(): LocalDate =
        LocalDate.now().minusDays((ACTIVITY_RETENTION_DAYS - 1).toLong())

    private fun pruneActivityBefore(cutoffDate: LocalDate) {
        preferences.keys()
            .filter { key ->
                val rawDate = when {
                    key.startsWith(ACTIVITY_WATCHED_PREFIX) -> key.removePrefix(ACTIVITY_WATCHED_PREFIX)
                    key.startsWith(ACTIVITY_COMPLETED_PREFIX) -> key.removePrefix(ACTIVITY_COMPLETED_PREFIX)
                    else -> return@filter false
                }
                runCatching { LocalDate.parse(rawDate) }.getOrNull()?.isBefore(cutoffDate) == true
            }
            .forEach(preferences::remove)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val PROGRESS_PREFIX = "progress_"
        const val ACTIVITY_WATCHED_PREFIX = "activity_watched_"
        const val ACTIVITY_COMPLETED_PREFIX = "activity_completed_"
        const val ACTIVITY_RETENTION_DAYS = 90
        const val COMPLETION_THRESHOLD_PERCENT = 90L
        const val RECORD_SEPARATOR = "\u001F"
    }
}
