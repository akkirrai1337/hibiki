package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSkipSegmentLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsLayer
import org.akkirrai.hibiki.shared.player.PlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.AppPlayerUnlockOverlay
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.resolvePlaybackViewportScale
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

/** Embedded Desktop video surface with the shared playback controls layered above it. */
@Composable
internal fun DesktopVlcPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
) {
    val session = remember(playback.streamUrl, playback.headers) {
        DesktopVlcPlaybackSession(playback)
    }
    var scaleMode by remember(session) { mutableStateOf(settingsStore.load().videoScaleMode) }
    var videoWidth by remember(session) { mutableIntStateOf(0) }
    var videoHeight by remember(session) { mutableIntStateOf(0) }
    var playlistVisible by remember(session) { mutableStateOf(false) }
    var controlsLocked by remember(session) { mutableStateOf(false) }
    var unlockButtonVisible by remember(session) { mutableStateOf(false) }
    var completionHandled by remember(session) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember(session) { mutableLongStateOf(0L) }
    var hiddenSkipSegmentKey by remember(context.episodeId) { mutableStateOf<String?>(null) }
    var skipCountdownSeconds by remember { mutableIntStateOf(SKIP_SEGMENT_COUNTDOWN_SECONDS) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsDestination by remember { mutableStateOf(PlayerSettingsDestination.Root) }
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)
    DisposableEffect(session) {
        onDispose { session.release() }
    }
    LaunchedEffect(session) {
        while (true) {
            session.videoDimensions()?.let { (width, height) ->
                videoWidth = width
                videoHeight = height
            }
            delay(VideoDimensionPollMillis)
        }
    }
    LaunchedEffect(session, context.episodeId) {
        while (true) {
            if (!completionHandled &&
                settingsStore.load().autoPlayNextEpisode &&
                org.akkirrai.hibiki.shared.player.isPlaybackComplete(
                    positionMs = session.transport.positionMs(),
                    durationMs = session.transport.durationMs(),
                )
            ) {
                completionHandled = true
                resolveAdjacentEpisode(
                    context.episodes,
                    context.episodeId,
                    context.episodeNumber,
                    1,
                )?.let(onEpisodeSelected)
            }
            delay(500L)
        }
    }
    LaunchedEffect(session, context.episodeId) {
        while (true) {
            positionMs = session.transport.positionMs()
            delay(250L)
        }
    }
    val rawActiveSkipSegment = resolveActivePlaybackSegment(playback.segments, positionMs)
        ?.takeIf { controlsVisible && !controlsLocked && !playlistVisible && !settingsVisible }
    val activeSkipSegmentKey = rawActiveSkipSegment?.let { buildSkipSegmentKey(context.episodeId, it) }
    val activeSkipSegment = rawActiveSkipSegment
        ?.takeIf { hiddenSkipSegmentKey != activeSkipSegmentKey }
    LaunchedEffect(activeSkipSegmentKey, settingsStore.load().autoSkipSegments) {
        val key = activeSkipSegmentKey ?: run {
            skipCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS
            return@LaunchedEffect
        }
        val segment = rawActiveSkipSegment
        if (hiddenSkipSegmentKey == key) return@LaunchedEffect
        skipCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS
        repeat(SKIP_SEGMENT_COUNTDOWN_SECONDS) {
            delay(1_000L)
            if (hiddenSkipSegmentKey == key) return@LaunchedEffect
            skipCountdownSeconds = (skipCountdownSeconds - 1).coerceAtLeast(0)
        }
        if (settingsStore.load().autoSkipSegments && hiddenSkipSegmentKey != key) {
            session.transport.seekToMs(segment.endMs)
        } else if (hiddenSkipSegmentKey != key) {
            hiddenSkipSegmentKey = key
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val scale = resolvePlaybackViewportScale(
            mode = scaleMode,
            sourceWidth = videoWidth,
            sourceHeight = videoHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        AppSystemBackHandler(
            enabled = playlistVisible,
            onBack = { playlistVisible = false },
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SwingPanel(
                factory = { session.component },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (scaleMode) {
                            VideoScaleMode.FIT -> Unit
                            VideoScaleMode.CROP -> {
                                scaleX = scale.scaleX
                                scaleY = scale.scaleY
                            }
                            VideoScaleMode.STRETCH -> {
                                scaleX = scale.scaleX
                                scaleY = scale.scaleY
                            }
                        }
                    },
                update = {},
            )
            if (!controlsLocked) {
                AppPlaybackControls(
                    transport = session.transport,
                    playback = playback,
                    context = context,
                    scaleMode = scaleMode,
                    onScaleClick = {
                        scaleMode = scaleMode.next()
                        settingsStore.save(settingsStore.load().copy(videoScaleMode = scaleMode))
                    },
                    onBack = onBack,
                    playlistEnabled = context.episodes.isNotEmpty(),
                    onPlaylistClick = { playlistVisible = true },
                    hasPreviousEpisode = episodeNavigation.hasPrevious,
                    hasNextEpisode = episodeNavigation.hasNext,
                    onPreviousEpisode = {
                        resolveAdjacentEpisode(
                            context.episodes,
                            context.episodeId,
                            context.episodeNumber,
                            -1,
                        )?.let(onEpisodeSelected)
                    },
                    onNextEpisode = {
                        resolveAdjacentEpisode(
                            context.episodes,
                            context.episodeId,
                            context.episodeNumber,
                            1,
                        )?.let(onEpisodeSelected)
                    },
                    onLockClick = {
                        controlsLocked = true
                        unlockButtonVisible = true
                        playlistVisible = false
                    },
                    lockContentDescription = appText(AppTextKey.PlayerLock),
                    onControlsVisibilityChanged = { controlsVisible = it },
                    onSettingsClick = {
                        settingsDestination = PlayerSettingsDestination.Root
                        settingsVisible = true
                    },
                    settingsContentDescription = appText(AppTextKey.Settings),
                )
            }
            AppPlayerUnlockOverlay(
                visible = controlsLocked && unlockButtonVisible,
                label = appText(AppTextKey.PlayerUnlock),
                onClick = {
                    controlsLocked = false
                    unlockButtonVisible = false
                },
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = PlayerUnlockBottomPadding),
            )
            AppPlayerPlaylistLayer(
                visible = playlistVisible,
                currentEpisodeId = context.episodeId,
                episodes = context.episodes,
                headline = { episode ->
                    appText(AppTextKey.PlayerEpisodeNumber)
                        .replace("%s", org.akkirrai.hibiki.shared.player.formatEpisodeNumber(episode.number))
                },
                onDismissRequest = { playlistVisible = false },
                onEpisodeClick = { episodeId ->
                    context.episodes.firstOrNull { it.id == episodeId }?.let(onEpisodeSelected)
                },
                nowMs = { System.currentTimeMillis() },
                backHandler = { enabled, callback ->
                    AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                },
            )
            activeSkipSegment?.let { segment ->
                AppPlayerSkipSegmentLayer(
                    visible = true,
                    controlsVisible = controlsVisible,
                    countdownSeconds = skipCountdownSeconds,
                    maxCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS,
                    autoSkipEnabled = settingsStore.load().autoSkipSegments,
                    skipLabel = appText(AppTextKey.PlayerSkip),
                    watchLabel = appText(AppTextKey.PlayerWatch),
                    onSkipClick = { session.transport.seekToMs(segment.endMs) },
                    onWatchClick = { activeSkipSegmentKey?.let { hiddenSkipSegmentKey = it } },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            if (settingsVisible) {
                AppPlayerSettingsLayer(
                    onDismissRequest = {
                        settingsVisible = false
                        settingsDestination = PlayerSettingsDestination.Root
                    },
                    nowMs = { System.currentTimeMillis() },
                    backHandler = { enabled, callback ->
                        AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                    },
                ) { dismissPanel ->
                    AppPlayerSettingsContent(
                        destination = settingsDestination,
                        selectedSpeed = selectedSpeed,
                        selectedSourceId = context.sourceId,
                        selectedPlayerName = context.selectedPlayerName,
                        selectedQualityLabel = context.selectedQualityLabel ?: playback.qualityLabel,
                        availableQualityLabels = playback.availableQualityLabels,
                        autoSkipSegments = autoSkipSegments,
                        autoPlayNextEpisode = autoPlayNextEpisode,
                        options = context.settingsOptions,
                        onNavigate = { settingsDestination = it },
                        onBack = { settingsDestination = PlayerSettingsDestination.Root },
                        backHandler = { enabled, callback ->
                            AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                        },
                        onSelectSpeed = { speed ->
                            selectedSpeed = speed
                            session.transport.setRate(speed)
                            settingsStore.save(settingsStore.load().copy(playbackSpeed = speed))
                        },
                        onSelectVoiceover = { source ->
                            dismissPanel()
                            settingsVisible = false
                            onSettingsAction(PlaybackSettingsAction.SelectVoiceover(source))
                        },
                        onSelectPlayer = { playerName ->
                            dismissPanel()
                            settingsVisible = false
                            onSettingsAction(PlaybackSettingsAction.SelectPlayer(playerName))
                        },
                        onSelectQuality = { qualityLabel ->
                            dismissPanel()
                            settingsVisible = false
                            onSettingsAction(PlaybackSettingsAction.SelectQuality(qualityLabel))
                        },
                        onAutoSkipSegmentsChange = { enabled ->
                            autoSkipSegments = enabled
                            onSettingsAction(PlaybackSettingsAction.SetAutoSkipSegments(enabled))
                        },
                        onAutoPlayNextEpisodeChange = { enabled ->
                            autoPlayNextEpisode = enabled
                            onSettingsAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(enabled))
                        },
                    )
                }
            }
        }
        }
    }
}

private const val VideoDimensionPollMillis = 500L
private const val SKIP_SEGMENT_COUNTDOWN_SECONDS = 10
