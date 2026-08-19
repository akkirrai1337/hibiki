package org.akkirrai.hibiki.profile

import org.akkirrai.hibiki.player.model.EpisodeWatchProgress

/** Read boundary for locally persisted playback state used by Profile. */
interface LocalWatchStateRepository {
    fun getAllEpisodeProgress(): List<EpisodeWatchProgress>

    fun getDailyWatchActivity(): List<DailyWatchActivity>
}
