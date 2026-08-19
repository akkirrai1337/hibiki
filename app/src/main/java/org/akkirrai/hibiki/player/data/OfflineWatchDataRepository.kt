package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.model.PlaybackStream

/** Optional offline watch data supplied by a platform media backend. */
interface OfflineWatchDataRepository {
    fun getOfflineSources(titleId: String): List<WatchSource>

    fun getOfflineEpisodes(sourceId: String): List<WatchEpisode>

    fun getOfflinePlayback(sourceId: String, episodeId: String): PlaybackStream? = null
}
