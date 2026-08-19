package org.akkirrai.hibiki.player

import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource

/** Platform-neutral watch data contract used by shared UI and player orchestration. */
interface WatchDataRepository : AutoCloseable {
    suspend fun loadSources(animeId: String): List<WatchSource>

    /** Refreshes remote sources when the user explicitly retries the flow. */
    suspend fun refreshSources(animeId: String): List<WatchSource> = loadSources(animeId)

    suspend fun getEpisodes(sourceId: String): List<WatchEpisode>

    suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink>

    suspend fun getPlaybackSettingsOptions(sourceId: String, episodeId: String): PlaybackSettingsOptions
    suspend fun resolvePlayback(
        sourceId: String,
        episodeId: String,
        preferredQuality: String? = null,
        preferredPlayerName: String? = null,
        forceRefresh: Boolean = false,
    ): PlaybackStream

    override fun close()
}
