package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import android.view.WindowManager
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.core.discord.DiscordPlaybackPresence
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlayerOverlayStack
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerPanelOverlays
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
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.DefaultSkipSegmentCountdownSeconds
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
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
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay

/** Android platform host for the common playback controls and Media3 transport. */
@Composable
internal fun AndroidCommonPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    navigationState: AppNavigationState,
    progressRepository: PlaybackProgressRepository,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val androidContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(androidContext) { androidContext.findHibikiActivity() }
    val preferences = LocalAppPreferences.current
    val preferencesState = LocalAppPreferencesState.current
    val exoPlayer = remember(androidContext, playback.sessionKey()) {
        ExoPlayer.Builder(androidContext).build()
    }
    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(androidContext, exoPlayer).build()
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

    fun savePlaybackProgress() {
        progressCoordinator.persistCurrentPosition(transport)
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

    LaunchedEffect(exoPlayer, context.episodeId, preferencesState.autoPlayNextEpisode) {
        while (true) {
            if (resolveAutoPlayNextEpisode(
                    episodes = context.episodes,
                    currentEpisodeId = context.episodeId,
                    currentEpisodeNumber = context.episodeNumber,
                    positionMs = transport.positionMs(),
                    durationMs = transport.durationMs(),
                    autoPlayEnabled = preferencesState.autoPlayNextEpisode,
                    completionHandled = completionState.isHandled,
                ) != null
            ) {
                completionState = completionState.markHandled()
                selectAdjacentEpisode(1)
            }
            delay(500L)
        }
    }

    LaunchedEffect(exoPlayer, context.episodeId, playback.streamUrl, isPlaying) {
        while (true) {
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
            if (!isPlaying) return@LaunchedEffect
            delay(1_000L)
        }
    }

    LaunchedEffect(exoPlayer, context.episodeId) {
        while (true) {
            positionMs = transport.positionMs()
            isPlaying = exoPlayer.isPlaying
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
        }
        exoPlayer.addListener(listener)
        exoPlayer.setMediaSource(playback.toAndroidMediaSource(androidContext))
        exoPlayer.setPlaybackSpeed(preferencesState.playbackSpeed)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
                    savePlaybackProgress()
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
                    onAttached = {},
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
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                    dispatchPlayerEpisodeSelection(
                        episode = it,
                        setControlsVisible = { controlsVisible = true },
                        persistProgress = ::savePlaybackProgress,
                        onEpisodeSelected = onEpisodeSelected,
                    )
                }
            },
            nowMs = SystemClock::elapsedRealtime,
            backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
            settingsContent = { dismissPanel ->
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
                        dispatchPlayerSettingsRoot({ controlsVisible = true }, onOverlayEvent)
                    },
                    backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
                    onSelectSpeed = { speed ->
                        preferences.setPlaybackSpeed(speed)
                        exoPlayer.setPlaybackSpeed(speed)
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
    }
}

@Composable
internal fun AndroidPlayerWindowMode(active: Boolean) {
    val androidContext = LocalContext.current
    val activity = remember(androidContext) { androidContext.findHibikiActivity() }

    DisposableEffect(activity, active) {
        if (!active || activity == null) {
            onDispose {}
        } else {
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            val previousOrientation = activity.requestedOrientation
            val previousBehavior = controller.systemBarsBehavior
            val previousCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.window.attributes.layoutInDisplayCutoutMode
            } else {
                null
            }
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.window.attributes = activity.window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.requestedOrientation = previousOrientation
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && previousCutoutMode != null) {
                    activity.window.attributes = activity.window.attributes.apply {
                        layoutInDisplayCutoutMode = previousCutoutMode
                    }
                }
            }
        }
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
