package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlayerUnlockOverlay
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsLayer
import org.akkirrai.hibiki.shared.player.AppPlayerSkipSegmentLayer
import org.akkirrai.hibiki.shared.player.PlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.isPlaybackComplete
import org.akkirrai.hibiki.shared.player.resolveActivePlaybackSegment
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.model.WatchEpisode

/** Android platform host for the common playback controls and Media3 transport. */
@Composable
internal fun AndroidCommonPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    progressRepository: PlaybackProgressRepository,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val androidContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(androidContext) { androidContext.findHibikiActivity() }
    val preferences = LocalAppPreferences.current
    val preferencesState = LocalAppPreferencesState.current
    val exoPlayer = remember(androidContext, playback.streamUrl, playback.headers) {
        ExoPlayer.Builder(androidContext).build()
    }
    val transport = remember(exoPlayer) { AndroidMedia3PlaybackTransport(exoPlayer) }
    var videoAspectRatio by remember { mutableFloatStateOf(DEFAULT_VIDEO_ASPECT_RATIO) }
    var playlistVisible by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var unlockButtonVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsDestination by remember { mutableStateOf(PlayerSettingsDestination.Root) }
    var completionHandled by remember(context.episodeId) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var hiddenSkipSegmentKey by remember(context.episodeId) { mutableStateOf<String?>(null) }
    var skipCountdownSeconds by remember { mutableIntStateOf(SKIP_SEGMENT_COUNTDOWN_SECONDS) }
    val videoScaleMode = preferencesState.videoScaleMode
    val episodeNavigation = resolveEpisodeNavigationAvailability(
        episodes = context.episodes,
        currentEpisodeId = context.episodeId,
    )
    var isAudioOnly by remember { mutableStateOf(false) }
    var isPictureInPictureActive by remember { mutableStateOf(false) }
    val pictureInPictureSupported = remember(activity) {
        activity?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }

    fun savePlaybackProgress() {
        val position = transport.positionMs()
        if (position <= 0L) return
        progressRepository.saveEpisodeProgress(
            context = context,
            playback = playback,
            positionMs = position,
            durationMs = transport.durationMs(),
        )
    }

    fun closePlayback() {
        savePlaybackProgress()
        onBack()
    }

    fun dispatchSettingsAction(action: PlaybackSettingsAction) {
        savePlaybackProgress()
        onSettingsAction(action)
    }

    fun selectAdjacentEpisode(offset: Int) {
        savePlaybackProgress()
        resolveAdjacentEpisode(
            episodes = context.episodes,
            currentEpisodeId = context.episodeId,
            currentEpisodeNumber = context.episodeNumber,
            offset = offset,
        )?.let(onEpisodeSelected)
    }

    LaunchedEffect(exoPlayer, context.episodeId, preferencesState.autoPlayNextEpisode) {
        while (true) {
            if (!completionHandled &&
                preferencesState.autoPlayNextEpisode &&
                isPlaybackComplete(transport.positionMs(), transport.durationMs())
            ) {
                completionHandled = true
                selectAdjacentEpisode(1)
            }
            delay(500L)
        }
    }

    LaunchedEffect(exoPlayer, context.episodeId) {
        while (true) {
            positionMs = transport.positionMs()
            delay(250L)
        }
    }

    val rawActiveSkipSegment = resolveActivePlaybackSegment(playback.segments, positionMs)
        ?.takeIf { controlsVisible && !controlsLocked && !playlistVisible && !settingsVisible }
    val activeSkipSegmentKey = rawActiveSkipSegment?.let { buildSkipSegmentKey(context.episodeId, it) }
    val activeSkipSegment = rawActiveSkipSegment
        ?.takeIf { hiddenSkipSegmentKey != activeSkipSegmentKey }

    LaunchedEffect(activeSkipSegmentKey, preferencesState.autoSkipSegments) {
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
        if (preferencesState.autoSkipSegments && hiddenSkipSegmentKey != key) {
            transport.seekToMs(segment.endMs)
        } else if (hiddenSkipSegmentKey != key) {
            hiddenSkipSegmentKey = key
        }
    }

    DisposableEffect(androidContext, context.episodeId, episodeNavigation) {
        val receiver = registerPictureInPictureReceiver(
            context = androidContext,
            onToggleAudioOnly = {
                exoPlayer.play()
                isAudioOnly = true
                isPictureInPictureActive = false
                DiscordRpcManager.get(androidContext).setBackgroundAudioActive(true)
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

    LaunchedEffect(isPictureInPictureActive, context.episodeId, episodeNavigation) {
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
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) savePlaybackProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidPlayerSurface(
            exoPlayer = exoPlayer,
            isAudioOnly = false,
            videoScaleMode = videoScaleMode,
            videoAspectRatio = videoAspectRatio,
            isClosing = false,
            onAttached = {},
        )
        if (!controlsLocked) {
            AppPlaybackControls(
                transport = transport,
                playback = playback,
                context = context,
                scaleMode = videoScaleMode,
                onScaleClick = { preferences.setVideoScaleMode(videoScaleMode.next()) },
                onBack = ::closePlayback,
                playlistEnabled = context.episodes.isNotEmpty(),
                onPlaylistClick = { playlistVisible = true },
                hasPreviousEpisode = episodeNavigation.hasPrevious,
                hasNextEpisode = episodeNavigation.hasNext,
                onPreviousEpisode = {
                    selectAdjacentEpisode(-1)
                },
                onNextEpisode = {
                    selectAdjacentEpisode(1)
                },
                onLockClick = {
                    controlsLocked = true
                    unlockButtonVisible = true
                    playlistVisible = false
                },
                lockContentDescription = appText(AppTextKey.PlayerLock),
                pictureInPictureEnabled = pictureInPictureSupported,
                onPictureInPictureClick = {
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
                },
                pictureInPictureContentDescription = appText(AppTextKey.PlayerPictureInPicture),
                onSettingsClick = {
                    settingsDestination = PlayerSettingsDestination.Root
                    settingsVisible = true
                },
                settingsContentDescription = appText(AppTextKey.Settings),
                onControlsVisibilityChanged = { controlsVisible = it },
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
                    .replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissRequest = { playlistVisible = false },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let {
                    savePlaybackProgress()
                    onEpisodeSelected(it)
                }
            },
            nowMs = SystemClock::elapsedRealtime,
            backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
        )
        activeSkipSegment?.let { segment ->
            AppPlayerSkipSegmentLayer(
                visible = true,
                controlsVisible = controlsVisible,
                countdownSeconds = skipCountdownSeconds,
                maxCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS,
                autoSkipEnabled = preferencesState.autoSkipSegments,
                skipLabel = appText(AppTextKey.PlayerSkip),
                watchLabel = appText(AppTextKey.PlayerWatch),
                onSkipClick = { transport.seekToMs(segment.endMs) },
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
                nowMs = SystemClock::elapsedRealtime,
                backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
            ) { dismissPanel ->
                AppPlayerSettingsContent(
                    destination = settingsDestination,
                    selectedSpeed = preferencesState.playbackSpeed,
                    selectedSourceId = context.sourceId,
                    selectedPlayerName = context.selectedPlayerName,
                    selectedQualityLabel = context.selectedQualityLabel ?: playback.qualityLabel,
                    availableQualityLabels = playback.availableQualityLabels,
                    autoSkipSegments = preferencesState.autoSkipSegments,
                    autoPlayNextEpisode = preferencesState.autoPlayNextEpisode,
                    options = context.settingsOptions,
                    onNavigate = { settingsDestination = it },
                    onBack = { settingsDestination = PlayerSettingsDestination.Root },
                    backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
                    onSelectSpeed = { speed ->
                        preferences.setPlaybackSpeed(speed)
                        exoPlayer.setPlaybackSpeed(speed)
                    },
                    onSelectVoiceover = { source ->
                        dismissPanel()
                        settingsVisible = false
                        dispatchSettingsAction(PlaybackSettingsAction.SelectVoiceover(source))
                    },
                    onSelectPlayer = { playerName ->
                        dismissPanel()
                        settingsVisible = false
                        dispatchSettingsAction(PlaybackSettingsAction.SelectPlayer(playerName))
                    },
                    onSelectQuality = { qualityLabel ->
                        dismissPanel()
                        settingsVisible = false
                        dispatchSettingsAction(PlaybackSettingsAction.SelectQuality(qualityLabel))
                    },
                    onAutoSkipSegmentsChange = { enabled ->
                        onSettingsAction(PlaybackSettingsAction.SetAutoSkipSegments(enabled))
                    },
                    onAutoPlayNextEpisodeChange = { enabled ->
                        onSettingsAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(enabled))
                    },
                )
            }
        }
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private const val SKIP_SEGMENT_COUNTDOWN_SECONDS = 10
