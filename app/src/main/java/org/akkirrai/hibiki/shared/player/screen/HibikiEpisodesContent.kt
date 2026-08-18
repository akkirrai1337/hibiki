package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.AppEpisodeDownloadRowContent
import org.akkirrai.hibiki.shared.player.AppEpisodesContent
import org.akkirrai.hibiki.shared.player.EpisodeDownloadActionState
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.EpisodeDownloadState
import org.akkirrai.hibiki.shared.player.EpisodeRow
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.buildEpisodeRowHeadline
import org.akkirrai.hibiki.shared.player.resolveEpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.toEpisodeDownloadActionState
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun HibikiEpisodesContent(
    state: EpisodesScreenState,
    source: WatchSource,
    anime: Anime,
    profileData: LocalProfileData,
    playbackLoading: Boolean,
    navigationLocked: Boolean,
    downloadControlsVisible: Boolean,
    episodeDownloadRepository: EpisodeDownloadRepository?,
    episodeDownloadStates: Map<String, EpisodeDownloadState>,
    onEpisodeDownloadStatesChange: (Map<String, EpisodeDownloadState>) -> Unit,
    libraryRepository: LibraryRepository,
    onEpisodeClick: (WatchEpisode) -> Unit,
    onLibraryChanged: () -> Unit,
    onRetry: () -> Unit,
    listContentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val downloadScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        AppEpisodesContent(
            result = state.result,
            sourceTitle = source.title.ifBlank { appText(AppTextKey.WatchSourceFallback) },
            emptyMessage = appText(AppTextKey.WatchEpisodesEmptyTitle),
            retryLabel = appText(AppTextKey.SearchRetry),
            onRetry = onRetry,
            episodeContent = { episode, shape ->
                val progress = profileData.episodeProgress.firstOrNull {
                    it.titleId == anime.id && it.episodeId == episode.id
                }
                val status = resolveEpisodeProgressStatus(progress)
                val defaultHeadline = appText(AppTextKey.WatchEpisodeHeadline)
                if (episodeDownloadRepository != null) {
                    AppEpisodeDownloadRowContent(
                        episode = episode,
                        progress = progress,
                        status = status,
                        downloadState = episodeDownloadStates[episode.id]
                            ?.toEpisodeDownloadActionState()
                            ?: EpisodeDownloadState.NotDownloaded.toEpisodeDownloadActionState(),
                        showDownloadControls = downloadControlsVisible,
                        shape = shape,
                        enabled = !playbackLoading && !navigationLocked,
                        watchedHeadline = { number -> appText(AppTextKey.WatchEpisodeHeadlineWatched).replace("%s", number) },
                        defaultHeadline = { number -> defaultHeadline.replace("%s", number) },
                        watchedLabel = appText(AppTextKey.WatchStatusWatched),
                        queuedLabel = appText(AppTextKey.WatchStatusQueued),
                        downloadingLabel = { percent -> appText(AppTextKey.WatchStatusDownloading).replace("%s", percent.toString()) },
                        pausedLabel = appText(AppTextKey.WatchStatusPaused),
                        downloadedLabel = appText(AppTextKey.WatchDownloaded),
                        failedLabel = appText(AppTextKey.WatchStatusFailed),
                        downloadedContentDescription = appText(AppTextKey.WatchDownloaded),
                        downloadContentDescription = appText(AppTextKey.WatchDownload),
                        pauseContentDescription = appText(AppTextKey.WatchPause),
                        resumeContentDescription = appText(AppTextKey.WatchResume),
                        removeContentDescription = appText(AppTextKey.WatchRemoveDownload),
                        onClick = { onEpisodeClick(episode) },
                        onDownloadClick = {
                            onEpisodeDownloadStatesChange(episodeDownloadStates +
                                (episode.id to EpisodeDownloadState.Queued)
                            )
                            downloadScope.launch {
                                episodeDownloadRepository.enqueueEpisodes(source, listOf(episode))
                            }
                        },
                        onPauseClick = { episodeDownloadRepository.pauseEpisode(source.sourceId, episode.id) },
                        onResumeClick = { episodeDownloadRepository.resumeEpisode(source.sourceId, episode.id) },
                        onRemoveClick = {
                            episodeDownloadRepository.removeEpisode(source.sourceId, episode.id)
                            onEpisodeDownloadStatesChange(
                                episodeDownloadStates + (episode.id to EpisodeDownloadState.NotDownloaded),
                            )
                            // Checked across all of the title's sources, not just this one --
                            // another dub can still have completed downloads.
                            if (!episodeDownloadRepository.hasDownloadsForTitle(anime.id)) {
                                libraryRepository.removeSavedFromLibrary(anime.id)
                            }
                            onLibraryChanged()
                        },
                    )
                } else {
                    val genericEpisodeTitle = defaultHeadline.replace("%s", formatEpisodeNumber(episode.number))
                    EpisodeRow(
                        headline = buildEpisodeRowHeadline(
                            episode = episode,
                            progress = progress,
                            status = status,
                            watchedHeadline = { number -> appText(AppTextKey.WatchEpisodeHeadlineWatched).replace("%s", number) },
                            defaultHeadline = { number -> defaultHeadline.replace("%s", number) },
                            watchedLabel = appText(AppTextKey.WatchStatusWatched),
                        ),
                        // Some sources don't provide a real episode title and instead echo back
                        // something like "Episode 1", identical to the generated headline. Only
                        // show the subtitle when it actually adds information.
                        subtitle = episode.title
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && !it.equals(genericEpisodeTitle, ignoreCase = true) },
                        inProgress = status == org.akkirrai.hibiki.shared.player.model.EpisodeProgressStatus.InProgress,
                        enabled = !playbackLoading && !navigationLocked,
                        showDownloadAction = false,
                        shape = shape,
                        onClick = { onEpisodeClick(episode) },
                    )
                }
            },
            listContentPadding = listContentPadding,
            modifier = Modifier.weight(1f),
        )
    }
}
