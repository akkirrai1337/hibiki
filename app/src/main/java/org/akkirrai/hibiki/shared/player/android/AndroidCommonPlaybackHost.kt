package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.SystemClock
import android.os.Build
import android.content.pm.ActivityInfo
import android.view.TextureView
import android.view.WindowManager
import androidx.media3.ui.PlayerView
import java.util.UUID
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.AppPreferencesState
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.discord.DiscordPlaybackPresence
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlayerOverlayStack
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerPanelOverlays
import org.akkirrai.hibiki.shared.player.AppPlayerChrome
import org.akkirrai.hibiki.shared.player.AppPlayerLoadingOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistButton
import org.akkirrai.hibiki.shared.player.AppPlayerTopOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerBottomOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerActionControls
import org.akkirrai.hibiki.shared.player.AppPlayerPanelOverlays
import org.akkirrai.hibiki.shared.player.dispatchAdjacentPlayerEpisodeSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerEpisodeSelection
import org.akkirrai.hibiki.shared.player.dispatchPlayerClose
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsAction
import org.akkirrai.hibiki.shared.player.dispatchPlayerPlaylistOpen
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsOpen
import org.akkirrai.hibiki.shared.player.dispatchPlayerPlaylistDismiss
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsDismiss
import org.akkirrai.hibiki.shared.player.dispatchPlayerLock
import org.akkirrai.hibiki.shared.player.dispatchPlayerUnlock
import org.akkirrai.hibiki.shared.player.dispatchPlayerSettingsDestination
import org.akkirrai.hibiki.shared.navigation.AppPlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.DefaultSkipSegmentCountdownSeconds
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.formatEpisodeDuration
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.resolveAutoPlayNextEpisode
import org.akkirrai.hibiki.shared.player.PlaybackProgressCoordinator
import org.akkirrai.hibiki.shared.player.sessionKey
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.player.shouldShowSkipSegmentPrompt
import org.akkirrai.hibiki.shared.player.textKey
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

/** Coordinates Android window restoration with the shared player route lifecycle. */
internal class AndroidPlayerWindowController {
    private var restoreAction: (() -> Unit)? = null

    // Lets the platform-agnostic system-back handler (which has no access to the ExoPlayer
    // transport or video surface) ask the currently-mounted player to save its progress and
    // resume frame before the shared navigation layer tears down the playback session --
    // otherwise a system back gesture skips that entirely, unlike the in-player back button.
    private var persistAction: (() -> Unit)? = null

    fun restore() {
        restoreAction?.invoke()
    }

    fun persist() {
        persistAction?.invoke()
    }

    internal fun setRestoreAction(action: (() -> Unit)?) {
        restoreAction = action
    }

    internal fun setPersistAction(action: (() -> Unit)?) {
        persistAction = action
    }
}

/** Android platform host for the common playback controls and Media3 transport. */
@Composable
internal fun AndroidCommonPlaybackHost(
    playback: PlaybackStream?,
    context: PlaybackContext,
    navigationState: AppNavigationState,
    playbackLoading: Boolean,
    progressRepository: PlaybackProgressRepository,
    resumeFrameRepository: ResumeFrameRepository,
    windowController: AndroidPlayerWindowController,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode, PlaybackContext) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val androidContext = LocalContext.current
    val preferences = LocalAppPreferences.current
    val preferencesState = LocalAppPreferencesState.current
    if (playback == null) {
        AndroidPlaybackLoadingChrome(
            context = context,
            navigationState = navigationState,
            playbackLoading = playbackLoading,
            preferences = preferences,
            preferencesState = preferencesState,
            onBack = onBack,
            onPlaylistClick = { dispatchPlayerPlaylistOpen(onOverlayEvent) },
            onSettingsClick = { dispatchPlayerSettingsOpen(onOverlayEvent) },
            onEpisodeSelected = { episode -> onEpisodeSelected(episode, context) },
            onSettingsAction = onSettingsAction,
            onOverlayEvent = onOverlayEvent,
        )
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(androidContext) { androidContext.findHibikiActivity() }
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    val exoPlayer = remember(androidContext, playback.sessionKey()) {
        ExoPlayer.Builder(androidContext).build()
    }
    val mediaSession = remember(exoPlayer) {
        // The id must be freshly generated per exoPlayer/session, not hoisted above this
        // remember(exoPlayer) block -- media3 rejects reusing an id still held by a session
        // that hasn't been released yet, and the old session's DisposableEffect cleanup only
        // runs after this composition, so a stable id here throws "Session ID must be unique"
        // every time exoPlayer is recreated (episode switch).
        MediaSession.Builder(androidContext, exoPlayer)
            .setId("hibiki-playback-${UUID.randomUUID()}")
            .build()
    }
    val transport = remember(exoPlayer) { AndroidMedia3PlaybackTransport(exoPlayer) }
    val progressCoordinator = remember(exoPlayer) {
        PlaybackProgressCoordinator { progress ->
            progressRepository.saveEpisodeProgress(
                context = context,
                playback = playback,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
            )
        }
    }
    var videoAspectRatio by remember { mutableFloatStateOf(DEFAULT_VIDEO_ASPECT_RATIO) }
    val playlistVisible = navigationState.overlays.lastOrNull() == AppOverlay.Playlist
    var playerLockState by remember { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerLockState()) }
    val settingsVisible = navigationState.overlays.lastOrNull() == AppOverlay.PlayerSettings
    var completionState by remember(context.episodeId) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerCompletionState()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var isPlaying by remember(exoPlayer) { mutableStateOf(true) }
    var lifecycleResumePositionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var resumePlaybackAfterLifecyclePause by remember(exoPlayer) { mutableStateOf(false) }
    var playerSkipState by remember(context.episodeId) { mutableStateOf(org.akkirrai.hibiki.shared.player.PlayerSkipState()) }
    val videoScaleMode = preferencesState.videoScaleMode
    val episodeNavigation = resolveEpisodeNavigationAvailability(
        episodes = context.episodes,
        currentEpisodeId = context.episodeId,
    )
    var isAudioOnly by remember { mutableStateOf(false) }
    var isEnteringPictureInPicture by remember { mutableStateOf(false) }
    var isPictureInPictureActive by remember { mutableStateOf(false) }
    val pictureInPictureSupported = remember(activity) {
        activity?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    fun savePlaybackProgress() {
        progressCoordinator.persistCurrentPosition(transport)
    }

    // Grabs whatever frame the TextureView is currently displaying -- must run synchronously
    // before the outgoing exoPlayer/stream is swapped out (episode switch, source/quality
    // change), not from a later onDispose, since by then the surface may already show the next
    // stream or nothing at all.
    fun captureResumeFrame() {
        val textureView = playerView?.videoSurfaceView as? TextureView ?: return
        val bitmap = runCatching { textureView.bitmap }.getOrNull() ?: return
        resumeFrameRepository.saveFrame(context.titleId, bitmap)
    }

    fun persistPlaybackState() {
        savePlaybackProgress()
        captureResumeFrame()
    }

    // The system back gesture exits through HibikiSystemBackCoordinator, which has no access to
    // this composable's transport/video surface -- it calls windowController.persist() instead,
    // which resolves here for as long as this player is on screen.
    DisposableEffect(windowController, exoPlayer) {
        windowController.setPersistAction(::persistPlaybackState)
        onDispose { windowController.setPersistAction(null) }
    }

    // Belt-and-suspenders against process death/crash losing more than a few seconds of
    // progress -- every other persist point is event-driven (pause, close, episode switch).
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(PlaybackProgressAutoSaveIntervalMillis)
            persistPlaybackState()
        }
    }

    fun closePlayback() {
        dispatchPlayerClose(
            persistProgress = ::persistPlaybackState,
            onBack = {
                windowController.restore()
                onBack()
            },
        )
    }

    fun selectAdjacentEpisode(offset: Int) {
        AppLogger.d(
            "BeakoKit/playback-request",
            "selectAdjacentEpisode: offset=$offset currentEpisodeId=${context.episodeId} episodeCount=${context.episodes.size}",
        )
        val dispatched = dispatchAdjacentPlayerEpisodeSelection(
            episodes = context.episodes,
            currentEpisodeId = context.episodeId,
            currentEpisodeNumber = context.episodeNumber,
            offset = offset,
            setControlsVisible = { controlsVisible = true },
            pausePlayback = { transport.pause() },
            persistProgress = ::persistPlaybackState,
            onEpisodeSelected = { episode -> onEpisodeSelected(episode, context) },
        )
        if (!dispatched) {
            AppLogger.w("BeakoKit/playback-request", "selectAdjacentEpisode: no adjacent episode found for offset=$offset")
        }
    }

    // Single tick drives position tracking, autoplay-next detection, and the Discord presence
    // update. DiscordRpcManager.showPlayback() already rate-limits its own publishes, so calling
    // it every tick is safe and avoids running three separate polling loops over the same state.
    LaunchedEffect(exoPlayer, context.episodeId, playback.streamUrl) {
        while (true) {
            positionMs = transport.positionMs()
            isPlaying = exoPlayer.isPlaying

            if (resolveAutoPlayNextEpisode(
                    episodes = context.episodes,
                    currentEpisodeId = context.episodeId,
                    currentEpisodeNumber = context.episodeNumber,
                    positionMs = positionMs,
                    durationMs = transport.durationMs(),
                    autoPlayEnabled = preferencesState.autoPlayNextEpisode,
                    completionHandled = completionState.isHandled,
                ) != null
            ) {
                completionState = completionState.markHandled()
                selectAdjacentEpisode(1)
            }

            DiscordRpcManager.get(androidContext).showPlayback(
                DiscordPlaybackPresence(
                    titleId = context.titleId,
                    animeTitle = playback.animeTitle,
                    voiceover = playback.sourceTitle,
                    episodeNumber = context.episodeNumber,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = transport.durationMs(),
                    isPlaying = isPlaying,
                ),
            )

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

    LaunchedEffect(activeSkipSegmentKey, preferencesState.autoSkipSegments) {
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
        if (preferencesState.autoSkipSegments && playerSkipState.hiddenSegmentKey != key) {
            transport.seekToMs(segment.endMs)
        } else if (playerSkipState.hiddenSegmentKey != key) {
            playerSkipState = playerSkipState.hide(key)
        }
    }

    DisposableEffect(androidContext, context.episodeId, episodeNavigation) {
        val receiver = registerPictureInPictureReceiver(
            context = androidContext,
            onToggleAudioOnly = {
                exoPlayer.play()
                isAudioOnly = true
                isPictureInPictureActive = false
                activity?.moveTaskToBack(true)
            },
            onTogglePlayback = {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            onPreviousEpisode = { if (episodeNavigation.hasPrevious) selectAdjacentEpisode(-1) },
            onNextEpisode = { if (episodeNavigation.hasNext) selectAdjacentEpisode(1) },
        )
        onDispose { androidContext.unregisterReceiver(receiver) }
    }

    LaunchedEffect(
        isPictureInPictureActive,
        isAudioOnly,
        isPlaying,
        context.episodeId,
        episodeNavigation,
    ) {
        if (isPictureInPictureActive) {
            activity?.setPictureInPictureParams(
                createAndroidPictureInPictureParams(
                    context = androidContext,
                    isPlaying = exoPlayer.isPlaying,
                    isAudioOnly = isAudioOnly,
                    hasPreviousEpisode = episodeNavigation.hasPrevious,
                    hasNextEpisode = episodeNavigation.hasNext,
                ),
            )
        }
    }

    LaunchedEffect(isEnteringPictureInPicture, isPictureInPictureActive, isAudioOnly) {
        val discord = DiscordRpcManager.get(androidContext)
        discord.setPictureInPictureActive(isEnteringPictureInPicture || isPictureInPictureActive)
        discord.setBackgroundAudioActive(isAudioOnly)
    }

    DisposableEffect(androidContext) {
        onDispose {
            val discord = DiscordRpcManager.get(androidContext)
            discord.setPictureInPictureActive(false)
            discord.setBackgroundAudioActive(false)
        }
    }

    DisposableEffect(exoPlayer, playback.streamUrl) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0 && videoSize.width > 0) {
                    videoAspectRatio = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio /
                        videoSize.height.toFloat()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.playWhenReady = true
        exoPlayer.setMediaSource(playback.toAndroidMediaSource(androidContext))
        if (context.pendingSeekMs > 0L) {
            exoPlayer.seekTo(context.pendingSeekMs)
        }
        exoPlayer.setPlaybackSpeed(preferencesState.playbackSpeed)
        exoPlayer.prepare()
        onDispose {
            savePlaybackProgress()
            mediaSession.release()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    persistPlaybackState()
                    if (isPictureInPictureActive || isAudioOnly) return@LifecycleEventObserver
                    lifecycleResumePositionMs = transport.positionMs().coerceAtLeast(0L)
                    resumePlaybackAfterLifecyclePause = exoPlayer.isPlaying
                    exoPlayer.pause()
                }

                Lifecycle.Event.ON_STOP -> savePlaybackProgress()

                Lifecycle.Event.ON_RESUME -> {
                    isPictureInPictureActive = false
                    isAudioOnly = false
                    val resumePositionMs = lifecycleResumePositionMs
                    if (resumePositionMs > 0L) {
                        exoPlayer.seekTo(resumePositionMs)
                        positionMs = resumePositionMs
                        lifecycleResumePositionMs = 0L
                    }
                    if (resumePlaybackAfterLifecyclePause) exoPlayer.play()
                    resumePlaybackAfterLifecyclePause = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppPlayerChrome(
            surface = {
                AndroidPlayerSurface(
                exoPlayer = exoPlayer,
                    isAudioOnly = isAudioOnly,
                    videoScaleMode = videoScaleMode,
                    videoAspectRatio = videoAspectRatio,
                    isClosing = false,
                    onAttached = { view -> playerView = view },
                )
            },
            controlsEnabled = !playerLockState.isLocked,
            controls = {
            AppPlaybackControls(
                transport = transport,
                playback = playback,
                context = context,
                scaleMode = videoScaleMode,
                onScaleClick = { preferences.setVideoScaleMode(videoScaleMode.next()) },
                scaleContentDescription = appText(videoScaleMode.textKey()),
                onBack = ::closePlayback,
                playlistEnabled = context.episodes.isNotEmpty(),
                onPlaylistClick = {
                    dispatchPlayerPlaylistOpen(onOverlayEvent)
                },
                hasPreviousEpisode = episodeNavigation.hasPrevious,
                hasNextEpisode = episodeNavigation.hasNext,
                onPreviousEpisode = {
                    selectAdjacentEpisode(-1)
                },
                onNextEpisode = {
                    selectAdjacentEpisode(1)
                },
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
                pictureInPictureEnabled = pictureInPictureSupported,
                onPictureInPictureClick = {
                    isEnteringPictureInPicture = true
                    DiscordRpcManager.get(androidContext).setPictureInPictureActive(true)
                    val entered = runCatching {
                        activity?.enterPictureInPictureMode(
                            createAndroidPictureInPictureParams(
                                context = androidContext,
                                isPlaying = exoPlayer.isPlaying,
                                isAudioOnly = isAudioOnly,
                                hasPreviousEpisode = episodeNavigation.hasPrevious,
                                hasNextEpisode = episodeNavigation.hasNext,
                            ),
                        ) ?: false
                    }.getOrDefault(false)
                    isPictureInPictureActive = entered
                    if (!entered) {
                        isEnteringPictureInPicture = false
                        DiscordRpcManager.get(androidContext).setPictureInPictureActive(false)
                    }
                },
                pictureInPictureContentDescription = appText(AppTextKey.PlayerPictureInPicture),
                onSettingsClick = {
                    dispatchPlayerSettingsOpen(onOverlayEvent)
                },
                settingsContentDescription = appText(AppTextKey.PlayerSettings),
                onControlsVisibilityChanged = { controlsVisible = it },
                playbackLoading = playbackLoading,
                topContentInset = 0.dp,
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
                    .replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissPlaylist = {
                dispatchPlayerPlaylistDismiss({ controlsVisible = true }, onOverlayEvent)
            },
            onDismissSettings = {
                dispatchPlayerSettingsDismiss({ controlsVisible = true }, onOverlayEvent)
            },
            onEpisodeClick = { episodeId ->
                AppLogger.d("BeakoKit/playback-request", "playlist onEpisodeClick: episodeId=$episodeId")
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                    dispatchPlayerEpisodeSelection(
                        episode = it,
                        setControlsVisible = { controlsVisible = true },
                        pausePlayback = { transport.pause() },
                        persistProgress = ::persistPlaybackState,
                        onEpisodeSelected = { episode -> onEpisodeSelected(episode, context) },
                    )
                }
            },
            nowMs = SystemClock::elapsedRealtime,
            backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
            settingsContent = {
                AppPlayerSettingsContent(
                    destination = navigationState.playerSettingsDestination,
                    selectedSpeed = preferencesState.playbackSpeed,
                    selectedSourceId = context.sourceId,
                    selectedPlayerName = context.selectedPlayerName,
                    selectedQualityLabel = context.selectedQualityLabel ?: playback.qualityLabel,
                    availableQualityLabels = playback.availableQualityLabels,
                    autoSkipSegments = preferencesState.autoSkipSegments,
                    autoPlayNextEpisode = preferencesState.autoPlayNextEpisode,
                    options = context.settingsOptions,
                    onNavigate = {
                        dispatchPlayerSettingsDestination(it, { controlsVisible = true }, onOverlayEvent)
                    },
                    onBack = {
                        dispatchPlayerSettingsDestination(
                            AppPlayerSettingsDestination.Root,
                            { controlsVisible = true },
                            onOverlayEvent,
                        )
                    },
                    backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
                    onSelectSpeed = { speed ->
                        preferences.setPlaybackSpeed(speed)
                        exoPlayer.setPlaybackSpeed(speed)
                    },
                    onSelectVoiceover = { source ->
                        dispatchPlayerSettingsAction(
                            action = PlaybackSettingsAction.SelectVoiceover(source),
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::persistPlaybackState,
                            onSettingsAction = onSettingsAction,
                        )
                    },
                    onSelectPlayer = { playerName ->
                        dispatchPlayerSettingsAction(
                            action = PlaybackSettingsAction.SelectPlayer(playerName),
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::persistPlaybackState,
                            onSettingsAction = onSettingsAction,
                        )
                    },
                    onSelectQuality = { qualityLabel ->
                        dispatchPlayerSettingsAction(
                            action = PlaybackSettingsAction.SelectQuality(qualityLabel),
                            setControlsVisible = { controlsVisible = true },
                            persistProgress = ::persistPlaybackState,
                            onSettingsAction = onSettingsAction,
                        )
                    },
                    onAutoSkipSegmentsChange = { enabled ->
                        onSettingsAction(PlaybackSettingsAction.SetAutoSkipSegments(enabled))
                    },
                    onAutoPlayNextEpisodeChange = { enabled ->
                        onSettingsAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(enabled))
                    },
                )
            },
            skipVisible = activeSkipSegment != null,
            controlsVisible = controlsVisible,
            skipCountdownSeconds = playerSkipState.countdownSeconds,
            autoSkipEnabled = preferencesState.autoSkipSegments,
            skipLabel = appText(AppTextKey.PlayerSkip),
            watchLabel = appText(AppTextKey.PlayerWatch),
            onSkipClick = { activeSkipSegment?.let { transport.seekToMs(it.endMs) } },
            onWatchClick = { activeSkipSegmentKey?.let { playerSkipState = playerSkipState.hide(it) } },
                skipModifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        },
        )

        BackHandler(enabled = playlistVisible || settingsVisible) {
            if (settingsVisible && navigationState.playerSettingsDestination != AppPlayerSettingsDestination.Root) {
                onOverlayEvent(AppNavigationEvent.Back)
            } else {
                onOverlayEvent(AppNavigationEvent.DismissOverlay)
            }
        }
    }
}

@Composable
private fun AndroidPlaybackLoadingChrome(
    context: PlaybackContext,
    navigationState: AppNavigationState,
    playbackLoading: Boolean,
    preferences: AppPreferences,
    preferencesState: AppPreferencesState,
    onBack: () -> Unit,
    onPlaylistClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    val playlistVisible = navigationState.overlays.lastOrNull() == AppOverlay.Playlist
    val settingsVisible = navigationState.overlays.lastOrNull() == AppOverlay.PlayerSettings
    val title = context.animeTitle.ifBlank { context.sourceTitle }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        AppPlayerLoadingOverlay(visible = playbackLoading)
        AppPlayerTopOverlay(
            title = title,
            subtitle = appText(AppTextKey.PlayerEpisodeNumber).replace(
                "%s",
                formatEpisodeNumber(context.episodeNumber),
            ),
            playlistEnabled = context.episodes.isNotEmpty(),
            backContent = {
                org.akkirrai.hibiki.shared.design.component.navigation.AppBackButton(
                    onClick = onBack,
                    contentDescription = appText(AppTextKey.Back),
                )
            },
            playlistContent = {
                AppPlayerPlaylistButton(
                    onClick = onPlaylistClick,
                    contentDescription = null,
                )
            },
        )
        AppPlayerBottomOverlay(
            positionLabel = "${formatEpisodeDuration(0L)} / ${formatEpisodeDuration(0L)}",
            durationMs = 0L,
            bufferedPositionMs = 0L,
            sliderPositionMs = 0L,
            onSliderValueChange = {},
            onSliderValueChangeFinished = {},
            timelineEnabled = false,
            controlsContent = {
                AppPlayerActionControls(
                    onScaleClick = {},
                    scaleEnabled = false,
                    onLockClick = {},
                    lockEnabled = false,
                    pictureInPictureEnabled = false,
                    onPictureInPictureClick = {},
                    onSettingsClick = onSettingsClick,
                    settingsEnabled = true,
                    settingsContentDescription = appText(AppTextKey.PlayerSettings),
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AppPlayerPanelOverlays(
            playlistVisible = playlistVisible,
            settingsVisible = settingsVisible,
            currentEpisodeId = context.episodeId,
            episodes = context.episodes,
            episodeHeadline = { episode ->
                appText(AppTextKey.PlayerEpisodeNumber)
                    .replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissPlaylist = {
                dispatchPlayerPlaylistDismiss({}, onOverlayEvent)
            },
            onDismissSettings = {
                dispatchPlayerSettingsDismiss({}, onOverlayEvent)
            },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let(onEpisodeSelected)
            },
            nowMs = SystemClock::elapsedRealtime,
            backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
            settingsContent = { onSettingsBack ->
                AppPlayerSettingsContent(
                    destination = navigationState.playerSettingsDestination,
                    selectedSpeed = preferencesState.playbackSpeed,
                    selectedSourceId = context.sourceId,
                    selectedPlayerName = context.selectedPlayerName,
                    selectedQualityLabel = context.selectedQualityLabel,
                    availableQualityLabels = emptyList(),
                    autoSkipSegments = preferencesState.autoSkipSegments,
                    autoPlayNextEpisode = preferencesState.autoPlayNextEpisode,
                    options = context.settingsOptions,
                    onNavigate = {
                        dispatchPlayerSettingsDestination(it, {}, onOverlayEvent)
                    },
                    onBack = onSettingsBack,
                    backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
                    onSelectSpeed = preferences::setPlaybackSpeed,
                    onSelectVoiceover = { onSettingsAction(PlaybackSettingsAction.SelectVoiceover(it)) },
                    onSelectPlayer = { onSettingsAction(PlaybackSettingsAction.SelectPlayer(it)) },
                    onSelectQuality = { onSettingsAction(PlaybackSettingsAction.SelectQuality(it)) },
                    onAutoSkipSegmentsChange = {
                        onSettingsAction(PlaybackSettingsAction.SetAutoSkipSegments(it))
                    },
                    onAutoPlayNextEpisodeChange = {
                        onSettingsAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(it))
                    },
                )
            },
            skipVisible = false,
            controlsVisible = true,
            skipCountdownSeconds = 0,
            autoSkipEnabled = false,
            skipLabel = "",
            watchLabel = "",
            onSkipClick = {},
            onWatchClick = {},
        )
    }
}

@Composable
internal fun AndroidPlayerWindowMode(
    active: Boolean,
    controller: AndroidPlayerWindowController,
    activity: android.app.Activity,
) {
    DisposableEffect(activity, active) {
        if (!active) {
            controller.setRestoreAction(null)
            onDispose {}
        } else {
            val windowInsetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            val previousBehavior = windowInsetsController.systemBarsBehavior
            val previousCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.window.attributes.layoutInDisplayCutoutMode
            } else {
                null
            }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            fun hideSystemBars() {
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
            fun requestLandscape() {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                hideSystemBars()
            }
            hideSystemBars()
            activity.runOnUiThread(::requestLandscape)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.window.attributes = activity.window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            var restored = false
            fun restoreWindowUi() {
                if (restored) return
                restored = true
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = previousBehavior
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && previousCutoutMode != null) {
                    activity.window.attributes = activity.window.attributes.apply {
                        layoutInDisplayCutoutMode = previousCutoutMode
                    }
                }
            }
            activity.window.decorView.post {
                if (!restored) {
                    requestLandscape()
                }
            }
            activity.window.decorView.postDelayed({
                if (!restored) {
                    requestLandscape()
                }
            }, 500L)
            controller.setRestoreAction(::restoreWindowUi)
            onDispose {
                restoreWindowUi()
                controller.setRestoreAction(null)
            }
        }
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private const val PlaybackProgressAutoSaveIntervalMillis = 15_000L
