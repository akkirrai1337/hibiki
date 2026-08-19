package org.akkirrai.hibiki.player

import kotlinx.coroutines.CancellationException
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource

internal data class HibikiPlaybackResolution(
    val playback: PlaybackStream,
    val episodes: List<WatchEpisode>,
    val episodeId: String,
    val episodeNumber: Double,
    val usedOfflinePlayback: Boolean,
)

internal suspend fun resolveHibikiPlayback(
    repository: WatchDataRepository,
    offlineRepository: OfflineWatchDataRepository?,
    source: WatchSource,
    requestedEpisode: WatchEpisode,
    requestEpisodes: List<WatchEpisode>,
    preferredQuality: String?,
    preferredPlayerName: String?,
    forceRefresh: Boolean,
): HibikiPlaybackResolution {
    val loadedEpisodes = if (requestEpisodes.isNotEmpty()) {
        requestEpisodes
    } else {
        offlineRepository
            ?.getOfflineEpisodes(source.sourceId)
            ?.takeIf { it.isNotEmpty() }
            ?: try {
                repository.getEpisodes(source.sourceId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                emptyList()
            }
    }
    val effectiveEpisode = resolveCurrentEpisode(
        requestedEpisodeId = requestedEpisode.id,
        requestedEpisodeNumber = requestedEpisode.number,
        episodes = loadedEpisodes,
        currentEpisodes = requestEpisodes,
    ) ?: requestedEpisode
    val offlinePlayback = offlineRepository
        ?.getOfflinePlayback(source.sourceId, effectiveEpisode.id)
    val playback = offlinePlayback ?: repository.resolvePlayback(
        sourceId = source.sourceId,
        episodeId = effectiveEpisode.id,
        preferredQuality = preferredQuality ?: source.qualityLabel,
        preferredPlayerName = preferredPlayerName,
        forceRefresh = forceRefresh,
    )
    return HibikiPlaybackResolution(
        playback = playback,
        episodes = loadedEpisodes,
        episodeId = effectiveEpisode.id,
        episodeNumber = effectiveEpisode.number,
        usedOfflinePlayback = offlinePlayback != null,
    )
}
