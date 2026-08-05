package org.akkirrai.hibiki.feature.player

import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.download.OfflineEpisodeDownloadState
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.EpisodeDownloadState
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository

/** Android adapter that preserves the existing Media3 download queue semantics. */
internal class AndroidEpisodeDownloadRepository(
    private val delegate: OfflineDownloadRepository,
) : EpisodeDownloadRepository, OfflineWatchDataRepository {
    override suspend fun enqueueEpisodes(
        source: WatchSource,
        episodes: List<WatchEpisode>,
    ): Int = delegate.enqueueEpisodes(source, episodes)

    override fun getEpisodeStates(
        sourceId: String,
        episodeIds: List<String>,
    ): Map<String, EpisodeDownloadState> = delegate
        .getEpisodeStates(sourceId, episodeIds)
        .mapValues { (_, state) -> state.toSharedState() }

    override fun pauseEpisode(sourceId: String, episodeId: String) {
        delegate.pauseEpisode(sourceId, episodeId)
    }

    override fun resumeEpisode(sourceId: String, episodeId: String) {
        delegate.resumeEpisode(sourceId, episodeId)
    }

    override fun removeEpisode(sourceId: String, episodeId: String) {
        delegate.removeEpisode(sourceId, episodeId)
    }

    override fun getOfflineSources(titleId: String): List<WatchSource> = delegate.getOfflineSources(titleId)

    override fun getOfflineEpisodes(sourceId: String): List<WatchEpisode> = delegate.getOfflineEpisodes(sourceId)

    override fun getOfflinePlayback(sourceId: String, episodeId: String): PlaybackStream? =
        delegate.getOfflinePlayback(sourceId, episodeId)
}

private fun OfflineEpisodeDownloadState.toSharedState(): EpisodeDownloadState = when (this) {
    OfflineEpisodeDownloadState.NotDownloaded -> EpisodeDownloadState.NotDownloaded
    OfflineEpisodeDownloadState.Queued -> EpisodeDownloadState.Queued
    is OfflineEpisodeDownloadState.Downloading -> EpisodeDownloadState.Downloading(progress)
    OfflineEpisodeDownloadState.Paused -> EpisodeDownloadState.Paused
    OfflineEpisodeDownloadState.Completed -> EpisodeDownloadState.Completed
    OfflineEpisodeDownloadState.Failed -> EpisodeDownloadState.Failed
}
