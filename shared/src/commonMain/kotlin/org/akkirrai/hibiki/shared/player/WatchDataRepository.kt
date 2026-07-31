package org.akkirrai.hibiki.shared.player

import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource

/** Platform-neutral watch data contract used by shared UI and player orchestration. */
interface WatchDataRepository : AutoCloseable {
    suspend fun loadSources(animeId: String): List<WatchSource>

    suspend fun getEpisodes(sourceId: String): List<WatchEpisode>

    suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink>

    suspend fun getPlaybackSettingsOptions(sourceId: String, episodeId: String): PlaybackSettingsOptions
    suspend fun resolvePlayback(
        sourceId: String,
        episodeId: String,
        preferredQuality: String? = null,
    ): PlaybackStream

    override fun close()
}
