package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress

/** Read boundary for locally persisted playback state used by Profile. */
interface LocalWatchStateRepository {
    fun getAllEpisodeProgress(): List<EpisodeWatchProgress>

    fun getDailyWatchActivity(): List<DailyWatchActivity>
}
