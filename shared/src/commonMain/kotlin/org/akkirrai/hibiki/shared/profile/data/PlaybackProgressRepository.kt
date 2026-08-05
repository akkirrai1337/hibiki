package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress

/** Platform storage boundary for playback progress written by shared hosts. */
interface PlaybackProgressRepository {
    fun getAllPlaybackProgress(): List<EpisodeWatchProgress>

    fun getPlaybackProgress(titleId: String, episodeId: String): EpisodeWatchProgress?

    fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    )
}
