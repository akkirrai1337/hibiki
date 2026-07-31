package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress

/** Platform storage boundary for playback progress written by shared hosts. */
interface PlaybackProgressRepository {
    fun getPlaybackProgress(titleId: String, episodeId: String): EpisodeWatchProgress?

    fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    )
}
