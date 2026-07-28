package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineEpisodeDownloadState
import org.akkirrai.hibiki.core.model.EpisodeProgressStatus
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.EpisodesUiState
import org.akkirrai.hibiki.shared.player.EpisodesList
import org.akkirrai.hibiki.shared.player.AppEpisodesStateContent
import org.akkirrai.hibiki.shared.player.AppEpisodesDownloadToggle
import org.akkirrai.hibiki.shared.player.EpisodesDownloadToggleEndPadding
import org.akkirrai.hibiki.shared.player.EpisodesDownloadToggleTopPadding
import org.akkirrai.hibiki.shared.player.rememberEpisodesDownloadControlsVisible
import org.akkirrai.hibiki.shared.player.resolveEpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.AppEpisodeDownloadAction
import org.akkirrai.hibiki.shared.player.AppEpisodeDownloadRow
import org.akkirrai.hibiki.shared.player.EpisodeDownloadActionState
import org.akkirrai.hibiki.shared.player.forDisplay
import org.akkirrai.hibiki.shared.player.shouldShowAction
import org.akkirrai.hibiki.shared.player.buildEpisodeRowHeadline
import org.akkirrai.hibiki.shared.player.resolveEpisodeDownloadSubtitle
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.watchTitleIdFromSourceId

@Composable
fun EpisodesScreen(
    sourceId: String,
    sourceTitle: String,
    downloadMode: Boolean,
    onBackClick: () -> Unit,
    onEpisodeClick: (WatchEpisode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodesViewModel = viewModel(
        factory = EpisodesViewModel.Factory(
            sourceId = sourceId,
            context = LocalContext.current,
        )
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val offlineDownloadRepository = remember(dependencies) { dependencies.offlineDownloadRepository() }
    val offlineTitleMetadataRepository = remember(dependencies) { dependencies.offlineTitleMetadataRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val titleId = remember(sourceId) { watchTitleIdFromSourceId(sourceId) }
    var savedProgress by remember(titleId) {
        mutableStateOf(watchStateRepository.getEpisodeProgress(titleId))
    }
    val navigationLockedState = rememberWatchNavigationLockState(lifecycleOwner)
    val navigationLocked = navigationLockedState.value
    var downloadStates by remember(sourceId) { mutableStateOf<Map<String, OfflineEpisodeDownloadState>>(emptyMap()) }
    var downloadControlsVisible by rememberEpisodesDownloadControlsVisible(sourceId, downloadMode)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.result, sourceId, lifecycleOwner) {
        val content = state.result as? EpisodesUiState.Content ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                savedProgress = withContext(Dispatchers.IO) {
                    watchStateRepository.migrateLegacyScopedEpisodeProgress(
                        titleId = titleId,
                        episodeIds = content.items.mapTo(mutableSetOf(), WatchEpisode::id),
                    )
                    watchStateRepository.getEpisodeProgress(titleId)
                }
                downloadStates = withContext(Dispatchers.IO) {
                    offlineDownloadRepository.getEpisodeStates(
                        sourceId = sourceId,
                        episodeIds = content.items.map { it.id },
                    )
                }
                delay(700)
            }
        }
    }

    WatchScreenScaffold(
        onBackClick = {
            if (navigationLocked) return@WatchScreenScaffold
            navigationLockedState.value = true
            onBackClick()
        },
        navigationLocked = navigationLocked,
        modifier = modifier,
    ) {
        AppEpisodesDownloadToggle(
            isVisible = downloadControlsVisible,
            contentDescription = stringResource(R.string.watch_download),
            onClick = { downloadControlsVisible = !downloadControlsVisible },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(
                    end = EpisodesDownloadToggleEndPadding,
                    top = EpisodesDownloadToggleTopPadding,
                )
                .zIndex(1f),
        )

        AppEpisodesStateContent(
            result = state.result,
            sourceTitle = sourceTitle,
            emptyMessage = stringResource(R.string.watch_episodes_empty_title),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::load,
        ) { episodes ->
                val watchSourceFallback = stringResource(R.string.watch_source_fallback)
                val downloadSource = remember(sourceId, sourceTitle, episodes.size) {
                    WatchSource(
                        sourceId = sourceId,
                        title = sourceTitle.ifBlank { watchSourceFallback },
                        episodeCount = episodes.size,
                    )
                }
                EpisodesList(
                    episodes = episodes,
                    episodeContent = { episode, shape ->
                        val progress = savedProgress.firstOrNull { it.episodeId == episode.id }
                        EpisodeRow(
                            episode = episode,
                            progress = progress,
                            status = resolveEpisodeProgressStatus(
                                progress = progress,
                            ),
                            downloadState = downloadStates[episode.id] ?: OfflineEpisodeDownloadState.NotDownloaded,
                            showDownloadControls = downloadControlsVisible,
                            shape = shape,
                            enabled = !navigationLocked,
                            onClick = {
                                if (navigationLocked) return@EpisodeRow
                                navigationLockedState.value = true
                                onEpisodeClick(episode)
                            },
                            onDownloadClick = {
                                downloadStates = downloadStates + (episode.id to OfflineEpisodeDownloadState.Queued)
                                coroutineScope.launch(Dispatchers.IO) {
                                    offlineDownloadRepository.enqueueEpisodes(
                                        source = downloadSource,
                                        episodes = listOf(episode),
                                    )
                                    offlineTitleMetadataRepository.get(titleId)?.let { cachedAnime ->
                                        libraryRepository.saveToLibrary(cachedAnime, LibraryCategory.Saved)
                                    }
                                }
                            },
                            onPauseClick = {
                                offlineDownloadRepository.pauseEpisode(sourceId, episode.id)
                                downloadStates = downloadStates + (episode.id to OfflineEpisodeDownloadState.Paused)
                            },
                            onResumeClick = {
                                offlineDownloadRepository.resumeEpisode(sourceId, episode.id)
                                downloadStates = downloadStates + (episode.id to OfflineEpisodeDownloadState.Queued)
                            },
                            onRemoveClick = {
                                offlineDownloadRepository.removeEpisode(sourceId, episode.id)
                                val updatedStates = downloadStates + (episode.id to OfflineEpisodeDownloadState.NotDownloaded)
                                downloadStates = updatedStates
                                if (!updatedStates.values.any(OfflineEpisodeDownloadState::keepsTitleSaved)) {
                                    libraryRepository.removeSavedFromLibrary(titleId)
                                }
                            },
                        )
                    },
                )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: WatchEpisode,
    progress: EpisodeWatchProgress?,
    status: EpisodeProgressStatus,
    downloadState: OfflineEpisodeDownloadState,
    showDownloadControls: Boolean,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    enabled: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val sharedDownloadState = downloadState.toEpisodeDownloadActionState()
    val visibleDownloadState = sharedDownloadState.forDisplay(showDownloadControls)
    AppEpisodeDownloadRow(
        headline = buildEpisodeRowHeadline(
            episode = episode,
            progress = progress,
            status = status,
            watchedHeadline = { number -> stringResource(R.string.watch_episode_headline_watched, number) },
            defaultHeadline = { number -> stringResource(R.string.watch_episode_headline, number) },
            watchedLabel = stringResource(R.string.watch_status_watched),
        ),
        subtitle = buildEpisodeSubtitle(visibleDownloadState).takeIf(String::isNotBlank),
        inProgress = status == EpisodeProgressStatus.InProgress,
        enabled = enabled,
        showDownloadAction = sharedDownloadState.shouldShowAction(showDownloadControls),
        shape = shape,
        onClick = onClick,
        downloadState = sharedDownloadState,
        controlsEnabled = showDownloadControls,
        downloadedContentDescription = stringResource(R.string.watch_downloaded),
        downloadContentDescription = stringResource(R.string.watch_download),
        pauseContentDescription = stringResource(R.string.watch_pause),
        resumeContentDescription = stringResource(R.string.watch_resume),
        removeContentDescription = stringResource(R.string.watch_remove_download),
        onDownloadClick = onDownloadClick,
        onPauseClick = onPauseClick,
        onResumeClick = onResumeClick,
        onRemoveClick = onRemoveClick,
    )
}

private fun OfflineEpisodeDownloadState.toEpisodeDownloadActionState(): EpisodeDownloadActionState = when (this) {
    OfflineEpisodeDownloadState.NotDownloaded -> EpisodeDownloadActionState.NotDownloaded
    OfflineEpisodeDownloadState.Queued -> EpisodeDownloadActionState.Queued
    is OfflineEpisodeDownloadState.Downloading -> EpisodeDownloadActionState.Downloading(progress)
    OfflineEpisodeDownloadState.Paused -> EpisodeDownloadActionState.Paused
    OfflineEpisodeDownloadState.Completed -> EpisodeDownloadActionState.Completed
    OfflineEpisodeDownloadState.Failed -> EpisodeDownloadActionState.Failed
}



@Composable
private fun buildEpisodeSubtitle(
    downloadState: EpisodeDownloadActionState,
): String {
    val downloadingProgress = (downloadState as? EpisodeDownloadActionState.Downloading)?.progress ?: 0f
    return resolveEpisodeDownloadSubtitle(
        state = downloadState,
        queuedLabel = stringResource(R.string.watch_status_queued),
        downloadingLabel = stringResource(
            R.string.watch_status_downloading,
            (downloadingProgress * 100).toInt(),
        ),
        pausedLabel = stringResource(R.string.watch_status_paused),
        downloadedLabel = stringResource(R.string.watch_downloaded),
        failedLabel = stringResource(R.string.watch_status_failed),
    )
}

private fun OfflineEpisodeDownloadState.keepsTitleSaved(): Boolean {
    return when (this) {
        OfflineEpisodeDownloadState.NotDownloaded,
        OfflineEpisodeDownloadState.Failed -> false
        OfflineEpisodeDownloadState.Queued,
        is OfflineEpisodeDownloadState.Downloading,
        OfflineEpisodeDownloadState.Paused,
        OfflineEpisodeDownloadState.Completed -> true
    }
}
