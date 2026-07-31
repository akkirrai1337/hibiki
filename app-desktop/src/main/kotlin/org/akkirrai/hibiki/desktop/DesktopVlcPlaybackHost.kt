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
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
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
import org.akkirrai.hibiki.shared.player.resolveAutoPlayNextEpisode
import org.akkirrai.hibiki.shared.player.resolvePersistablePlaybackProgress
import org.akkirrai.hibiki.shared.player.PlaybackProgressCoordinator
import org.akkirrai.hibiki.shared.player.sessionKey
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
    navigationState: AppNavigationState,
    settingsStore: AppSettingsStore,
    progressRepository: PlaybackProgressRepository,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    val session = remember(playback.sessionKey()) {
        DesktopVlcPlaybackSession(playback).also {
            it.transport.setRate(settingsStore.load().playbackSpeed)
        }
    }
    val progressCoordinator = remember(session) {
        PlaybackProgressCoordinator { progress ->
            progressRepository.saveEpisodeProgress(
                context = context,
                playback = playback,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
            )
        }
    }
    var scaleMode by remember(session) { mutableStateOf(settingsStore.load().videoScaleMode) }
    var videoWidth by remember(session) { mutableIntStateOf(0) }
    var videoHeight by remember(session) { mutableIntStateOf(0) }
    val playlistVisible = navigationState.overlays.lastOrNull() == AppOverlay.Playlist
    var controlsLocked by remember(session) { mutableStateOf(false) }
    var unlockButtonVisible by remember(session) { mutableStateOf(false) }
    var completionHandled by remember(session) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember(session) { mutableLongStateOf(0L) }
    var hiddenSkipSegmentKey by remember(context.episodeId) { mutableStateOf<String?>(null) }
    var skipCountdownSeconds by remember { mutableIntStateOf(SKIP_SEGMENT_COUNTDOWN_SECONDS) }
    val settingsVisible = navigationState.overlays.lastOrNull() == AppOverlay.PlayerSettings
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }

    fun savePlaybackProgress() {
        val position = session.transport.positionMs()
        resolvePersistablePlaybackProgress(position, session.transport.durationMs())?.let { progress ->
            progressCoordinator.persistIfChanged(progress)
        }
    }

    fun closePlayback() {
        savePlaybackProgress()
        onBack()
    }

    fun selectAdjacentEpisode(offset: Int) {
        savePlaybackProgress()
        resolveAdjacentEpisode(
            context.episodes,
            context.episodeId,
            context.episodeNumber,
            offset,
        )?.let(onEpisodeSelected)
    }

    fun dispatchSettingsAction(action: PlaybackSettingsAction) {
        savePlaybackProgress()
        onSettingsAction(action)
    }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)
    DisposableEffect(session) {
        onDispose {
            savePlaybackProgress()
            session.release()
        }
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
            resolveAutoPlayNextEpisode(
                episodes = context.episodes,
                currentEpisodeId = context.episodeId,
                currentEpisodeNumber = context.episodeNumber,
                positionMs = session.transport.positionMs(),
                durationMs = session.transport.durationMs(),
                autoPlayEnabled = settingsStore.load().autoPlayNextEpisode,
                completionHandled = completionHandled,
            )?.let {
                completionHandled = true
                savePlaybackProgress()
                onEpisodeSelected(it)
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
            onBack = {
                onOverlayEvent(AppNavigationEvent.DismissOverlay)
            },
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
                    onBack = ::closePlayback,
                    playlistEnabled = context.episodes.isNotEmpty(),
                    onPlaylistClick = {
                        onOverlayEvent(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
                    },
                    hasPreviousEpisode = episodeNavigation.hasPrevious,
                    hasNextEpisode = episodeNavigation.hasNext,
                onPreviousEpisode = { selectAdjacentEpisode(-1) },
                onNextEpisode = { selectAdjacentEpisode(1) },
                    onLockClick = {
                        controlsLocked = true
                        unlockButtonVisible = true
                        if (playlistVisible) {
                            onOverlayEvent(AppNavigationEvent.DismissOverlay)
                        }
                    },
                    lockContentDescription = appText(AppTextKey.PlayerLock),
                    onControlsVisibilityChanged = { controlsVisible = it },
                    onSettingsClick = {
                        onOverlayEvent(AppNavigationEvent.OpenPlayerSettings)
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
                onDismissRequest = {
                    onOverlayEvent(AppNavigationEvent.DismissOverlay)
                    controlsVisible = true
                },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                        savePlaybackProgress()
                        onEpisodeSelected(it)
                    }
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
                        onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
                        controlsVisible = true
                    },
                    nowMs = { System.currentTimeMillis() },
                    backHandler = { enabled, callback ->
                        AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                    },
                ) { dismissPanel ->
                    AppPlayerSettingsContent(
                        destination = navigationState.playerSettingsDestination,
                        selectedSpeed = selectedSpeed,
                        selectedSourceId = context.sourceId,
                        selectedPlayerName = context.selectedPlayerName,
                        selectedQualityLabel = context.selectedQualityLabel ?: playback.qualityLabel,
                        availableQualityLabels = playback.availableQualityLabels,
                        autoSkipSegments = autoSkipSegments,
                        autoPlayNextEpisode = autoPlayNextEpisode,
                        options = context.settingsOptions,
                        onNavigate = {
                            controlsVisible = true
                            onOverlayEvent(AppNavigationEvent.SetPlayerSettingsDestination(it))
                        },
                        onBack = {
                            controlsVisible = true
                            onOverlayEvent(AppNavigationEvent.SetPlayerSettingsDestination(PlayerSettingsDestination.Root))
                        },
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
                            dispatchSettingsAction(PlaybackSettingsAction.SelectVoiceover(source))
                        },
                        onSelectPlayer = { playerName ->
                            dismissPanel()
                            dispatchSettingsAction(PlaybackSettingsAction.SelectPlayer(playerName))
                        },
                        onSelectQuality = { qualityLabel ->
                            dismissPanel()
                            dispatchSettingsAction(PlaybackSettingsAction.SelectQuality(qualityLabel))
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
