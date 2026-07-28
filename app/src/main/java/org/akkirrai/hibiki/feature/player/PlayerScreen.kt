package org.akkirrai.hibiki.feature.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.shared.design.component.AppBackButton as SharedBackButton
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.URI
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.download.OfflineMediaCache
import org.akkirrai.hibiki.core.discord.DiscordPlaybackPresence
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.player.localizationKey
import org.akkirrai.hibiki.shared.player.playerToggleValueLocalizationKey
import org.akkirrai.hibiki.shared.player.pictureInPictureAudioModeLocalizationKey
import org.akkirrai.hibiki.shared.player.pictureInPicturePlaybackLocalizationKey
import org.akkirrai.hibiki.shared.player.formatHeaderNames
import org.akkirrai.hibiki.shared.player.formatShortUrl
import org.akkirrai.hibiki.shared.player.resolveVideoScaleFactors
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.PlaybackSegment
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackStreamType
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.player.PlayerUiState
import org.akkirrai.hibiki.shared.player.PlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.localizationKey
import org.akkirrai.hibiki.shared.player.formatEpisodeDuration
import org.akkirrai.hibiki.shared.player.buildSkipSegmentKey
import org.akkirrai.hibiki.shared.player.formatSeekDeltaLabel
import org.akkirrai.hibiki.shared.player.formatPlaybackSpeed
import org.akkirrai.hibiki.shared.player.playbackSpeedOptions
import org.akkirrai.hibiki.shared.player.sortQualityLabels
import org.akkirrai.hibiki.shared.player.uniquePlayerNames
import org.akkirrai.hibiki.shared.player.resolvePlayerEpisodeSubtitle
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.resolveCurrentPlaybackPosition
import org.akkirrai.hibiki.shared.player.resolveEpisodeNumberTitle
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsEntry
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsChoice
import org.akkirrai.hibiki.shared.player.AppPlayerSpeedOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerSeekOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerActionControls
import org.akkirrai.hibiki.shared.player.AppPlayerLoadingOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerErrorOverlay
import org.akkirrai.hibiki.shared.player.appPlayerSettingsItems
import org.akkirrai.hibiki.shared.player.PlayerSettingsValue
import org.akkirrai.hibiki.shared.player.buildPlayerSettingsRootEntries
import org.akkirrai.hibiki.shared.player.PlayerSettingsEntry
import org.akkirrai.hibiki.shared.player.AppPlayerUnlockOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerCenterControls
import org.akkirrai.hibiki.shared.player.AppPlayerBottomOverlay
import org.akkirrai.hibiki.shared.player.PlayerSettingsPanelMaxWidth
import org.akkirrai.hibiki.shared.player.PlayerSettingsPanelRestingOffsetY
import org.akkirrai.hibiki.shared.player.PlayerPlaylistPanelMaxWidth
import org.akkirrai.hibiki.shared.player.PlayerSettingsPanelWidthFraction
import org.akkirrai.hibiki.shared.player.PlayerPlaylistPanelWidthFraction
import org.akkirrai.hibiki.shared.player.AppPlayerTopOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerSkipSegmentOverlay
import org.akkirrai.hibiki.shared.player.PlayerSkipSegmentEndPadding
import org.akkirrai.hibiki.shared.player.PlayerSkipSegmentBottomPadding
import org.akkirrai.hibiki.shared.player.PlayerSkipSegmentControlsBottomPadding
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.AppPlayerOverlayPanel
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistButton
import org.akkirrai.hibiki.shared.player.AppPlayerControlsOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsSheet
import org.akkirrai.hibiki.shared.player.AppPlaylistBottomSheet
import org.akkirrai.hibiki.shared.player.AppAutoHideVisibilityEffect

@Composable
fun PlayerScreen(
    sourceId: String,
    episodeId: String,
    episodeNumberHint: Double? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.Factory(
            sourceId = sourceId,
            episodeId = episodeId,
            initialEpisodeNumber = episodeNumberHint,
            appContext = LocalContext.current,
        )
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val appPreferences = LocalAppPreferences.current
    val preferencesState = LocalAppPreferencesState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val activity = remember(context, view) {
        context.findActivity() ?: view.context.findActivity()
    }
    val pictureInPictureSupported = activity?.packageManager
        ?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var unlockButtonVisible by remember { mutableStateOf(false) }
    var playlistVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsDestination by remember { mutableStateOf(PlayerSettingsDestination.Root) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var unlockButtonInteractionTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(preferencesState.playbackSpeed) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var sliderPositionMs by remember { mutableLongStateOf(0L) }
    var pendingSeekMs by remember { mutableLongStateOf(0L) }
    var lifecycleResumePositionMs by remember { mutableLongStateOf(0L) }
    var resumePlaybackAfterLifecyclePause by remember { mutableStateOf(false) }
    var isEnteringPictureInPicture by remember { mutableStateOf(false) }
    var isPictureInPictureActive by remember { mutableStateOf(false) }
    var isAudioOnly by remember { mutableStateOf(false) }
    val videoScaleMode = preferencesState.videoScaleMode
    var videoAspectRatio by remember { mutableFloatStateOf(DEFAULT_VIDEO_ASPECT_RATIO) }
    var isSeeking by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var attachedPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var restoreWindowUi by remember { mutableStateOf<(() -> Unit)?>(null) }
    val autoSkipSegments = preferencesState.autoSkipSegments
    val autoPlayNextEpisode = preferencesState.autoPlayNextEpisode
    LaunchedEffect(preferencesState.playbackSpeed) {
        playbackSpeed = preferencesState.playbackSpeed
    }
    var handledEndedEpisodeId by remember { mutableStateOf<String?>(null) }
    var skipCountdownSeconds by remember { mutableIntStateOf(SKIP_SEGMENT_COUNTDOWN_SECONDS) }
    var hiddenSkipSegmentKey by remember { mutableStateOf<String?>(null) }
    var lastDoubleTapAtMs by remember { mutableLongStateOf(0L) }
    var lastDoubleTapDirection by remember { mutableIntStateOf(0) }
    var accumulatedDoubleTapSteps by remember { mutableIntStateOf(0) }
    var accumulatedDoubleTapBasePositionMs by remember { mutableLongStateOf(0L) }
    var pendingDoubleTapSeekJob by remember { mutableStateOf<Job?>(null) }
    var doubleTapSeekOverlayVisible by remember { mutableStateOf(false) }
    var doubleTapSeekOverlayDeltaMs by remember { mutableLongStateOf(0L) }
    var holdSpeedOverlayVisible by remember { mutableStateOf(false) }
    val watchedSeconds = remember(state.currentSourceId, state.currentEpisodeId) { mutableSetOf<Long>() }
    var lastTrackedPlaybackPositionMs by remember(state.currentSourceId, state.currentEpisodeId) { mutableLongStateOf(-1L) }
    val seekOverlayActive = isSeeking ||
        holdSpeedOverlayVisible ||
        (doubleTapSeekOverlayVisible && accumulatedDoubleTapSteps > 0)
    val coroutineScope = rememberCoroutineScope()
    val resumeFrameRepository = remember(context) { ResumeFrameRepository(context) }
    val offlineTitleMetadataRepository = remember(context) { OfflineTitleMetadataRepository(context) }
    val discordRpcManager = remember(context) { DiscordRpcManager.get(context) }
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    )
                    .build()
            )
            .build()
            .apply {
                playWhenReady = true
            }
    }
    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer).build()
    }
    val episodeNavigation = resolveEpisodeNavigationAvailability(
        episodes = state.episodes,
        currentEpisodeId = state.currentEpisodeId,
    )
    val hasNextEpisode = episodeNavigation.hasNext
    val hasPreviousEpisode = episodeNavigation.hasPrevious
    fun keepControlsVisible() {
        if (controlsLocked) return
        controlsVisible = true
        controlsInteractionTick += 1
    }

    fun keepUnlockButtonVisible() {
        if (!controlsLocked) return
        unlockButtonVisible = true
        unlockButtonInteractionTick += 1
    }

    fun applyPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun isInGestureArea(y: Float, height: Int): Boolean {
        // Interactive controls consume the pointer themselves. Any remaining free video area
        // should keep supporting player gestures even while controls are visible.
        return true
    }

    fun watchedSecondsSnapshot(): List<Long> = watchedSeconds.sorted()

    fun saveCurrentPlaybackProgress() {
        val safeDurationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
        val safePositionMs = resolveCurrentPlaybackPosition(
            playerPositionMs = exoPlayer.currentPosition,
            trackedPositionMs = positionMs,
            sliderPositionMs = sliderPositionMs,
        )
        viewModel.savePlaybackProgress(
            positionMs = safePositionMs,
            durationMs = safeDurationMs,
            watchedSeconds = watchedSecondsSnapshot(),
        )
    }

    fun captureCurrentVideoFrame() =
        (attachedPlayerView?.videoSurfaceView as? TextureView)
            ?.takeIf(TextureView::isAvailable)
            ?.bitmap

    fun saveCurrentVideoFrame() {
        val frame = captureCurrentVideoFrame() ?: return
        val titleId = state.currentSourceId.substringBefore(':')
        coroutineScope.launch(Dispatchers.IO) {
            resumeFrameRepository.saveFrame(titleId, frame)
        }
    }

    fun resetAccumulatedDoubleTapSeek() {
        pendingDoubleTapSeekJob?.cancel()
        pendingDoubleTapSeekJob = null
        lastDoubleTapAtMs = 0L
        lastDoubleTapDirection = 0
        accumulatedDoubleTapSteps = 0
        accumulatedDoubleTapBasePositionMs = 0L
        doubleTapSeekOverlayVisible = false
        doubleTapSeekOverlayDeltaMs = 0L
    }

    fun currentPlaybackPositionMs(): Long {
        return resolveCurrentPlaybackPosition(
            playerPositionMs = exoPlayer.currentPosition,
            trackedPositionMs = positionMs,
            sliderPositionMs = sliderPositionMs,
        )
    }

    fun commitAccumulatedDoubleTapSeek() {
        val direction = lastDoubleTapDirection
        val steps = accumulatedDoubleTapSteps
        if (direction == 0 || steps <= 0) return

        val deltaMs = SEEK_INCREMENT_MS * steps
        val safeDurationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
        val targetPositionMs = if (direction < 0) {
            (accumulatedDoubleTapBasePositionMs - deltaMs).coerceAtLeast(0L)
        } else if (safeDurationMs > 0L) {
            (accumulatedDoubleTapBasePositionMs + deltaMs).coerceAtMost(safeDurationMs)
        } else {
            accumulatedDoubleTapBasePositionMs + deltaMs
        }

        exoPlayer.seekTo(targetPositionMs)
        positionMs = targetPositionMs
        sliderPositionMs = targetPositionMs
        lastDoubleTapAtMs = 0L
        lastDoubleTapDirection = 0
        accumulatedDoubleTapSteps = 0
        accumulatedDoubleTapBasePositionMs = 0L
        pendingDoubleTapSeekJob = null
        doubleTapSeekOverlayVisible = false
    }

    fun scheduleAccumulatedDoubleTapSeek(direction: Int, eventTimeMs: Long) {
        val isAccumulating =
            direction == lastDoubleTapDirection &&
                accumulatedDoubleTapSteps > 0 &&
                eventTimeMs - lastDoubleTapAtMs <= DOUBLE_TAP_ACCUMULATION_WINDOW_MS
        val nextSteps = if (isAccumulating) accumulatedDoubleTapSteps + 1 else 1
        val basePositionMs = if (isAccumulating) {
            accumulatedDoubleTapBasePositionMs
        } else {
            currentPlaybackPositionMs()
        }

        pendingDoubleTapSeekJob?.cancel()
        lastDoubleTapAtMs = eventTimeMs
        lastDoubleTapDirection = direction
        accumulatedDoubleTapSteps = nextSteps
        accumulatedDoubleTapBasePositionMs = basePositionMs
        doubleTapSeekOverlayVisible = true
        doubleTapSeekOverlayDeltaMs = SEEK_INCREMENT_MS * nextSteps * direction
        pendingDoubleTapSeekJob = coroutineScope.launch {
            delay(DOUBLE_TAP_ACCUMULATION_WINDOW_MS)
            commitAccumulatedDoubleTapSeek()
        }
    }

    fun skipToSegmentEnd(segment: PlaybackSegment) {
        resetAccumulatedDoubleTapSeek()
        keepControlsVisible()
        exoPlayer.seekTo(segment.endMs)
        positionMs = segment.endMs
        sliderPositionMs = segment.endMs
    }

    fun runPlaybackSwitch(
        preservePosition: Boolean = false,
        action: (resumePositionMs: Long) -> Unit,
    ) {
        keepControlsVisible()
        resetAccumulatedDoubleTapSeek()
        val resumePositionMs = if (preservePosition) currentPlaybackPositionMs() else 0L
        exoPlayer.pause()
        saveCurrentPlaybackProgress()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        action(resumePositionMs)
    }

    fun pictureInPictureParams(): PictureInPictureParams {
        val actions = buildList {
            add(
                createPictureInPictureAction(
                    context = context,
                    action = PICTURE_IN_PICTURE_ACTION_TOGGLE_AUDIO_ONLY,
                    requestCode = PICTURE_IN_PICTURE_AUDIO_ONLY_REQUEST_CODE,
                    iconResId = R.drawable.ic_player_headphones_24,
                        titleResId = pictureInPictureAudioModeLocalizationKey(isAudioOnly)
                            .toPlayerPictureInPictureTitleResId(),
                )
            )
            add(
                createPictureInPictureAction(
                    context = context,
                    action = PICTURE_IN_PICTURE_ACTION_TOGGLE_PLAYBACK,
                    requestCode = PICTURE_IN_PICTURE_PLAYBACK_REQUEST_CODE,
                    iconResId = if (isPlaying) {
                        R.drawable.ic_player_media_pause_24
                    } else {
                        R.drawable.ic_player_media_play_arrow_24
                    },
                        titleResId = pictureInPicturePlaybackLocalizationKey(isPlaying)
                            .toPlayerPictureInPictureTitleResId(),
                )
            )
            if (hasPreviousEpisode) {
                add(
                    createPictureInPictureAction(
                        context = context,
                        action = PICTURE_IN_PICTURE_ACTION_PREVIOUS_EPISODE,
                        requestCode = PICTURE_IN_PICTURE_PREVIOUS_EPISODE_REQUEST_CODE,
                        iconResId = R.drawable.ic_player_media_skip_previous_24,
                        titleResId = R.string.watch_player_previous_episode,
                    )
                )
            }
            if (hasNextEpisode) {
                add(
                    createPictureInPictureAction(
                        context = context,
                        action = PICTURE_IN_PICTURE_ACTION_NEXT_EPISODE,
                        requestCode = PICTURE_IN_PICTURE_NEXT_EPISODE_REQUEST_CODE,
                        iconResId = R.drawable.ic_player_media_skip_next_24,
                        titleResId = R.string.watch_player_next_episode,
                    )
                )
            }
        }
        return PictureInPictureParams.Builder().setActions(actions).build()
    }

    DisposableEffect(context, state.currentEpisodeId, hasNextEpisode) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    PICTURE_IN_PICTURE_ACTION_TOGGLE_AUDIO_ONLY -> {
                        exoPlayer.play()
                        isAudioOnly = true
                        isPictureInPictureActive = false
                        discordRpcManager.setBackgroundAudioActive(true)
                        activity?.moveTaskToBack(true)
                    }

                    PICTURE_IN_PICTURE_ACTION_TOGGLE_PLAYBACK -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }

                    PICTURE_IN_PICTURE_ACTION_PREVIOUS_EPISODE -> {
                        if (hasPreviousEpisode) runPlaybackSwitch { viewModel.playPreviousEpisode() }
                    }

                    PICTURE_IN_PICTURE_ACTION_NEXT_EPISODE -> {
                        if (hasNextEpisode) runPlaybackSwitch { viewModel.playNextEpisode() }
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(PICTURE_IN_PICTURE_ACTION_TOGGLE_AUDIO_ONLY)
                addAction(PICTURE_IN_PICTURE_ACTION_TOGGLE_PLAYBACK)
                addAction(PICTURE_IN_PICTURE_ACTION_PREVIOUS_EPISODE)
                addAction(PICTURE_IN_PICTURE_ACTION_NEXT_EPISODE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(state.currentEpisodeId, hasPreviousEpisode, hasNextEpisode, isPlaying) {
        if (isPictureInPictureActive) {
            activity?.setPictureInPictureParams(pictureInPictureParams())
        }
    }

    val handleBackClick = remember(exoPlayer, onBackClick, state.currentSourceId) {
        {
            if (isClosing) return@remember
            isClosing = true
            resetAccumulatedDoubleTapSeek()
            controlsVisible = false
            playlistVisible = false
            settingsVisible = false
            viewModel.savePlaybackProgress(
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
                watchedSeconds = watchedSecondsSnapshot(),
            )
            exoPlayer.playWhenReady = false
            val frame = captureCurrentVideoFrame()
            val titleId = state.currentSourceId.substringBefore(':')
            coroutineScope.launch {
                if (frame != null) {
                    withContext(Dispatchers.IO) {
                        resumeFrameRepository.saveFrame(titleId, frame)
                    }
                }
                attachedPlayerView?.player = null
                exoPlayer.clearVideoSurface()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                restoreWindowUi?.invoke()
                onBackClick()
            }
        }
    }

    BackHandler(onBack = handleBackClick)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                if (playbackState == Player.STATE_READY && pendingSeekMs > 0L) {
                    exoPlayer.seekTo(pendingSeekMs)
                    positionMs = pendingSeekMs
                    sliderPositionMs = pendingSeekMs
                    pendingSeekMs = 0L
                    viewModel.consumePendingSeek()
                }
                if (playbackState == Player.STATE_ENDED && autoPlayNextEpisode) {
                    val currentEpisodeId = state.currentEpisodeId
                    val hasNextEpisode = resolveEpisodeNavigationAvailability(
                        episodes = state.episodes,
                        currentEpisodeId = currentEpisodeId,
                    ).hasNext
                    if (hasNextEpisode && handledEndedEpisodeId != currentEpisodeId) {
                        handledEndedEpisodeId = currentEpisodeId
                        viewModel.savePlaybackProgress(
                            positionMs = exoPlayer.duration.takeIf { it > 0 } ?: exoPlayer.currentPosition.coerceAtLeast(0L),
                            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
                            watchedSeconds = watchedSecondsSnapshot(),
                        )
                        viewModel.playNextEpisode()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(
                    PLAYBACK_LOG_TAG,
                    buildString {
                        append("[player.error] sourceId=")
                        append(state.currentSourceId)
                        append(" episodeId=")
                        append(state.currentEpisodeId)
                        append(" type=")
                        append(state.playback?.streamType)
                        append(" stream=")
                        append(formatShortUrl(state.playback?.streamUrl))
                        append(" code=")
                        append(error.errorCodeName)
                        append(" message=")
                        append(error.message)
                    },
                    error
                )
                viewModel.recoverFromPlaybackError(state.playback?.streamUrl)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            pendingDoubleTapSeekJob?.cancel()
            pendingDoubleTapSeekJob = null
            viewModel.savePlaybackProgress(
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
                watchedSeconds = watchedSecondsSnapshot(),
            )
            exoPlayer.removeListener(listener)
            mediaSession.release()
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    saveCurrentPlaybackProgress()
                    if (!isClosing) saveCurrentVideoFrame()
                    if (isEnteringPictureInPicture || isPictureInPictureActive || isAudioOnly) {
                        return@LifecycleEventObserver
                    }
                    lifecycleResumePositionMs = currentPlaybackPositionMs()
                    resumePlaybackAfterLifecyclePause = exoPlayer.isPlaying
                    exoPlayer.pause()
                }

                Lifecycle.Event.ON_STOP -> saveCurrentPlaybackProgress()

                Lifecycle.Event.ON_RESUME -> {
                    isEnteringPictureInPicture = false
                    isPictureInPictureActive = false
                    isAudioOnly = false
                    val resumePositionMs = lifecycleResumePositionMs
                    if (resumePositionMs > 0L) {
                        exoPlayer.seekTo(resumePositionMs)
                        positionMs = resumePositionMs
                        sliderPositionMs = resumePositionMs
                        lifecycleResumePositionMs = 0L
                    }
                    if (resumePlaybackAfterLifecyclePause) {
                        exoPlayer.play()
                    }
                    resumePlaybackAfterLifecyclePause = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        discordRpcManager,
        isEnteringPictureInPicture,
        isPictureInPictureActive,
        isAudioOnly,
    ) {
        discordRpcManager.setPictureInPictureActive(
            isEnteringPictureInPicture || isPictureInPictureActive,
        )
        discordRpcManager.setBackgroundAudioActive(isAudioOnly)
    }

    DisposableEffect(discordRpcManager) {
        onDispose {
            discordRpcManager.setPictureInPictureActive(false)
            discordRpcManager.setBackgroundAudioActive(false)
        }
    }

    DisposableEffect(activity, view) {
        if (activity == null) {
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

            fun applyPlayerWindowMode() {
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
            }

            applyPlayerWindowMode()

            var restored = false
            restoreWindowUi = restore@{
                if (restored) {
                    return@restore
                }
                restored = true
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

            view.post {
                if (!restored) {
                    applyPlayerWindowMode()
                }
            }

            onDispose {
                restoreWindowUi?.invoke()
                restoreWindowUi = null
            }
        }
    }

    LaunchedEffect(state.playback) {
        val playback = state.playback
        if (playback == null) {
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }
        AppLogger.d(
            PLAYBACK_LOG_TAG,
            buildString {
                append("[player.prepare] sourceId=")
                append(state.currentSourceId)
                append(" episodeId=")
                append(state.currentEpisodeId)
                append(" type=")
                append(playback.streamType)
                append(" streamHost=")
                append(playback.streamUrl.safeHost())
                append(" headerNames=")
                append(formatHeaderNames(playback.headers))
            },
        )
        keepControlsVisible()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaSource(playback.toMediaSource(context))
        applyPlaybackSpeed(playbackSpeed)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(state.pendingSeekMs, state.currentEpisodeId, state.currentSourceId) {
        pendingSeekMs = state.pendingSeekMs.coerceAtLeast(0L)
        handledEndedEpisodeId = null
    }

    LaunchedEffect(exoPlayer, playbackSpeed) {
        applyPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            bufferedPositionMs = exoPlayer.bufferedPosition.takeIf { it > 0 } ?: 0L
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                sliderPositionMs = positionMs
            }
            delay(250)
        }
    }

    LaunchedEffect(exoPlayer, state.currentSourceId, state.currentEpisodeId) {
        lastTrackedPlaybackPositionMs = -1L
        while (true) {
            val currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val currentDurationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            val trackingAllowed = exoPlayer.isPlaying &&
                currentDurationMs > 0L &&
                !isSeeking

            if (trackingAllowed) {
                val previousPositionMs = lastTrackedPlaybackPositionMs
                if (previousPositionMs >= 0L) {
                    val deltaMs = currentPositionMs - previousPositionMs
                    if (deltaMs in 1L..WATCHED_SECONDS_TRACKING_MAX_DELTA_MS) {
                        val startSecond = previousPositionMs / 1_000L
                        val endSecond = currentPositionMs / 1_000L
                        for (second in startSecond..endSecond) {
                            if (second * 1_000L < currentDurationMs) {
                                watchedSeconds += second
                            }
                        }
                    }
                }
                lastTrackedPlaybackPositionMs = currentPositionMs
            } else {
                lastTrackedPlaybackPositionMs = -1L
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(exoPlayer, state.currentSourceId, state.currentEpisodeId) {
        while (true) {
            delay(PLAYBACK_PROGRESS_SAVE_INTERVAL_MS)
            if (exoPlayer.isPlaying) {
                saveCurrentPlaybackProgress()
            }
        }
    }

    LaunchedEffect(
        state.playback,
        state.currentSourceId,
        state.currentEpisodeId,
        state.currentEpisodeNumber,
        isPlaying,
    ) {
        val playback = state.playback ?: return@LaunchedEffect
        val titleId = state.currentSourceId.substringBefore(':')
        val coverUrl = withContext(Dispatchers.IO) {
            offlineTitleMetadataRepository.get(titleId)?.let { metadata ->
                metadata.posterUrl ?: metadata.posterFallbackUrl
            }
        }
        while (true) {
            discordRpcManager.showPlayback(
                DiscordPlaybackPresence(
                    titleId = titleId,
                    animeTitle = state.animeTitle.ifBlank { playback.animeTitle },
                    voiceover = playback.sourceTitle,
                    episodeNumber = state.currentEpisodeNumber,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    coverUrl = coverUrl,
                ),
            )
            if (!isPlaying) return@LaunchedEffect
            delay(DISCORD_RPC_PLAYBACK_UPDATE_INTERVAL_MS)
        }
    }

    AppAutoHideVisibilityEffect(
        enabled = !controlsLocked,
        visible = controlsVisible,
        interactionTick = controlsInteractionTick,
        blocked = playlistVisible || settingsVisible || state.isLoading || state.errorMessage != null || isSeeking,
        hideDelayMillis = PLAYER_CONTROLS_AUTO_HIDE_DELAY_MS,
        onHide = { controlsVisible = false },
    )

    AppAutoHideVisibilityEffect(
        enabled = controlsLocked,
        visible = unlockButtonVisible,
        interactionTick = unlockButtonInteractionTick,
        blocked = isSeeking,
        hideDelayMillis = PLAYER_CONTROLS_AUTO_HIDE_DELAY_MS,
        onHide = { unlockButtonVisible = false },
    )

    val rawActiveSkipSegment = state.playback?.segments
        ?.firstOrNull { segment -> positionMs >= segment.startMs && positionMs < segment.endMs }
        ?.takeIf {
            !controlsLocked &&
                !playlistVisible &&
                !settingsVisible &&
                state.errorMessage == null &&
                !state.isLoading
        }
    val activeSkipSegmentKey = rawActiveSkipSegment?.let { segment ->
        buildSkipSegmentKey(
            episodeId = state.currentEpisodeId,
            segment = segment,
        )
    }
    val activeSkipSegment = rawActiveSkipSegment
        ?.takeIf { activeSkipSegmentKey != null && hiddenSkipSegmentKey != activeSkipSegmentKey }

    LaunchedEffect(activeSkipSegmentKey, autoSkipSegments) {
        val key = activeSkipSegmentKey ?: run {
            skipCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS
            return@LaunchedEffect
        }
        val segment = rawActiveSkipSegment ?: return@LaunchedEffect
        if (hiddenSkipSegmentKey == key) return@LaunchedEffect

        skipCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS
        repeat(SKIP_SEGMENT_COUNTDOWN_SECONDS) {
            delay(1_000L)
            if (hiddenSkipSegmentKey == key) return@LaunchedEffect
            skipCountdownSeconds = (skipCountdownSeconds - 1).coerceAtLeast(0)
        }

        if (autoSkipSegments && hiddenSkipSegmentKey != key) {
            skipToSegmentEnd(segment)
        } else if (!autoSkipSegments && hiddenSkipSegmentKey != key) {
            hiddenSkipSegmentKey = key
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(settingsVisible, playlistVisible, controlsLocked, playbackSpeed, durationMs) {
                if (settingsVisible || playlistVisible) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = true)
                    var upPosition = firstDown.position
                    var holdSpeedActive = false
                    var holdSpeedEligible = !controlsLocked
                    val holdSpeedDeadlineMs = firstDown.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    holdSpeedOverlayVisible = false

                    while (true) {
                        val event = if (holdSpeedEligible && !holdSpeedActive) {
                            val remainingTimeoutMs = (holdSpeedDeadlineMs - SystemClock.uptimeMillis())
                                .coerceAtLeast(1L)
                            withTimeoutOrNull(remainingTimeoutMs) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            holdSpeedActive = true
                            holdSpeedOverlayVisible = true
                            applyPlaybackSpeed(2f)
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
                        if (!change.pressed) {
                            upPosition = change.position
                            break
                        }
                        val totalDragX = change.position.x - firstDown.position.x
                        val totalDragY = change.position.y - firstDown.position.y
                        val movedTooFarForHold =
                            kotlin.math.abs(totalDragX) >= viewConfiguration.touchSlop ||
                                kotlin.math.abs(totalDragY) >= viewConfiguration.touchSlop
                        if (movedTooFarForHold) holdSpeedEligible = false
                    }

                    if (holdSpeedActive) {
                        holdSpeedOverlayVisible = false
                        applyPlaybackSpeed(playbackSpeed)
                        return@awaitEachGesture
                    }

                    val secondDown = withTimeoutOrNull(DOUBLE_TAP_TIMEOUT_MS) {
                        awaitFirstDown(requireUnconsumed = true)
                    }
                    if (secondDown != null) {
                        val secondUp = waitForUpOrCancellation()
                        if (secondUp == null) return@awaitEachGesture
                        val tapOffset = secondDown.position
                        if (!isInGestureArea(tapOffset.y, size.height)) {
                            if (controlsLocked) {
                                keepUnlockButtonVisible()
                            } else if (controlsVisible) {
                                controlsVisible = false
                            } else {
                                controlsVisible = true
                                controlsInteractionTick += 1
                            }
                            return@awaitEachGesture
                        }
                        if (controlsLocked) {
                            keepUnlockButtonVisible()
                        } else if (controlsVisible) {
                            keepControlsVisible()
                        }
                        scheduleAccumulatedDoubleTapSeek(
                            direction = if (tapOffset.x < size.width / 2f) -1 else 1,
                            eventTimeMs = secondUp.uptimeMillis,
                        )
                    } else {
                        if (accumulatedDoubleTapSteps > 0) return@awaitEachGesture
                        if (controlsLocked) {
                            unlockButtonVisible = !unlockButtonVisible
                            if (unlockButtonVisible) {
                                unlockButtonInteractionTick += 1
                            }
                            return@awaitEachGesture
                        }
                        if (controlsVisible) {
                            controlsVisible = false
                        } else {
                            controlsVisible = true
                            controlsInteractionTick += 1
                        }
                    }
                }
            }
    ) {
        if (!isClosing) AndroidView(
            factory = { viewContext ->
                (LayoutInflater.from(viewContext)
                    .inflate(R.layout.view_media3_player, null, false) as PlayerView)
                    .apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = exoPlayer
                    applyVideoScale(videoScaleMode, videoAspectRatio)
                    attachedPlayerView = this
                }
            },
            update = { playerView ->
                attachedPlayerView = playerView
                playerView.player = if (isAudioOnly) null else exoPlayer
                playerView.applyVideoScale(videoScaleMode, videoAspectRatio)
            },
            modifier = Modifier.fillMaxSize()
        )

        AppPlayerSpeedOverlay(
            visible = holdSpeedOverlayVisible,
            label = "2×",
            modifier = Modifier.align(Alignment.Center),
        )

        AppPlayerSeekOverlay(
            visible = doubleTapSeekOverlayVisible && doubleTapSeekOverlayDeltaMs != 0L,
            label = formatSeekDeltaLabel(doubleTapSeekOverlayDeltaMs),
            modifier = Modifier.align(Alignment.Center),
        )

        activeSkipSegment?.let { skipSegment ->
            AppPlayerSkipSegmentOverlay(
                visible = !isPictureInPictureActive,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = PlayerSkipSegmentEndPadding,
                        bottom = if (controlsVisible) {
                            PlayerSkipSegmentControlsBottomPadding
                        } else {
                            PlayerSkipSegmentBottomPadding
                        },
                    ),
                countdownSeconds = skipCountdownSeconds,
                maxCountdownSeconds = SKIP_SEGMENT_COUNTDOWN_SECONDS,
                autoSkipEnabled = autoSkipSegments,
                skipLabel = stringResource(R.string.watch_player_skip),
                watchLabel = stringResource(R.string.watch_player_watch),
                onSkipClick = {
                    skipToSegmentEnd(skipSegment)
                },
                onWatchClick = {
                    activeSkipSegmentKey?.let { hiddenSkipSegmentKey = it }
                },
            )
        }

        AppPlayerControlsOverlay(
            visible = !isPictureInPictureActive &&
                !controlsLocked &&
                (controlsVisible || state.isLoading || state.errorMessage != null),
            modifier = Modifier.fillMaxSize(),
        ) {
                AppPlayerTopOverlay(
                    title = state.animeTitle,
                    subtitle = currentEpisodeSubtitle(state),
                    playlistEnabled = state.episodes.isNotEmpty(),
                    backContent = {
                        SharedBackButton(
                            onClick = handleBackClick,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    },
                    playlistContent = {
                        AppPlayerPlaylistButton(
                            onClick = {
                                keepControlsVisible()
                                playlistVisible = true
                            },
                            contentDescription = stringResource(R.string.watch_player_playlist),
                        )
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                AppPlayerCenterControls(
                    visible = !seekOverlayActive && !state.isLoading && !isBuffering,
                    hasPreviousEpisode = hasPreviousEpisode,
                    hasNextEpisode = hasNextEpisode,
                    onTogglePlay = {
                        keepControlsVisible()
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onPreviousEpisode = {
                        runPlaybackSwitch { viewModel.playPreviousEpisode() }
                    },
                    onNextEpisode = {
                        runPlaybackSwitch { viewModel.playNextEpisode() }
                    },
                    isPlaying = isPlaying,
                    modifier = Modifier.align(Alignment.Center),
                )

                AppPlayerBottomOverlay(
                    positionLabel = "${formatEpisodeDuration(sliderPositionMs)} / ${formatEpisodeDuration(durationMs)}",
                    durationMs = durationMs,
                    bufferedPositionMs = bufferedPositionMs,
                    sliderPositionMs = sliderPositionMs,
                    onSliderValueChange = { newValue ->
                        keepControlsVisible()
                        isSeeking = true
                        sliderPositionMs = newValue
                    },
                    onSliderValueChangeFinished = {
                        keepControlsVisible()
                        resetAccumulatedDoubleTapSeek()
                        exoPlayer.seekTo(sliderPositionMs)
                        positionMs = sliderPositionMs
                        isSeeking = false
                    },
                    controlsContent = {
                        AppPlayerActionControls(
                            onScaleClick = {
                                keepControlsVisible()
                                appPreferences.setVideoScaleMode(videoScaleMode.next())
                            },
                            scaleMode = videoScaleMode,
                            scaleContentDescription = stringResource(videoScaleMode.contentDescriptionResId()),
                            onLockClick = {
                                controlsLocked = true
                                controlsVisible = false
                                unlockButtonVisible = true
                                unlockButtonInteractionTick += 1
                                playlistVisible = false
                                settingsVisible = false
                            },
                            lockContentDescription = stringResource(R.string.watch_player_lock),
                            pictureInPictureEnabled = pictureInPictureSupported && state.playback != null,
                            onPictureInPictureClick = {
                                isEnteringPictureInPicture = true
                                discordRpcManager.setPictureInPictureActive(true)
                                controlsVisible = false
                                val entered = runCatching {
                                    activity?.enterPictureInPictureMode(pictureInPictureParams()) ?: false
                                }.getOrDefault(false)
                                isPictureInPictureActive = entered
                                if (!entered) {
                                    isEnteringPictureInPicture = false
                                    discordRpcManager.setPictureInPictureActive(false)
                                }
                            },
                            pictureInPictureContentDescription = stringResource(R.string.watch_player_picture_in_picture),
                            onSettingsClick = {
                                keepControlsVisible()
                                settingsDestination = PlayerSettingsDestination.Root
                                settingsVisible = true
                                viewModel.loadSettingsOptions()
                            },
                            settingsContentDescription = stringResource(R.string.watch_player_settings),
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
        }

        AppPlayerUnlockOverlay(
            visible = controlsLocked && unlockButtonVisible,
            label = stringResource(R.string.watch_player_unlock),
            onClick = {
                controlsLocked = false
                unlockButtonVisible = false
                keepControlsVisible()
            },
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = PlayerUnlockBottomPadding),
        )

        AppPlayerLoadingOverlay(
            visible = state.isLoading || isBuffering,
        )

        state.errorMessage?.let { message ->
            AppPlayerErrorOverlay(
                message = message,
                title = stringResource(R.string.watch_player_error_title),
                retryLabel = stringResource(R.string.watch_player_retry),
                onRetry = { viewModel.load(forceRefresh = true) },
            )
        }

        if (playlistVisible) {
            AppPlayerOverlayPanel(
                onDismissRequest = {
                    playlistVisible = false
                    keepControlsVisible()
                },
                widthFraction = PlayerPlaylistPanelWidthFraction,
                maxWidth = PlayerPlaylistPanelMaxWidth,
                swipeToDismissEnabled = false,
                nowMs = SystemClock::elapsedRealtime,
                backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
            ) { dismissPanel ->
                AppPlaylistBottomSheet(
                    currentEpisodeId = state.currentEpisodeId,
                    episodes = state.episodes,
                    headline = { episode ->
                        resolveEpisodeNumberTitle(
                            episodeNumber = episode.number,
                            episodeLabel = { number -> stringResource(R.string.watch_episode_number, number) },
                        )
                    },
                    onEpisodeClick = { episodeId ->
                        dismissPanel()
                        runPlaybackSwitch { viewModel.selectEpisode(episodeId) }
                    }
                )
            }
        }

        if (settingsVisible) {
            AppPlayerOverlayPanel(
                onDismissRequest = {
                    settingsVisible = false
                    settingsDestination = PlayerSettingsDestination.Root
                    keepControlsVisible()
                },
                widthFraction = PlayerSettingsPanelWidthFraction,
                maxWidth = PlayerSettingsPanelMaxWidth,
                restingOffsetY = PlayerSettingsPanelRestingOffsetY,
                swipeToDismissEnabled = false,
                nowMs = SystemClock::elapsedRealtime,
                backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
            ) { dismissPanel ->
                PlayerSettingsSheet(
                    destination = settingsDestination,
                    selectedSpeed = playbackSpeed,
                    selectedSourceId = state.currentSourceId,
                    selectedPlayerName = state.selectedPlayerName,
                    selectedQualityLabel = state.selectedQualityLabel ?: state.playback?.qualityLabel,
                    availableQualityLabels = state.playback?.availableQualityLabels.orEmpty(),
                    autoSkipSegments = autoSkipSegments,
                    autoPlayNextEpisode = autoPlayNextEpisode,
                    options = state.settingsOptions,
                    onNavigate = {
                        settingsDestination = it
                        keepControlsVisible()
                    },
                    onBack = {
                        settingsDestination = PlayerSettingsDestination.Root
                        keepControlsVisible()
                    },
                    onSelectSpeed = { speed ->
                        keepControlsVisible()
                        playbackSpeed = speed
                        appPreferences.setPlaybackSpeed(speed)
                        applyPlaybackSpeed(speed)
                    },
                    onSelectVoiceover = { source ->
                        runPlaybackSwitch(preservePosition = true) { resumePositionMs ->
                            viewModel.selectVoiceover(source, resumePositionMs)
                        }
                    },
                    onSelectPlayer = { playerName ->
                        runPlaybackSwitch(preservePosition = true) { resumePositionMs ->
                            viewModel.selectPlayer(playerName, resumePositionMs)
                        }
                    },
                    onSelectQuality = { quality ->
                        runPlaybackSwitch(preservePosition = true) { resumePositionMs ->
                            viewModel.selectQuality(quality, resumePositionMs)
                        }
                    },
                    onAutoSkipSegmentsChange = { enabled ->
                        keepControlsVisible()
                        appPreferences.setAutoSkipSegments(enabled)
                    },
                    onAutoPlayNextEpisodeChange = { enabled ->
                        keepControlsVisible()
                        appPreferences.setAutoPlayNextEpisode(enabled)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerSettingsSheet(
    destination: PlayerSettingsDestination,
    selectedSpeed: Float,
    selectedSourceId: String,
    selectedPlayerName: String?,
    selectedQualityLabel: String?,
    availableQualityLabels: List<String>,
    autoSkipSegments: Boolean,
    autoPlayNextEpisode: Boolean,
    options: org.akkirrai.hibiki.core.model.PlaybackSettingsOptions,
    onNavigate: (PlayerSettingsDestination) -> Unit,
    onBack: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectVoiceover: (WatchSource) -> Unit,
    onSelectPlayer: (String?) -> Unit,
    onSelectQuality: (String?) -> Unit,
    onAutoSkipSegmentsChange: (Boolean) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
) {
    val speedValues = playbackSpeedOptions.map { speed ->
        PlayerSettingsValue(
            id = speed.toString(),
            label = formatPlaybackSpeed(speed),
            selected = selectedSpeed == speed,
            onClick = { onSelectSpeed(speed) },
        )
    }
    val voiceoverValues = options.voiceovers.map { source ->
        PlayerSettingsValue(
            id = source.sourceId,
            label = source.title.ifBlank { source.sourceId },
            description = source.qualityLabel,
            selected = selectedSourceId == source.sourceId,
            onClick = { onSelectVoiceover(source) },
        )
    }
    val playerValues = uniquePlayerNames(options.links).map { name ->
        PlayerSettingsValue(
            id = name,
            label = name,
            selected = selectedPlayerName == name || (selectedPlayerName == null && options.links.firstOrNull()?.playerName == name),
            onClick = { onSelectPlayer(name) },
        )
    }
    val qualityValues = sortQualityLabels(options.links.mapNotNull { it.qualityLabel } + availableQualityLabels)
        .map { quality ->
            PlayerSettingsValue(
                id = quality,
                label = quality,
                selected = selectedQualityLabel == quality,
                onClick = { onSelectQuality(quality) },
            )
        }
    val rootEntries = buildPlayerSettingsRootEntries(
        speedValues = speedValues,
        voiceoverValues = voiceoverValues,
        playerValues = playerValues,
        qualityValues = qualityValues,
        autoSkipSegments = autoSkipSegments,
        autoPlayNextEpisode = autoPlayNextEpisode,
        onNavigate = onNavigate,
        onAutoSkipSegmentsChange = onAutoSkipSegmentsChange,
        onAutoPlayNextEpisodeChange = onAutoPlayNextEpisodeChange,
        voiceoverTitle = stringResource(R.string.watch_player_settings_voiceover),
        qualityTitle = stringResource(R.string.watch_player_settings_quality),
        speedTitle = stringResource(R.string.watch_player_settings_speed),
        autoSkipTitle = stringResource(R.string.watch_player_settings_auto_skip),
        autoSkipValue = stringResource(
            playerToggleValueLocalizationKey(autoSkipSegments).toPlayerSettingsValueResId()
        ),
        autoPlayTitle = stringResource(R.string.watch_player_settings_auto_play_next),
        autoPlayValue = stringResource(
            playerToggleValueLocalizationKey(autoPlayNextEpisode).toPlayerSettingsValueResId()
        ),
        playerTitle = stringResource(R.string.watch_player_settings_player),
    )

    BackHandler(enabled = destination != PlayerSettingsDestination.Root) {
        onBack()
    }

    AppPlayerSettingsSheet(
        destination = destination,
        title = { targetDestination -> stringResource(targetDestination.titleResId) },
        onBack = onBack,
        backContent = {
            SharedBackButton(
                onClick = onBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        },
        content = { targetDestination ->
            appPlayerSettingsItems(
                destination = targetDestination,
                rootEntries = rootEntries,
                speedValues = speedValues,
                voiceoverValues = voiceoverValues,
                playerValues = playerValues,
                qualityValues = qualityValues,
                entryContent = { entry ->
                    AppPlayerSettingsEntry(
                        title = entry.title,
                        value = entry.value,
                        onClick = entry.onClick,
                    )
                },
                choiceContent = { value ->
                    AppPlayerSettingsChoice(
                        label = value.label,
                        description = value.description,
                        selected = value.selected,
                        onClick = value.onClick,
                    )
                },
            )
        },
    )
}
private val PlayerSettingsDestination.titleResId: Int
    get() = when (localizationKey()) {
        "watch_player_settings_root" -> R.string.watch_player_settings_root
        "watch_player_settings_speed" -> R.string.watch_player_settings_speed
        "watch_player_settings_voiceover" -> R.string.watch_player_settings_voiceover
        "watch_player_settings_player" -> R.string.watch_player_settings_player
        "watch_player_settings_quality" -> R.string.watch_player_settings_quality
        else -> error("Unknown player settings localization key")
    }

private fun String.toPlayerSettingsValueResId(): Int = when (this) {
    "watch_player_settings_on" -> R.string.watch_player_settings_on
    "watch_player_settings_off" -> R.string.watch_player_settings_off
    else -> error("Unknown player settings value localization key")
}

private fun String.toPlayerPictureInPictureTitleResId(): Int = when (this) {
    "watch_player_show_video" -> R.string.watch_player_show_video
    "watch_player_audio_only" -> R.string.watch_player_audio_only
    "watch_player_pause" -> R.string.watch_player_pause
    "watch_player_play" -> R.string.watch_player_play
    else -> error("Unknown player picture-in-picture localization key")
}

private fun PlayerView.applyVideoScale(mode: VideoScaleMode, videoAspectRatio: Float) {
    val textureView = videoSurfaceView as? TextureView ?: return
    if (!textureView.isLaidOut || textureView.width == 0 || textureView.height == 0) {
        textureView.doOnLayout { applyVideoScale(mode, videoAspectRatio) }
        return
    }

    val containerAspectRatio = textureView.width.toFloat() / textureView.height
    val aspectRatioFactor = videoAspectRatio / containerAspectRatio
    val factors = resolveVideoScaleFactors(mode, aspectRatioFactor)
    val scaleX = factors.scaleX
    val scaleY = factors.scaleY
    val target = TextureVideoScale(mode, scaleX, scaleY)
    if (textureView.tag == target) return

    textureView.tag = target
    textureView.animate()
        .cancel()
    textureView.animate()
        .scaleX(scaleX)
        .scaleY(scaleY)
        .setDuration(PLAYER_VIDEO_SCALE_ANIMATION_DURATION_MS)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

private data class TextureVideoScale(
    val mode: VideoScaleMode,
    val scaleX: Float,
    val scaleY: Float,
)

@StringRes
private fun VideoScaleMode.contentDescriptionResId(): Int = when (localizationKey()) {
    "watch_player_video_scale_fit" -> R.string.watch_player_video_scale_fit
    "watch_player_video_scale_crop" -> R.string.watch_player_video_scale_crop
    "watch_player_video_scale_stretch" -> R.string.watch_player_video_scale_stretch
    else -> error("Unknown video scale localization key")
}

private fun PlaybackStream.toMediaSource(context: Context): MediaSource {
    val dataSourceFactory = OfflineMediaCache.buildPlaybackDataSourceFactory(
        context = context,
        headers = headers,
    )
    val mediaItem = MediaItem.Builder()
        .setUri(streamUrl.toUri())
        .setMimeType(
            when (streamType) {
                PlaybackStreamType.HLS -> MimeTypes.APPLICATION_M3U8
                PlaybackStreamType.MP4 -> MimeTypes.VIDEO_MP4
                PlaybackStreamType.DASH -> MimeTypes.APPLICATION_MPD
            }
        )
        .build()

    return when (streamType) {
        PlaybackStreamType.HLS -> HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
        PlaybackStreamType.DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        PlaybackStreamType.MP4 -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }
}

@Composable
private fun currentEpisodeSubtitle(state: PlayerUiState): String {
    return resolvePlayerEpisodeSubtitle(
        state = state,
        episodeLabel = { number -> stringResource(R.string.watch_episode_number, number) },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun createPictureInPictureAction(
    context: Context,
    action: String,
    requestCode: Int,
    iconResId: Int,
    @StringRes titleResId: Int,
): RemoteAction {
    val title = context.getString(titleResId)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return RemoteAction(
        Icon.createWithResource(context, iconResId),
        title,
        title,
        pendingIntent,
    )
}

private const val SEEK_INCREMENT_MS = 10_000L
private const val DOUBLE_TAP_TIMEOUT_MS = 260L
private const val DOUBLE_TAP_ACCUMULATION_WINDOW_MS = 700L
private const val MIN_BUFFER_MS = 30_000
private const val MAX_BUFFER_MS = 60_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_500
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
private const val PLAYBACK_LOG_TAG = "HibikiPlayback"
private const val WATCHED_SECONDS_TRACKING_MAX_DELTA_MS = 2_500L
private const val PLAYBACK_PROGRESS_SAVE_INTERVAL_MS = 30_000L
private const val DISCORD_RPC_PLAYBACK_UPDATE_INTERVAL_MS = 30_000L
private const val PLAYER_CONTROLS_AUTO_HIDE_DELAY_MS = 2_500L
private const val SKIP_SEGMENT_COUNTDOWN_SECONDS = 10
private const val PICTURE_IN_PICTURE_ACTION_TOGGLE_AUDIO_ONLY =
    "org.akkirrai.hibiki.action.TOGGLE_AUDIO_ONLY"
private const val PICTURE_IN_PICTURE_ACTION_TOGGLE_PLAYBACK =
    "org.akkirrai.hibiki.action.TOGGLE_PLAYBACK"
private const val PICTURE_IN_PICTURE_ACTION_PREVIOUS_EPISODE =
    "org.akkirrai.hibiki.action.PREVIOUS_EPISODE"
private const val PICTURE_IN_PICTURE_ACTION_NEXT_EPISODE =
    "org.akkirrai.hibiki.action.NEXT_EPISODE"
private const val PICTURE_IN_PICTURE_AUDIO_ONLY_REQUEST_CODE = 1001
private const val PICTURE_IN_PICTURE_PLAYBACK_REQUEST_CODE = 1002
private const val PICTURE_IN_PICTURE_PREVIOUS_EPISODE_REQUEST_CODE = 1003
private const val PICTURE_IN_PICTURE_NEXT_EPISODE_REQUEST_CODE = 1004
private const val PLAYER_VIDEO_SCALE_ANIMATION_DURATION_MS = 220L
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private fun String?.safeHost(): String {
    if (this.isNullOrBlank()) return "unknown"
    return runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "unknown"
}

