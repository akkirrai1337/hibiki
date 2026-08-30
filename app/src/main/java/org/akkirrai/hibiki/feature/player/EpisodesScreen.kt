package org.akkirrai.hibiki.feature.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
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
import org.akkirrai.hibiki.core.reminder.EpisodeReminderScheduler
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.watchTitleIdFromSourceId
import org.akkirrai.hibiki.feature.details.isOngoingStatus
import org.akkirrai.hibiki.feature.details.rememberNextEpisodeEta

private const val WATCHED_END_TOLERANCE_MS = 1_000L
private const val EPISODES_PAGE_SIZE = 24
private const val EPISODE_ROW_ANIMATION_DURATION_MILLIS = 220

private val EpisodeRowCornerRadius = 17.dp
private val EpisodeNumberTileSize = 52.dp
private val EpisodeNumberTileCornerRadius = 15.dp
private val EpisodeWatchedBadgeSize = 18.dp
private val EpisodeWatchedBadgeIconSize = 12.dp
private val EpisodeProgressBarHeight = 3.dp
private val EpisodeResumeCardCornerRadius = 20.dp
private val EpisodeResumePlayTileSize = 46.dp
private val EpisodeResumePlayTileCornerRadius = 15.dp
private val EpisodeSectionDividerHeight = 3.dp
private val EpisodeSwipeRevealWidth = 76.dp

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
    val coroutineScope = rememberCoroutineScope()

    // Cached from whatever Details visit populated it (offlineTitleMetadataRepository is also
    // what saveToLibrary below reads from) -- this screen only otherwise knows about individual
    // episodes, not the anime's own nextEpisodeAt/status, so there's nothing to key an upcoming-
    // episode reminder off without it.
    val cachedAnime = remember(titleId) { offlineTitleMetadataRepository.get(titleId) }
    val nextEpisodeEta = rememberNextEpisodeEta(cachedAnime?.nextEpisodeAt)
        ?.takeIf { cachedAnime != null && isOngoingStatus(cachedAnime.status) }
    val episodeItems = (state.result as? EpisodesUiState.Content)?.items.orEmpty()
    val watchedCount = remember(savedProgress, episodeItems) {
        episodeItems.count { episode ->
            resolveEpisodeStatus(savedProgress.firstOrNull { it.episodeId == episode.id }) ==
                EpisodeProgressStatus.Watched
        }
    }
    val sourceSubtitle = sourceTitle.takeIf { cachedAnime != null && it.isNotBlank() }?.let { source ->
        if (episodeItems.isEmpty()) {
            source
        } else {
            stringResource(
                R.string.watch_source_progress_subtitle,
                source,
                watchedCount,
                episodeItems.size,
                stringResource(R.string.watch_status_watched),
            )
        }
    }
    val nextEpisodeNumber = remember(episodeItems) {
        (episodeItems.maxOfOrNull { it.number }?.toInt() ?: 0) + 1
    }
    var isEpisodeReminderSet by remember(titleId, nextEpisodeNumber) {
        mutableStateOf(EpisodeReminderScheduler.isScheduled(context, titleId, nextEpisodeNumber))
    }
    fun scheduleEpisodeReminder() {
        val nextEpisodeAt = cachedAnime?.nextEpisodeAt ?: return
        EpisodeReminderScheduler.schedule(context, titleId, cachedAnime.title, nextEpisodeNumber, nextEpisodeAt)
        isEpisodeReminderSet = true
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) scheduleEpisodeReminder() }
    val onEpisodeReminderClick: () -> Unit = onEpisodeReminderClick@{
        if (isEpisodeReminderSet) {
            EpisodeReminderScheduler.cancel(context, titleId, nextEpisodeNumber)
            isEpisodeReminderSet = false
            return@onEpisodeReminderClick
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleEpisodeReminder()
        }
    }

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
        title = cachedAnime?.title ?: sourceTitle,
        subtitle = sourceSubtitle,
        modifier = modifier,
    ) { contentPadding ->
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
                val resumeProgress = remember(savedProgress) { resolveResumeProgress(savedProgress) }
                val resumeEpisode = remember(resumeProgress, result.items) {
                    resumeProgress?.let { progress ->
                        result.items.firstOrNull { it.id == progress.episodeId }
                            ?: result.items.firstOrNull { it.number == progress.episodeNumber }
                    }
                }
                var visibleCount by remember(result.items.size, result.items.firstOrNull()?.id) {
                    mutableIntStateOf(EPISODES_PAGE_SIZE.coerceAtMost(result.items.size))
                }
                val visibleEpisodes = result.items.take(visibleCount)
                val hasMoreEpisodes = visibleCount < result.items.size
                val itemShape = RoundedCornerShape(EpisodeRowCornerRadius)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "episodes_header") {
                        EpisodesListHeader(
                            resumeEpisode = resumeEpisode,
                            resumeProgress = resumeProgress,
                            onResumeClick = resumeEpisode?.let { episode ->
                                {
                                    if (!navigationLocked) {
                                        navigationLockedState.value = true
                                        onEpisodeClick(episode)
                                    }
                                }
                            },
                            onResumeMarkWatched = resumeEpisode?.let { episode ->
                                resumeProgress?.let { progress ->
                                    {
                                        coroutineScope.launch {
                                            savedProgress = withContext(Dispatchers.IO) {
                                                watchStateRepository.saveEpisodeProgress(
                                                    titleId = titleId,
                                                    episodeId = episode.id,
                                                    episodeNumber = episode.number,
                                                    sourceId = progress.sourceId,
                                                    voiceoverId = progress.voiceoverId,
                                                    sourceTitle = progress.sourceTitle,
                                                    quality = progress.quality,
                                                    positionMs = progress.durationMs,
                                                    durationMs = progress.durationMs,
                                                )
                                                watchStateRepository.getEpisodeProgress(titleId)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    itemsIndexed(visibleEpisodes, key = { _, episode -> episode.id }) { _, episode ->
                        val progress = savedProgress.firstOrNull { it.episodeId == episode.id }
                        EpisodeRow(
                            episode = episode,
                            progress = progress,
                            status = resolveEpisodeStatus(progress),
                            downloadState = downloadStates[episode.id] ?: OfflineEpisodeDownloadState.NotDownloaded,
                            showDownloadControls = true,
                            enabled = !navigationLocked,
                            shape = itemShape,
                            onClick = {
                                if (navigationLocked) return@EpisodeRow
                                navigationLockedState.value = true
                                onEpisodeClick(episode)
                            },
                            onWatchStatusToggle = {
                                coroutineScope.launch {
                                    savedProgress = withContext(Dispatchers.IO) {
                                        if (resolveEpisodeStatus(progress) == EpisodeProgressStatus.Watched) {
                                            watchStateRepository.clearEpisodeProgress(titleId, episode.id)
                                        } else {
                                            val durationMs = progress?.durationMs?.takeIf { it > 0L } ?: 1L
                                            watchStateRepository.saveEpisodeProgress(
                                                titleId = titleId,
                                                episodeId = episode.id,
                                                episodeNumber = episode.number,
                                                sourceId = progress?.sourceId ?: sourceId,
                                                voiceoverId = progress?.voiceoverId ?: sourceId,
                                                sourceTitle = progress?.sourceTitle ?: sourceTitle,
                                                quality = progress?.quality,
                                                positionMs = durationMs,
                                                durationMs = durationMs,
                                            )
                                        }
                                        watchStateRepository.getEpisodeProgress(titleId)
                                    }
                                }
                            },
                            onDownloadClick = {
                                downloadStates = downloadStates + (episode.id to OfflineEpisodeDownloadState.Queued)
                                coroutineScope.launch(Dispatchers.IO) {
                                    offlineDownloadRepository.enqueueEpisodes(
                                        source = downloadSource,
                                        episodes = listOf(episode),
                                    )
                                    cachedAnime?.let { anime ->
                                        libraryRepository.saveToLibrary(anime, LibraryCategory.Saved)
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
                    if (hasMoreEpisodes) {
                        item(key = "show_more_episodes") {
                            ShowMoreEpisodesRow(
                                shape = itemShape,
                                onClick = {
                                    visibleCount = (visibleCount + EPISODES_PAGE_SIZE).coerceAtMost(result.items.size)
                                },
                            )
                        }
                    } else if (nextEpisodeEta != null) {
                        item(key = "upcoming_episode") {
                            UpcomingEpisodeRow(
                                episodeNumber = nextEpisodeNumber,
                                countdownText = nextEpisodeEta,
                                shape = itemShape,
                                isReminderSet = isEpisodeReminderSet,
                                onReminderClick = onEpisodeReminderClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesListHeader(
    resumeEpisode: WatchEpisode?,
    resumeProgress: EpisodeWatchProgress?,
    onResumeClick: (() -> Unit)?,
    onResumeMarkWatched: (() -> Unit)?,
) {
    val isVisible = resumeEpisode != null &&
        resumeProgress != null &&
        resumeProgress.durationMs > 0L &&
        onResumeClick != null &&
        onResumeMarkWatched != null
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(220)),
    ) {
        if (resumeEpisode != null && resumeProgress != null && onResumeClick != null && onResumeMarkWatched != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EpisodeResumeCard(
                title = stringResource(
                    R.string.watch_continue_episode,
                    formatEpisodeNumber(resumeEpisode.number),
                ),
                position = "${formatDuration(resumeProgress.positionMs)} / ${formatDuration(resumeProgress.durationMs)}",
                progressFraction = resumeProgress.positionMs.toFloat() / resumeProgress.durationMs.toFloat(),
                onClick = onResumeClick,
                onMarkWatched = onResumeMarkWatched,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EpisodeSectionDividerHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        }
    }
}

@Composable
private fun EpisodeResumeCard(
    title: String,
    position: String,
    progressFraction: Float,
    onClick: () -> Unit,
    onMarkWatched: () -> Unit,
) {
    val shape = RoundedCornerShape(EpisodeResumeCardCornerRadius)
    val swipeOffset = remember(title) { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val revealWidthPx = with(LocalDensity.current) { EpisodeSwipeRevealWidth.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = shape,
        ) {
            Box(
                modifier = Modifier.padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.watch_mark_watched),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Surface(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f || swipeOffset.value < 0f) {
                                change.consume()
                                swipeScope.launch {
                                    swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(-revealWidthPx, 0f))
                                }
                            }
                        },
                        onDragEnd = {
                            val shouldMarkWatched = swipeOffset.value <= -revealWidthPx * 0.55f
                            swipeScope.launch {
                                if (shouldMarkWatched) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    delay(110)
                                    onMarkWatched()
                                }
                                swipeOffset.animateTo(0f, animationSpec = tween(180))
                            }
                        },
                        onDragCancel = {
                            swipeScope.launch { swipeOffset.animateTo(0f, animationSpec = tween(180)) }
                        },
                    )
                }
                .clickable(onClick = onClick),
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(EpisodeResumePlayTileSize)
                        .clip(RoundedCornerShape(EpisodeResumePlayTileCornerRadius))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = position,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(EpisodeProgressBarHeight),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                drawStopIndicator = {},
            )
        }
    }
}
}

/**
 * Read-only row for the episode the source hasn't published yet -- laid out as a trailing list
 * row instead of a floating badge. Not clickable: there's nothing to play yet.
 */
@Composable
private fun UpcomingEpisodeRow(
    episodeNumber: Int,
    countdownText: String,
    shape: RoundedCornerShape,
    isReminderSet: Boolean,
    onReminderClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.watch_episode_headline, episodeNumber.toString()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            UpcomingEpisodeCountdownChip(countdownText)
            WatchDownloadIconButton(
                icon = if (isReminderSet) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd,
                contentDescription = stringResource(
                    if (isReminderSet) R.string.episode_reminder_scheduled else R.string.episode_reminder_schedule,
                ),
                active = isReminderSet,
                onClick = onReminderClick,
            )
        }
    }
}

// A smaller clone of Details' own NextEpisodeChip -- that one's sized for the large hero header,
// and reads as too wide/heavy squeezed into a compact list row here.
@Composable
private fun UpcomingEpisodeCountdownChip(text: String) {
    val chipColor = Color(0xFF80DF87)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(chipColor.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.hourglass),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = chipColor,
        )
        Text(
            text = text,
            color = chipColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShowMoreEpisodesRow(
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = stringResource(R.string.watch_episodes_show_more),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
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
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onWatchStatusToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    // Each toggled piece (subtitle line, download action) animates its own fade + size instead of
    // relying on animateContentSize() for the whole row: that animates the container's bounds
    // while its children pop in/out instantly, so the label visibly jumps mid-animation and the
    // two animations end up fighting each other instead of reading as one smooth resize.
    val sizeAnimationSpec = tween<androidx.compose.ui.unit.IntSize>(EPISODE_ROW_ANIMATION_DURATION_MILLIS)
    val fadeAnimationSpec = tween<Float>(EPISODE_ROW_ANIMATION_DURATION_MILLIS)
    val inProgress = status == EpisodeProgressStatus.InProgress
    val rowColor = if (inProgress) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val visibleDownloadState = if (downloadState == OfflineEpisodeDownloadState.Failed && !showDownloadControls) {
        OfflineEpisodeDownloadState.NotDownloaded
    } else {
        downloadState
    }
    val subtitle = buildEpisodeSubtitle(visibleDownloadState)
    val showDownloadAction = showDownloadControls || downloadState == OfflineEpisodeDownloadState.Completed
    val swipeOffset = remember(episode.id) { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val revealWidthPx = with(LocalDensity.current) { EpisodeSwipeRevealWidth.toPx() }
    val swipeActionMarksWatched = status != EpisodeProgressStatus.Watched

    Box(
        // clip must precede clickable -- Surface clips its own background/content to `shape`, but
        // a caller-supplied .clickable() on this outer modifier draws its ripple against the full
        // rectangular layout bounds unless it's clipped first, so the press highlight bled past
        // the row's rounded corners (visible on the first/last row of a grouped list).
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = shape,
        ) {
            Box(
                modifier = Modifier.padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (swipeActionMarksWatched) {
                        Icons.Rounded.Check
                    } else {
                        Icons.AutoMirrored.Outlined.Undo
                    },
                    contentDescription = stringResource(
                        if (swipeActionMarksWatched) R.string.watch_mark_watched else R.string.watch_unmark_watched,
                    ),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Surface(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.value.roundToInt(), 0) }
                .pointerInput(enabled, status) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f || swipeOffset.value < 0f) {
                                change.consume()
                                swipeScope.launch {
                                    swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(-revealWidthPx, 0f))
                                }
                            }
                        },
                        onDragEnd = {
                            val shouldToggle = swipeOffset.value <= -revealWidthPx * 0.55f
                            swipeScope.launch {
                                if (shouldToggle) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    delay(110)
                                    onWatchStatusToggle()
                                }
                                swipeOffset.animateTo(0f, animationSpec = tween(180))
                            }
                        },
                        onDragCancel = {
                            swipeScope.launch { swipeOffset.animateTo(0f, animationSpec = tween(180)) }
                        },
                    )
                }
                .clickable(enabled = enabled, onClick = onClick),
            color = rowColor,
            shape = shape,
        ) {
            Box {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EpisodeNumberTile(number = formatEpisodeNumber(episode.number), status = status)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (inProgress) 4.dp else 6.dp),
                ) {
                    Text(
                        text = buildEpisodeHeadline(episode, progress, status),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedVisibility(
                        visible = subtitle.isNotBlank(),
                        enter = fadeIn(fadeAnimationSpec) + expandVertically(sizeAnimationSpec),
                        exit = fadeOut(fadeAnimationSpec) + shrinkVertically(sizeAnimationSpec),
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showDownloadAction,
                    enter = fadeIn(fadeAnimationSpec) + expandIn(sizeAnimationSpec, expandFrom = Alignment.Center),
                    exit = fadeOut(fadeAnimationSpec) + shrinkOut(sizeAnimationSpec, shrinkTowards = Alignment.Center),
                ) {
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
            val progressFraction = when {
                status == EpisodeProgressStatus.Watched -> 1f
                progress != null && progress.durationMs > 0L && progress.positionMs > 0L ->
                    progress.positionMs.toFloat() / progress.durationMs.toFloat()
                else -> null
            }
            if (inProgress && progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(EpisodeProgressBarHeight),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
}

@Composable
private fun EpisodeNumberTile(
    number: String,
    status: EpisodeProgressStatus,
) {
    val active = status == EpisodeProgressStatus.InProgress
    Box(
        modifier = Modifier
            .size(EpisodeNumberTileSize)
            .clip(RoundedCornerShape(EpisodeNumberTileCornerRadius))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status == EpisodeProgressStatus.Watched) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
                    .size(EpisodeWatchedBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(EpisodeWatchedBadgeIconSize),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
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
    // Only Completed rows stay visible with controls off, swapping to this simplified
    // checkmark-only look -- anything else is hidden by the caller's AnimatedVisibility, which
    // needs this to keep rendering the *same* icon it had while collapsing away, not vanish the
    // instant controlsEnabled flips, or its shrink animation plays over empty space.
    if (!controlsEnabled) {
        if (state == OfflineEpisodeDownloadState.Completed) {
            WatchDownloadStateIcon(
                icon = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.watch_downloaded),
            )
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

private fun resolveEpisodeStatus(
    progress: EpisodeWatchProgress?,
): EpisodeProgressStatus {
    return when {
        progress == null || progress.positionMs == 0L -> EpisodeProgressStatus.NotStarted
        progress.isWatchedToEnd() -> EpisodeProgressStatus.Watched
        else -> EpisodeProgressStatus.InProgress
    }
}

/** Most recently updated episode that's been started but not finished, if any. */
private fun resolveResumeProgress(progressItems: List<EpisodeWatchProgress>): EpisodeWatchProgress? =
    progressItems
        .asSequence()
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)

@Composable
private fun buildEpisodeHeadline(
    episode: WatchEpisode,
    progress: EpisodeWatchProgress?,
    status: EpisodeProgressStatus,
): AnnotatedString {
    val number = formatEpisodeNumber(episode.number)
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
    return when (downloadState) {
        OfflineEpisodeDownloadState.NotDownloaded -> ""
        OfflineEpisodeDownloadState.Queued -> stringResource(R.string.watch_status_queued)
        is OfflineEpisodeDownloadState.Downloading -> stringResource(R.string.watch_status_downloading, (downloadState.progress * 100).toInt())
        OfflineEpisodeDownloadState.Paused -> stringResource(R.string.watch_status_paused)
        OfflineEpisodeDownloadState.Completed -> stringResource(R.string.watch_downloaded)
        OfflineEpisodeDownloadState.Failed -> stringResource(R.string.watch_status_failed)
    }
}

private fun EpisodeWatchProgress.isWatchedToEnd(): Boolean {
    return durationMs > 0L && positionMs >= (durationMs - WATCHED_END_TOLERANCE_MS).coerceAtLeast(0L)
}

private fun formatEpisodeNumber(number: Double): String {
    val text = if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
    return text.takeIf { '.' in it } ?: text.padStart(2, '0')
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
