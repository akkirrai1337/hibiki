package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource

/** Platform-neutral download contract mirrored from the Android episode flow. */
interface EpisodeDownloadRepository {
    suspend fun enqueueEpisodes(
        source: WatchSource,
        episodes: List<WatchEpisode>,
    ): Int

    fun getEpisodeStates(
        sourceId: String,
        episodeIds: List<String>,
    ): Map<String, EpisodeDownloadState>

    fun pauseEpisode(sourceId: String, episodeId: String)

    fun resumeEpisode(sourceId: String, episodeId: String)

    fun removeEpisode(sourceId: String, episodeId: String)

    // A title can have downloads under more than one WatchSource (different dubs). Checking only
    // the currently-viewed source's local state after a removal would wrongly evict the whole
    // title from the library if another source still has completed downloads.
    fun hasDownloadsForTitle(titleId: String): Boolean
}

sealed interface EpisodeDownloadState {
    data object NotDownloaded : EpisodeDownloadState
    data object Queued : EpisodeDownloadState
    data class Downloading(val progress: Float) : EpisodeDownloadState
    data object Paused : EpisodeDownloadState
    data object Completed : EpisodeDownloadState
    data object Failed : EpisodeDownloadState
}

fun EpisodeDownloadState.toEpisodeDownloadActionState(): EpisodeDownloadActionState = when (this) {
    EpisodeDownloadState.NotDownloaded -> EpisodeDownloadActionState.NotDownloaded
    EpisodeDownloadState.Queued -> EpisodeDownloadActionState.Queued
    is EpisodeDownloadState.Downloading -> EpisodeDownloadActionState.Downloading(progress)
    EpisodeDownloadState.Paused -> EpisodeDownloadActionState.Paused
    EpisodeDownloadState.Completed -> EpisodeDownloadActionState.Completed
    EpisodeDownloadState.Failed -> EpisodeDownloadActionState.Failed
}
