package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppFilledIconButton
import org.akkirrai.hibiki.core.design.component.AppFilledIconButtonStyle
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.download.OfflineEpisodeDownloadState
import org.akkirrai.hibiki.core.model.EpisodeProgressStatus
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.watchTitleIdFromSourceId

private const val WATCHED_END_TOLERANCE_MS = 1_000L

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
        AppFilledIconButton(
            onClick = { downloadControlsVisible = !downloadControlsVisible },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = UiDimens.ScreenPadding, top = 8.dp)
                .zIndex(1f),
            style = if (downloadControlsVisible) {
                AppFilledIconButtonStyle.PrimaryContainer
            } else {
                AppFilledIconButtonStyle.Surface
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = stringResource(R.string.watch_download),
            )
        }

        when (val result = state.result) {
            EpisodesUiState.Loading -> {
                AppCenteredLoading(modifier = Modifier.fillMaxSize())
            }

            EpisodesUiState.Empty -> {
                WatchEmptyState(
                    title = sourceTitle,
                    message = stringResource(R.string.watch_episodes_empty_title),
                    icon = Icons.Outlined.VideoLibrary,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::load,
                )
            }

            is EpisodesUiState.Error -> {
                WatchEmptyState(
                    title = sourceTitle,
                    message = result.message,
                    icon = Icons.Outlined.VideoLibrary,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::load,
                )
            }

            is EpisodesUiState.Content -> {
                val watchSourceFallback = stringResource(R.string.watch_source_fallback)
                val downloadSource = remember(sourceId, sourceTitle, result.items.size) {
                    WatchSource(
                        sourceId = sourceId,
                        title = sourceTitle.ifBlank { watchSourceFallback },
                        episodeCount = result.items.size,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 68.dp, bottom = 12.dp),
                ) {
                    items(result.items, key = WatchEpisode::id) { episode ->
                        val progress = savedProgress.firstOrNull { it.episodeId == episode.id }
                        EpisodeRow(
                            episode = episode,
                            progress = progress,
                            status = resolveEpisodeStatus(
                                progress = progress,
                            ),
                            downloadState = downloadStates[episode.id] ?: OfflineEpisodeDownloadState.NotDownloaded,
                            showDownloadControls = downloadControlsVisible,
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
                    }
                }
            }
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
    enabled: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = UiDimens.ScreenPadding,
                vertical = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(
                if (status == EpisodeProgressStatus.InProgress) 4.dp else 6.dp
            )
        ) {
            Text(
                text = buildEpisodeHeadline(episode, progress, status),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val visibleDownloadState = if (
                downloadState == OfflineEpisodeDownloadState.Failed && !showDownloadControls
            ) {
                OfflineEpisodeDownloadState.NotDownloaded
            } else {
                downloadState
            }
            val subtitle = buildEpisodeSubtitle(visibleDownloadState)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDownloadControls || downloadState == OfflineEpisodeDownloadState.Completed) {
            EpisodeDownloadAction(
                state = downloadState,
                controlsEnabled = showDownloadControls,
                onDownloadClick = onDownloadClick,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onRemoveClick = onRemoveClick,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
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


private fun resolveEpisodeStatus(
    progress: EpisodeWatchProgress?,
): EpisodeProgressStatus {
    return when {
        progress == null || progress.positionMs == 0L -> EpisodeProgressStatus.NotStarted
        progress.isWatchedToEnd() -> EpisodeProgressStatus.Watched
        else -> EpisodeProgressStatus.InProgress
    }
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
                append(" • ${formatDuration(progress.positionMs)} / ${formatDuration(progress.durationMs)}")
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

private fun EpisodeWatchProgress.isWatchedToEnd(): Boolean {
    return durationMs > 0L && positionMs >= (durationMs - WATCHED_END_TOLERANCE_MS).coerceAtLeast(0L)
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
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
