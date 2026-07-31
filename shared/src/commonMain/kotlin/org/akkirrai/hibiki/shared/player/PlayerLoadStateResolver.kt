package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode

fun PlayerUiState.beginPlaybackLoad(excludedStreamUrls: Set<String>): PlayerUiState = copy(
    isLoading = true,
    errorMessage = null,
    failedStreamUrls = failedStreamUrls + excludedStreamUrls,
    recoveryAttempted = excludedStreamUrls.isNotEmpty(),
)

fun PlayerUiState.withPlaybackLoaded(
    stream: PlaybackStream,
    episodes: List<WatchEpisode>,
    episodeId: String,
    episodeNumber: Double?,
    savedSeekMs: Long?,
): PlayerUiState = copy(
    isLoading = false,
    playback = stream,
    animeTitle = stream.animeTitle.trim().takeIf(String::isNotBlank) ?: animeTitle,
    errorMessage = null,
    episodes = episodes,
    currentEpisodeId = episodeId,
    currentEpisodeNumber = episodeNumber,
    pendingSeekMs = savedSeekMs ?: pendingSeekMs,
    failedStreamUrls = failedStreamUrls - stream.streamUrl,
    recoveryAttempted = false,
    selectedQualityLabel = stream.qualityLabel ?: selectedQualityLabel,
)

fun PlayerUiState.withPlaybackError(
    message: String,
    episodes: List<WatchEpisode>,
    episodeId: String,
    episodeNumber: Double?,
): PlayerUiState = copy(
    isLoading = false,
    playback = null,
    errorMessage = message,
    episodes = episodes,
    currentEpisodeId = episodeId,
    currentEpisodeNumber = episodeNumber,
    recoveryAttempted = false,
)
