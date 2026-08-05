package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.EpisodeDownloadState

internal fun Map<String, EpisodeDownloadState>.markEpisodeQueued(
    episodeId: String,
): Map<String, EpisodeDownloadState> = this + (episodeId to EpisodeDownloadState.Queued)

internal fun Map<String, EpisodeDownloadState>.markEpisodeRemoved(
    episodeId: String,
): Map<String, EpisodeDownloadState> = this + (episodeId to EpisodeDownloadState.NotDownloaded)
