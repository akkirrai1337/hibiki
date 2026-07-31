package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream

/** Platform storage boundary for playback progress written by shared hosts. */
interface PlaybackProgressRepository {
    fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    )
}
