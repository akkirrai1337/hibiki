package org.akkirrai.hibiki.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineEpisodeDownloadState
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.EpisodeProgressStatus
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.model.formatEpisodeNumber
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.watchTitleIdFromSourceId
import org.akkirrai.hibiki.feature.player.EPISODES_PAGE_SIZE
import org.akkirrai.hibiki.feature.player.EpisodeRow
import org.akkirrai.hibiki.feature.player.EpisodeRowCornerRadius
import org.akkirrai.hibiki.feature.player.EpisodesUiState
import org.akkirrai.hibiki.feature.player.ShowMoreEpisodesRow
import org.akkirrai.hibiki.feature.player.UpcomingEpisodeRow
import org.akkirrai.hibiki.feature.player.keepsTitleSaved
import org.akkirrai.hibiki.feature.player.resolveEpisodeAutoScrollIndex
import org.akkirrai.hibiki.feature.player.resolveEpisodeStatus

/** How many placeholder rows to show while a source's episode list is loading. */
private const val SKELETON_ROWS = 3

/**
 * The episode list of a title, shown on its details page. A title reachable through several
 * sources (or a source split into seasons) gets a chip row to switch between them; a single one
 * shows the list straight away.
 *
 * Progress, downloads and the watched toggle behave exactly like the standalone episodes screen -
 * both use the same rows - so a title looks and feels the same wherever its episodes are opened.
 */
@Composable
internal fun DetailsEpisodesSection(
    anime: Anime,
    sources: List<WatchSource>,
    selectedSource: WatchSource,
    state: EpisodesUiState,
    onSelectSource: (WatchSource) -> Unit,
    onRetry: () -> Unit,
    onEpisodeClick: (WatchEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceId = selectedSource.sourceId
    val context = LocalContext.current
    val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val offlineDownloadRepository = remember(dependencies) { dependencies.offlineDownloadRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val titleId = remember(sourceId) { watchTitleIdFromSourceId(sourceId) }

    var savedProgress by remember(titleId, sourceId) {
        mutableStateOf(watchStateRepository.getEpisodeProgressForSource(titleId, sourceId))
    }
    var downloadStates by remember(sourceId) {
        mutableStateOf<Map<String, OfflineEpisodeDownloadState>>(emptyMap())
    }
    // Set only for a finished download: removing one throws a file away, cancelling one in flight does not.
    var pendingRemoval by remember(sourceId) { mutableStateOf<WatchEpisode?>(null) }
    val removeDownload: (WatchEpisode) -> Unit = { episode ->
        offlineDownloadRepository.removeEpisode(sourceId, episode.id)
        val updated = downloadStates + (episode.id to OfflineEpisodeDownloadState.NotDownloaded)
        downloadStates = updated
        if (!updated.values.any(OfflineEpisodeDownloadState::keepsTitleSaved)) {
            libraryRepository.removeSavedFromLibrary(titleId)
        }
    }

    val episodes = (state as? EpisodesUiState.Content)?.items.orEmpty()
    LaunchedEffect(state, sourceId, lifecycleOwner) {
        val content = state as? EpisodesUiState.Content ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                savedProgress = withContext(Dispatchers.IO) {
                    watchStateRepository.getEpisodeProgressForSource(titleId, sourceId)
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

    val watchedCount = remember(savedProgress, episodes) {
        episodes.count { episode ->
            resolveEpisodeStatus(savedProgress.firstOrNull { it.episodeId == episode.id }) ==
                EpisodeProgressStatus.Watched
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.watch_episodes_section_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (episodes.isNotEmpty()) {
                Text(
                    text = "$watchedCount / ${episodes.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sources.size > 1) {
            LazyRow(
                modifier = Modifier.padding(top = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sources, key = { it.sourceId }) { source ->
                    FilterChip(
                        selected = source.sourceId == sourceId,
                        onClick = { onSelectSource(source) },
                        label = {
                            Text(
                                text = source.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        val shape = RoundedCornerShape(EpisodeRowCornerRadius)
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                EpisodesUiState.Loading -> repeat(SKELETON_ROWS) { EpisodeSkeletonRow(shape) }

                EpisodesUiState.Empty -> EpisodesMessage(
                    message = stringResource(R.string.watch_episodes_empty_title),
                    onRetry = onRetry,
                )

                is EpisodesUiState.Error -> EpisodesMessage(message = state.message, onRetry = onRetry)

                is EpisodesUiState.Content -> {
                    // Open with the episode you would watch next in view, like the standalone list.
                    val startIndex = remember(state.items, savedProgress) {
                        resolveEpisodeAutoScrollIndex(state.items, savedProgress) ?: 0
                    }
                    var visibleCount by remember(sourceId, state.items.size) {
                        mutableIntStateOf(
                            (startIndex + EPISODES_PAGE_SIZE).coerceAtLeast(EPISODES_PAGE_SIZE)
                                .coerceAtMost(state.items.size),
                        )
                    }
                    val downloadSource = remember(selectedSource, state.items.size) {
                        selectedSource.copy(episodeCount = state.items.size)
                    }
                    state.items.take(visibleCount).forEach { episode ->
                        val progress = savedProgress.firstOrNull { it.episodeId == episode.id }
                        EpisodeRow(
                            episode = episode,
                            progress = progress,
                            status = resolveEpisodeStatus(progress),
                            downloadState = downloadStates[episode.id] ?: OfflineEpisodeDownloadState.NotDownloaded,
                            showDownloadControls = true,
                            enabled = true,
                            shape = shape,
                            onClick = { onEpisodeClick(episode) },
                            onWatchStatusToggle = {
                                scope.launch {
                                    savedProgress = withContext(Dispatchers.IO) {
                                        toggleWatched(watchStateRepository, titleId, sourceId, selectedSource, episode, progress)
                                        watchStateRepository.getEpisodeProgressForSource(titleId, sourceId)
                                    }
                                }
                            },
                            onDownloadClick = {
                                downloadStates = downloadStates + (episode.id to OfflineEpisodeDownloadState.Queued)
                                scope.launch(Dispatchers.IO) {
                                    offlineDownloadRepository.enqueueEpisodes(
                                        source = downloadSource,
                                        episodes = listOf(episode),
                                    )
                                    libraryRepository.saveToLibrary(anime, LibraryCategory.Saved)
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
                                if (downloadStates[episode.id] == OfflineEpisodeDownloadState.Completed) {
                                    pendingRemoval = episode
                                } else {
                                    removeDownload(episode)
                                }
                            },
                        )
                    }
                    if (visibleCount < state.items.size) {
                        ShowMoreEpisodesRow(
                            shape = shape,
                            onClick = {
                                visibleCount = (visibleCount + EPISODES_PAGE_SIZE).coerceAtMost(state.items.size)
                            },
                        )
                    } else {
                        val eta = rememberNextEpisodeEta(anime.nextEpisodeAt)
                            ?.takeIf { isOngoingStatus(anime.status) }
                        if (eta != null) {
                            UpcomingEpisodeRow(
                                episodeNumber = (state.items.maxOfOrNull { it.number }?.toInt() ?: 0) + 1,
                                countdownText = eta,
                                shape = shape,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { episode ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.watch_remove_download_confirm_title)) },
            text = {
                Text(stringResource(R.string.watch_remove_download_confirm_message, formatEpisodeNumber(episode.number)))
            },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; removeDownload(episode) }) {
                    Text(stringResource(R.string.watch_remove_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun toggleWatched(
    repository: org.akkirrai.hibiki.core.source.WatchStateRepository,
    titleId: String,
    sourceId: String,
    source: WatchSource,
    episode: WatchEpisode,
    progress: EpisodeWatchProgress?,
) {
    if (resolveEpisodeStatus(progress) == EpisodeProgressStatus.Watched) {
        repository.clearEpisodeProgress(titleId, episode.id, sourceId)
    } else {
        val durationMs = progress?.durationMs?.takeIf { it > 0L } ?: 1L
        repository.saveEpisodeProgress(
            titleId = titleId,
            episodeId = episode.id,
            episodeNumber = episode.number,
            sourceId = progress?.sourceId ?: sourceId,
            voiceoverId = progress?.voiceoverId ?: sourceId,
            sourceTitle = progress?.sourceTitle ?: source.title,
            quality = progress?.quality,
            positionMs = durationMs,
            durationMs = durationMs,
        )
    }
}

/** A row-shaped placeholder that holds the list's height while episodes load. */
@Composable
private fun EpisodeSkeletonRow(shape: RoundedCornerShape) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(68.dp).clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
    ) {}
}

@Composable
private fun EpisodesMessage(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EpisodeRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.search_retry)) }
        }
    }
}
