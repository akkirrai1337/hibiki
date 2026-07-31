package org.akkirrai.hibiki.desktop

import java.util.Base64
import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository

internal class DesktopPlaybackProgressRepository : PlaybackProgressRepository {
    private val preferences = Preferences.userNodeForPackage(DesktopPlaybackProgressRepository::class.java)

    override fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 0L) return
        val key = encode("${context.titleId}|${context.episodeId}")
        val value = listOf(
            context.episodeNumber,
            context.sourceId,
            context.sourceTitle,
            playback.qualityLabel.orEmpty(),
            positionMs.coerceAtLeast(0L),
            durationMs.coerceAtLeast(0L),
            System.currentTimeMillis(),
        ).joinToString(RECORD_SEPARATOR)
        preferences.put("$PROGRESS_PREFIX$key", value)
        preferences.flush()
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

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val PROGRESS_PREFIX = "progress_"
        const val RECORD_SEPARATOR = "\u001F"
    }
}
