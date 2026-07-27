package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.download.OfflineEpisodeDownloadState
import org.akkirrai.hibiki.core.model.EpisodeProgressStatus
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.EpisodesUiState
import org.akkirrai.hibiki.shared.player.EpisodesList
import org.akkirrai.hibiki.shared.player.AppEpisodesStateContent
import org.akkirrai.hibiki.shared.player.AppEpisodesDownloadToggle
import org.akkirrai.hibiki.shared.player.resolveEpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.formatEpisodeDuration
import org.akkirrai.hibiki.shared.player.DownloadIconButton as WatchDownloadIconButton
import org.akkirrai.hibiki.shared.player.DownloadStateIcon as WatchDownloadStateIcon
import org.akkirrai.hibiki.shared.player.DownloadProgressBadge as WatchDownloadProgressBadge
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
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
    var downloadControlsVisible by remember(sourceId, downloadMode) { mutableStateOf(downloadMode) }
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
            icon = Icons.Outlined.Download,
            contentDescription = stringResource(R.string.watch_download),
            onClick = { downloadControlsVisible = !downloadControlsVisible },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = UiDimens.ScreenPadding, top = 8.dp)
                .zIndex(1f),
        )

        AppEpisodesStateContent(
            result = state.result,
            sourceTitle = sourceTitle,
            emptyMessage = stringResource(R.string.watch_episodes_empty_title),
            retryLabel = stringResource(R.string.search_retry),
            icon = Icons.Outlined.VideoLibrary,
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
    val visibleDownloadState = if (
        downloadState == OfflineEpisodeDownloadState.Failed && !showDownloadControls
    ) OfflineEpisodeDownloadState.NotDownloaded else downloadState
    org.akkirrai.hibiki.shared.player.EpisodeRow(
        headline = buildEpisodeHeadline(episode, progress, status),
        subtitle = buildEpisodeSubtitle(visibleDownloadState).takeIf(String::isNotBlank),
        inProgress = status == EpisodeProgressStatus.InProgress,
        enabled = enabled,
        showDownloadAction = showDownloadControls || downloadState == OfflineEpisodeDownloadState.Completed,
        shape = shape,
        onClick = onClick,
        downloadAction = {
            EpisodeDownloadAction(
                state = downloadState,
                controlsEnabled = showDownloadControls,
                onDownloadClick = onDownloadClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onRemoveClick = onRemoveClick,
            )
        },
    )
}

@Composable
private fun EpisodeDownloadAction(
    state: OfflineEpisodeDownloadState,
    controlsEnabled: Boolean,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    if (!controlsEnabled) {
        if (state == OfflineEpisodeDownloadState.Completed) {
            PassiveDownloadStateIcon()
        }
        return
    }

    when (state) {
        OfflineEpisodeDownloadState.NotDownloaded,
        OfflineEpisodeDownloadState.Failed -> WatchDownloadIconButton(
            icon = Icons.Outlined.Download,
            contentDescription = stringResource(R.string.watch_download),
            active = false,
            onClick = onDownloadClick,
        )
        OfflineEpisodeDownloadState.Queued -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchDownloadIconButton(
                icon = Icons.Outlined.Pause,
                contentDescription = stringResource(R.string.watch_pause),
                active = true,
                onClick = onPauseClick,
            )
            WatchDownloadIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.watch_remove_download),
                active = true,
                onClick = onRemoveClick,
            )
        }
        is OfflineEpisodeDownloadState.Downloading -> Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchDownloadProgressBadge(progress = state.progress)
            WatchDownloadIconButton(
                icon = Icons.Outlined.Pause,
                contentDescription = stringResource(R.string.watch_pause),
                active = true,
                onClick = onPauseClick,
            )
            WatchDownloadIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.watch_remove_download),
                active = true,
                onClick = onRemoveClick,
            )
        }
        OfflineEpisodeDownloadState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchDownloadIconButton(
                icon = Icons.Outlined.PlayArrow,
                contentDescription = stringResource(R.string.watch_resume),
                active = true,
                onClick = onResumeClick,
            )
            WatchDownloadIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.watch_remove_download),
                active = true,
                onClick = onRemoveClick,
            )
        }
        OfflineEpisodeDownloadState.Completed -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchDownloadStateIcon(
                icon = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.watch_downloaded),
            )
            WatchDownloadIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.watch_remove_download),
                active = true,
                onClick = onRemoveClick,
            )
        }
    }
}

@Composable
private fun PassiveDownloadStateIcon() {
    WatchDownloadStateIcon(
        icon = Icons.Outlined.Check,
        contentDescription = stringResource(R.string.watch_downloaded),
    )
}


@Composable
private fun buildEpisodeHeadline(
    episode: WatchEpisode,
    progress: EpisodeWatchProgress?,
    status: EpisodeProgressStatus,
): AnnotatedString {
    val number = if (episode.number % 1.0 == 0.0) episode.number.toInt().toString() else episode.number.toString()
    val headline = when (status) {
        EpisodeProgressStatus.Watched -> stringResource(R.string.watch_episode_headline_watched, number)
        else -> stringResource(R.string.watch_episode_headline, number)
    }
    return if (
        status == EpisodeProgressStatus.InProgress &&
        progress != null &&
        progress.durationMs > 0L
    ) {
        buildAnnotatedString {
            append(headline)
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
            ) {
                append(" • ${formatEpisodeDuration(progress.positionMs)} / ${formatEpisodeDuration(progress.durationMs)}")
            }
        }
    } else if (status == EpisodeProgressStatus.Watched) {
        buildAnnotatedString {
            append(headline)
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
            ) {
                append(" • ${stringResource(R.string.watch_status_watched)}")
            }
        }
    } else {
        AnnotatedString(headline)
    }
}

@Composable
private fun buildEpisodeSubtitle(
    downloadState: OfflineEpisodeDownloadState,
): String {
    val downloadLabel = when (downloadState) {
        OfflineEpisodeDownloadState.NotDownloaded -> ""
        OfflineEpisodeDownloadState.Queued -> stringResource(R.string.watch_status_queued)
        is OfflineEpisodeDownloadState.Downloading -> stringResource(R.string.watch_status_downloading, (downloadState.progress * 100).toInt())
        OfflineEpisodeDownloadState.Paused -> stringResource(R.string.watch_status_paused)
        OfflineEpisodeDownloadState.Completed -> stringResource(R.string.watch_downloaded)
        OfflineEpisodeDownloadState.Failed -> stringResource(R.string.watch_status_failed)
    }
    return downloadLabel
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
