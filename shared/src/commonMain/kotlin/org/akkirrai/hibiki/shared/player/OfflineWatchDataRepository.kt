package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.model.PlaybackStream

/** Optional offline watch data supplied by a platform media backend. */
interface OfflineWatchDataRepository {
    fun getOfflineSources(titleId: String): List<WatchSource>

    fun getOfflineEpisodes(sourceId: String): List<WatchEpisode>

    fun getOfflinePlayback(sourceId: String, episodeId: String): PlaybackStream? = null
}
