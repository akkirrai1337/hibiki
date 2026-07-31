package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource

/** Optional offline watch data supplied by a platform media backend. */
interface OfflineWatchDataRepository {
    fun getOfflineSources(titleId: String): List<WatchSource>

    fun getOfflineEpisodes(sourceId: String): List<WatchEpisode>
}
