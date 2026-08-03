package org.akkirrai.hibiki.shared.player

import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource

/** Routes watch operations by title source while keeping the legacy repository available. */
class RoutingWatchDataRepository(
    private val builtIn: WatchDataRepository,
    private val external: WatchDataRepository,
    private val isExternalSource: (SourceId) -> Boolean,
) : WatchDataRepository {
    override suspend fun loadSources(animeId: String): List<WatchSource> =
        delegateForTitle(animeId).loadSources(animeId)

    override suspend fun refreshSources(animeId: String): List<WatchSource> =
        delegateForTitle(animeId).refreshSources(animeId)

    override suspend fun getEpisodes(sourceId: String): List<WatchEpisode> =
        delegateForWatchSource(sourceId).getEpisodes(sourceId)

    override suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink> =
        delegateForWatchSource(sourceId).getPlayerLinks(sourceId, episodeId)

    override suspend fun getPlaybackSettingsOptions(
        sourceId: String,
        episodeId: String,
    ): PlaybackSettingsOptions = delegateForWatchSource(sourceId)
        .getPlaybackSettingsOptions(sourceId, episodeId)

    override suspend fun resolvePlayback(
        sourceId: String,
        episodeId: String,
        preferredQuality: String?,
        preferredPlayerName: String?,
        forceRefresh: Boolean,
    ): PlaybackStream = delegateForWatchSource(sourceId).resolvePlayback(
        sourceId = sourceId,
        episodeId = episodeId,
        preferredQuality = preferredQuality,
        preferredPlayerName = preferredPlayerName,
        forceRefresh = forceRefresh,
    )

    override fun close() = Unit

    private fun delegateForTitle(animeId: String): WatchDataRepository =
        delegateForSource(AnimeKey.parse(animeId)?.sourceId)

    private fun delegateForWatchSource(sourceId: String): WatchDataRepository =
        delegateForSource(AnimeKey.parse(watchTitleIdFromSourceId(sourceId))?.sourceId)

    private fun delegateForSource(sourceId: SourceId?): WatchDataRepository =
        sourceId?.takeIf(isExternalSource)?.let { external } ?: builtIn
}
