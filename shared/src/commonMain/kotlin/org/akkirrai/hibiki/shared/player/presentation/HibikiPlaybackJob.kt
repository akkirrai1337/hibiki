package org.akkirrai.hibiki.shared.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackSelection
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import kotlinx.coroutines.CancellationException

internal fun launchHibikiPlaybackJob(
    scope: CoroutineScope,
    repository: WatchDataRepository,
    offlineWatchDataRepository: OfflineWatchDataRepository?,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository?,
    progressRepository: PlaybackProgressRepository?,
    source: WatchSource,
    episode: WatchEpisode,
    requestEpisodes: List<WatchEpisode>,
    preferredQuality: String?,
    preferredPlayerName: String?,
    forceRefresh: Boolean,
    requestGeneration: Int,
    currentRequestGeneration: () -> Int,
    titleId: String,
    playerPresenter: PlayerPresenter,
    requestContext: PlaybackContext,
    onNavigateToPlayer: (String, String, Double) -> Unit,
    onPendingPlaybackContextChanged: (PlaybackContext?) -> Unit,
    onPlaybackSelectionChanged: (PlaybackSelection) -> Unit,
    onPlaybackReady: (PlaybackStream, PlaybackContext) -> Unit,
    onPlaybackPublished: ((PlaybackStream, PlaybackContext) -> Unit)?,
    onFinished: () -> Unit,
): Job = scope.launch {
    val result = try {
        Result.success(
            resolveHibikiPlayback(
                repository = repository,
                offlineRepository = offlineWatchDataRepository,
                source = source,
                requestedEpisode = episode,
                requestEpisodes = requestEpisodes,
                preferredQuality = preferredQuality,
                preferredPlayerName = preferredPlayerName,
                forceRefresh = forceRefresh,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
    if (requestGeneration != currentRequestGeneration()) return@launch
    result.onSuccess { resolution ->
        val loadedEpisodes = resolution.episodes
        val effectiveEpisodeId = resolution.episodeId
        val effectiveEpisodeNumber = resolution.episodeNumber
        val playback = if (resolution.usedOfflinePlayback) {
            offlineTitleMetadataRepository
                ?.get(titleId)
                ?.title
                ?.takeIf(String::isNotBlank)
                ?.let { title -> resolution.playback.copy(animeTitle = title) }
                ?: resolution.playback
        } else {
            resolution.playback
        }
        playerPresenter.update {
            it.withPlaybackLoaded(
                stream = playback,
                episodes = loadedEpisodes,
                episodeId = effectiveEpisodeId,
                episodeNumber = effectiveEpisodeNumber,
                savedSeekMs = progressRepository
                    ?.getPlaybackProgress(titleId, effectiveEpisodeId)
                    ?.let { progress ->
                        resolveResumablePlaybackPosition(progress.positionMs, progress.durationMs)
                    },
            )
        }
        onNavigateToPlayer(source.sourceId, effectiveEpisodeId, effectiveEpisodeNumber)
        val context = requestContext.copy(
            episodeId = effectiveEpisodeId,
            episodeNumber = effectiveEpisodeNumber,
            episodes = loadedEpisodes,
        )
        onPendingPlaybackContextChanged(null)
        onPlaybackSelectionChanged(
            PlaybackSelection(
                titleId = context.titleId,
                sourceId = source.sourceId,
                sourceTitle = source.title,
                quality = playback.qualityLabel ?: context.selectedQualityLabel,
                playerName = context.selectedPlayerName,
            ),
        )
        onPlaybackPublished?.invoke(playback, context) ?: onPlaybackReady(playback, context)
    }.onFailure { error ->
        playerPresenter.update {
            it.withPlaybackError(
                message = error.message ?: "Unable to resolve playback",
                episodes = requestEpisodes,
                episodeId = episode.id,
                episodeNumber = episode.number,
            )
        }
        println("Hibiki playback resolution failed: ${error.message ?: error::class.simpleName}")
    }
    onFinished()
}
