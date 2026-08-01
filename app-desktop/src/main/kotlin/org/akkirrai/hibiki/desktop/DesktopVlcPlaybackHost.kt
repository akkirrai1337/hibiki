package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import org.akkirrai.hibiki.shared.navigation.isPlayerSettingsOverlayActive
import org.akkirrai.hibiki.shared.navigation.isPlaylistOverlayActive
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerOverlayStack
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.AppPlayerChrome
import org.akkirrai.hibiki.shared.player.dispatchAdjacentPlayerEpisodeSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerEpisodeSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerClose
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerPlaylistOpen
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsOpen
import org.akkirrai.hibiki.shared.player.dispatchPlayerPlaylistDismiss
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsDismiss
import org.akkirrai.hibiki.shared.player.dispatchPlayerLock
import org.akkirrai.hibiki.shared.player.dispatchPlayerUnlock
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsRoot
import org.akkirrai.hibiki.shared.player.AppPlayerPanelOverlays
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.DefaultSkipSegmentCountdownSeconds
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveAutoPlayNextEpisode
import org.akkirrai.hibiki.shared.player.PlaybackProgressCoordinator
import org.akkirrai.hibiki.shared.player.sessionKey
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.resolvePlaybackViewportScale
import org.akkirrai.hibiki.shared.player.textKey
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.player.shouldShowSkipSegmentPrompt
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
    val playlistVisible = navigationState.isPlaylistOverlayActive
    var playerLockState by remember(session) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerLockState()) }
    var completionState by remember(session) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerCompletionState()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember(session) { mutableLongStateOf(0L) }
    var playerSkipState by remember(context.episodeId) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerSkipState()) }
    val settingsVisible = navigationState.isPlayerSettingsOverlayActive
    var selectedSpeed by remember(session) { mutableFloatStateOf(settingsStore.load().playbackSpeed) }
    var autoSkipSegments by remember(session) { mutableStateOf(settingsStore.load().autoSkipSegments) }
    var autoPlayNextEpisode by remember(session) { mutableStateOf(settingsStore.load().autoPlayNextEpisode) }

    fun savePlaybackProgress() {
        progressCoordinator.persistCurrentPosition(session.transport)
    }

    fun closePlayback() {
        dispatchPlayerClose(::savePlaybackProgress, onBack)
    }

    fun selectAdjacentEpisode(offset: Int) {
        dispatchAdjacentPlayerEpisodeSelection(
            episodes = context.episodes,
            currentEpisodeId = context.episodeId,
            currentEpisodeNumber = context.episodeNumber,
            offset = offset,
            setControlsVisible = { controlsVisible = true },
            persistProgress = ::savePlaybackProgress,
            onEpisodeSelected = onEpisodeSelected,
        )
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
                completionHandled = completionState.isHandled,
            )?.let {
                completionState = completionState.markHandled()
                dispatchPlayerEpisodeSelection(
                    episode = it,
                    setControlsVisible = { controlsVisible = true },
                    persistProgress = ::savePlaybackProgress,
                    onEpisodeSelected = onEpisodeSelected,
                )
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
        ?.takeIf {
            shouldShowSkipSegmentPrompt(
                controlsVisible = controlsVisible,
                playerLocked = playerLockState.isLocked,
                playlistVisible = playlistVisible,
                settingsVisible = settingsVisible,
            )
        }
    val activeSkipSegmentKey = rawActiveSkipSegment?.let { buildSkipSegmentKey(context.episodeId, it) }
    val activeSkipSegment = rawActiveSkipSegment
        ?.takeIf { playerSkipState.hiddenSegmentKey != activeSkipSegmentKey }
    LaunchedEffect(activeSkipSegmentKey, settingsStore.load().autoSkipSegments) {
        val key = activeSkipSegmentKey ?: run {
            playerSkipState = playerSkipState.resetCountdown()
            return@LaunchedEffect
        }
        val segment = rawActiveSkipSegment
        if (playerSkipState.hiddenSegmentKey == key) return@LaunchedEffect
        playerSkipState = playerSkipState.resetCountdown()
        repeat(DefaultSkipSegmentCountdownSeconds) {
            delay(1_000L)
            if (playerSkipState.hiddenSegmentKey == key) return@LaunchedEffect
            playerSkipState = playerSkipState.tick()
        }
        if (settingsStore.load().autoSkipSegments && playerSkipState.hiddenSegmentKey != key) {
            session.transport.seekToMs(segment.endMs)
        } else if (playerSkipState.hiddenSegmentKey != key) {
            playerSkipState = playerSkipState.hide(key)
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
        Box(modifier = Modifier.fillMaxSize()) {
            AppPlayerChrome(
                surface = {
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
                },
                controlsEnabled = !playerLockState.isLocked,
                controls = {
                AppPlaybackControls(
                    transport = session.transport,
                    playback = playback,
                    context = context,
                    scaleMode = scaleMode,
                    onScaleClick = {
                        scaleMode = scaleMode.next()
                        settingsStore.save(settingsStore.load().copy(videoScaleMode = scaleMode))
                    },
                    scaleContentDescription = appText(scaleMode.textKey()),
                    onBack = ::closePlayback,
                    playlistEnabled = context.episodes.isNotEmpty(),
                    onPlaylistClick = {
                        dispatchPlayerPlaylistOpen(onOverlayEvent)
                    },
                    hasPreviousEpisode = episodeNavigation.hasPrevious,
                    hasNextEpisode = episodeNavigation.hasNext,
                onPreviousEpisode = { selectAdjacentEpisode(-1) },
                onNextEpisode = { selectAdjacentEpisode(1) },
                    onLockClick = {
                        dispatchPlayerLock(
                            setLocked = { playerLockState = playerLockState.lock() },
                            setControlsHidden = { controlsVisible = false },
                            playlistVisible = playlistVisible,
                            settingsVisible = settingsVisible,
                            onOverlayEvent = onOverlayEvent,
                        )
                    },
                    lockContentDescription = appText(AppTextKey.PlayerLock),
                    onControlsVisibilityChanged = { controlsVisible = it },
                    onSettingsClick = {
                        dispatchPlayerSettingsOpen(onOverlayEvent)
                    },
                    settingsContentDescription = appText(AppTextKey.PlayerSettings),
                )
                },
                overlayContent = {
                    AppPlayerOverlayStack(
                lockState = playerLockState,
                unlockLabel = appText(AppTextKey.PlayerUnlock),
                onUnlock = {
                    dispatchPlayerUnlock(
                        setUnlocked = { playerLockState = playerLockState.unlock() },
                        setControlsVisible = { controlsVisible = true },
                    )
                },
                unlockModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = PlayerUnlockBottomPadding),
                includeSystemBottomInset = true,
            ) {
                AppPlayerPanelOverlays(
                playlistVisible = playlistVisible,
                settingsVisible = settingsVisible,
                currentEpisodeId = context.episodeId,
                episodes = context.episodes,
                episodeHeadline = { episode ->
                    appText(AppTextKey.PlayerEpisodeNumber)
                        .replace("%s", org.akkirrai.hibiki.shared.player.formatEpisodeNumber(episode.number))
                },
                onDismissPlaylist = {
                    dispatchPlayerPlaylistDismiss({ controlsVisible = true }, onOverlayEvent)
                },
                onDismissSettings = {
                    dispatchPlayerSettingsDismiss({ controlsVisible = true }, onOverlayEvent)
                },
                onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                        dispatchPlayerEpisodeSelection(
                            episode = it,
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::savePlaybackProgress,
                            onEpisodeSelected = onEpisodeSelected,
                        )
                    }
                },
                nowMs = { System.currentTimeMillis() },
                backHandler = { enabled, callback ->
                    AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                },
                settingsContent = { dismissPanel ->
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
                            dispatchPlayerSettingsDestination(it, { controlsVisible = true }, onOverlayEvent)
                        },
                        onBack = {
                            dispatchPlayerSettingsRoot({ controlsVisible = true }, onOverlayEvent)
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
                            dispatchPlayerSettingsSelection(
                                action = PlaybackSettingsAction.SelectVoiceover(source),
                                dismissPanel = dismissPanel,
                                setControlsVisible = { controlsVisible = true },
                                persistProgress = ::savePlaybackProgress,
                                onSettingsAction = onSettingsAction,
                            )
                        },
                        onSelectPlayer = { playerName ->
                            dispatchPlayerSettingsSelection(
                                action = PlaybackSettingsAction.SelectPlayer(playerName),
                                dismissPanel = dismissPanel,
                                setControlsVisible = { controlsVisible = true },
                                persistProgress = ::savePlaybackProgress,
                                onSettingsAction = onSettingsAction,
                            )
                        },
                        onSelectQuality = { qualityLabel ->
                            dispatchPlayerSettingsSelection(
                                action = PlaybackSettingsAction.SelectQuality(qualityLabel),
                                dismissPanel = dismissPanel,
                                setControlsVisible = { controlsVisible = true },
                                persistProgress = ::savePlaybackProgress,
                                onSettingsAction = onSettingsAction,
                            )
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
                },
                skipVisible = activeSkipSegment != null,
                controlsVisible = controlsVisible,
                skipCountdownSeconds = playerSkipState.countdownSeconds,
                autoSkipEnabled = settingsStore.load().autoSkipSegments,
                skipLabel = appText(AppTextKey.PlayerSkip),
                watchLabel = appText(AppTextKey.PlayerWatch),
                onSkipClick = { activeSkipSegment?.let { session.transport.seekToMs(it.endMs) } },
                onWatchClick = { activeSkipSegmentKey?.let { playerSkipState = playerSkipState.hide(it) } },
                    skipModifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            },
            )
        }
    }
}

private const val VideoDimensionPollMillis = 500L
