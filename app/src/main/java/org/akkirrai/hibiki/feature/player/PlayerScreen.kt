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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.math.max
import kotlin.math.min
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.download.OfflineMediaCache
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.VideoScaleMode
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppFilledIconButton
import org.akkirrai.hibiki.core.design.component.AppFilledIconButtonStyle
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.PlaybackSegment
import org.akkirrai.hibiki.core.model.PlaybackSegmentType
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackStreamType
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset

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
    var gestureSeekPreviewMs by remember { mutableLongStateOf(0L) }
    var gestureSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var gestureSeekStartMs by remember { mutableLongStateOf(0L) }
    var gestureSeekDragPx by remember { mutableFloatStateOf(0f) }
    var gestureSeekStartedWithControlsVisible by remember { mutableStateOf(false) }
    var gestureSeekInProgress by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var gestureSeekActive by remember { mutableStateOf(false) }
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
    val seekOverlayActive = gestureSeekInProgress ||
        gestureSeekActive ||
        isSeeking ||
        holdSpeedOverlayVisible ||
        (doubleTapSeekOverlayVisible && accumulatedDoubleTapSteps > 0)
    val coroutineScope = rememberCoroutineScope()
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
    val hasNextEpisode = state.episodes.indexOfFirst { it.id == state.currentEpisodeId }
        .let { it != -1 && it < state.episodes.lastIndex }
    val hasPreviousEpisode = state.episodes.indexOfFirst { it.id == state.currentEpisodeId } > 0
    val seekGestureTouchSlopPx = viewConfiguration.touchSlop * SEEK_GESTURE_TOUCH_SLOP_MULTIPLIER

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
        val currentPlayerPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val safePositionMs = when {
            currentPlayerPositionMs > 0L -> currentPlayerPositionMs
            positionMs > 0L -> positionMs
            else -> sliderPositionMs.coerceAtLeast(0L)
        }
        viewModel.savePlaybackProgress(
            positionMs = safePositionMs,
            durationMs = safeDurationMs,
            watchedSeconds = watchedSecondsSnapshot(),
        )
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
        val currentPlayerPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        return when {
            currentPlayerPositionMs > 0L -> currentPlayerPositionMs
            positionMs > 0L -> positionMs
            else -> sliderPositionMs.coerceAtLeast(0L)
        }
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

    fun runPlaybackSwitch(action: () -> Unit) {
        keepControlsVisible()
        resetAccumulatedDoubleTapSeek()
        saveCurrentPlaybackProgress()
        action()
    }

    fun pictureInPictureParams(): PictureInPictureParams {
        val actions = buildList {
            add(
                createPictureInPictureAction(
                    context = context,
                    action = PICTURE_IN_PICTURE_ACTION_TOGGLE_AUDIO_ONLY,
                    requestCode = PICTURE_IN_PICTURE_AUDIO_ONLY_REQUEST_CODE,
                    iconResId = R.drawable.ic_player_headphones_24,
                    titleResId = if (isAudioOnly) {
                        R.string.watch_player_show_video
                    } else {
                        R.string.watch_player_audio_only
                    },
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
                    titleResId = if (isPlaying) {
                        R.string.watch_player_pause
                    } else {
                        R.string.watch_player_play
                    },
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
                        activity?.moveTaskToBack(true)
                    }

                    PICTURE_IN_PICTURE_ACTION_TOGGLE_PLAYBACK -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }

                    PICTURE_IN_PICTURE_ACTION_PREVIOUS_EPISODE -> {
                        if (hasPreviousEpisode) runPlaybackSwitch(viewModel::playPreviousEpisode)
                    }

                    PICTURE_IN_PICTURE_ACTION_NEXT_EPISODE -> {
                        if (hasNextEpisode) runPlaybackSwitch(viewModel::playNextEpisode)
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

    val handleBackClick = remember(exoPlayer, onBackClick) {
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
            attachedPlayerView?.player = null
            exoPlayer.clearVideoSurface()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            restoreWindowUi?.invoke()
            onBackClick()
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
                    val currentIndex = state.episodes.indexOfFirst { it.id == currentEpisodeId }
                    val hasNextEpisode = currentIndex != -1 && currentIndex < state.episodes.lastIndex
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
                        append(state.playback?.streamUrl.shortUrl())
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
        val playback = state.playback ?: return@LaunchedEffect
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
                append(playback.headers.safeHeaderNames())
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
                !isSeeking &&
                !gestureSeekActive &&
                !gestureSeekInProgress

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

    AutoHideVisibilityEffect(
        enabled = !controlsLocked,
        visible = controlsVisible,
        interactionTick = controlsInteractionTick,
        blocked = playlistVisible || settingsVisible || state.isLoading || state.errorMessage != null || gestureSeekActive || isSeeking,
        onHide = { controlsVisible = false },
    )

    AutoHideVisibilityEffect(
        enabled = controlsLocked,
        visible = unlockButtonVisible,
        interactionTick = unlockButtonInteractionTick,
        blocked = gestureSeekActive || isSeeking,
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
                    var handledAsSeek = false
                    var holdSpeedActive = false
                    var holdSpeedEligible = !controlsLocked
                    val holdSpeedDeadlineMs = firstDown.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    val canSeekByGesture = isInGestureArea(firstDown.position.y, size.height) && durationMs > 0L

                    gestureSeekInProgress = false
                    gestureSeekActive = false
                    gestureSeekStartedWithControlsVisible = false
                    gestureSeekStartMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    gestureSeekPreviewMs = gestureSeekStartMs
                    gestureSeekDeltaMs = 0L
                    gestureSeekDragPx = 0f
                    holdSpeedOverlayVisible = false

                    while (true) {
                        val event = if (holdSpeedEligible && !holdSpeedActive && !handledAsSeek) {
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
                        if (!canSeekByGesture) continue
                        if (!gestureSeekActive && !isInGestureArea(change.position.y, size.height)) continue

                        val totalDragX = change.position.x - firstDown.position.x
                        val totalDragY = change.position.y - firstDown.position.y
                        val absoluteDragX = kotlin.math.abs(totalDragX)
                        val absoluteDragY = kotlin.math.abs(totalDragY)
                        val movedTooFarForHold =
                            absoluteDragX >= seekGestureTouchSlopPx ||
                                absoluteDragY >= seekGestureTouchSlopPx
                        if (movedTooFarForHold) holdSpeedEligible = false

                        if (holdSpeedActive) continue

                        val isClearHorizontalSeek =
                            absoluteDragX >= seekGestureTouchSlopPx &&
                                absoluteDragX > absoluteDragY * SEEK_GESTURE_HORIZONTAL_DOMINANCE

                        if (!gestureSeekActive && !isClearHorizontalSeek) continue

                        val width = size.width.takeIf { it > 0 } ?: continue
                        val safeDuration = durationMs.takeIf { it > 0L } ?: continue
                        gestureSeekDragPx = totalDragX
                        val totalDelta = (SEEK_GESTURE_FULL_WIDTH_MS * (gestureSeekDragPx / width.toFloat())).toLong()
                        val updatedPosition = (gestureSeekStartMs + totalDelta).coerceIn(0L, safeDuration)
                        if (!gestureSeekActive && kotlin.math.abs(totalDelta) < SEEK_GESTURE_THRESHOLD_MS) continue

                        if (!gestureSeekActive) {
                            gestureSeekStartedWithControlsVisible = controlsVisible
                        }
                        if (controlsLocked && unlockButtonVisible) {
                            keepUnlockButtonVisible()
                        } else if (!controlsLocked && gestureSeekStartedWithControlsVisible) {
                            keepControlsVisible()
                        }
                        change.consume()
                        gestureSeekInProgress = true
                        gestureSeekActive = true
                        handledAsSeek = true
                        gestureSeekPreviewMs = updatedPosition
                        gestureSeekDeltaMs = totalDelta
                    }

                    if (handledAsSeek) {
                        if (controlsLocked && unlockButtonVisible) {
                            keepUnlockButtonVisible()
                        } else if (!controlsLocked && gestureSeekStartedWithControlsVisible) {
                            keepControlsVisible()
                        }
                        resetAccumulatedDoubleTapSeek()
                        exoPlayer.seekTo(gestureSeekPreviewMs)
                        positionMs = gestureSeekPreviewMs
                        sliderPositionMs = gestureSeekPreviewMs
                        gestureSeekInProgress = false
                        gestureSeekActive = false
                        gestureSeekStartedWithControlsVisible = false
                        gestureSeekDeltaMs = 0L
                        gestureSeekDragPx = 0f
                        return@awaitEachGesture
                    }

                    gestureSeekInProgress = false
                    gestureSeekActive = false
                    gestureSeekStartedWithControlsVisible = false
                    gestureSeekDeltaMs = 0L
                    gestureSeekDragPx = 0f

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

        AnimatedVisibility(
            visible = gestureSeekActive,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140)),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatDuration(gestureSeekPreviewMs),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildSeekDeltaLabel(gestureSeekDeltaMs),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = holdSpeedOverlayVisible && !gestureSeekActive,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Text(
                    text = "2×",
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AnimatedVisibility(
            visible = doubleTapSeekOverlayVisible && doubleTapSeekOverlayDeltaMs != 0L && !gestureSeekActive,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Text(
                    text = buildSeekDeltaLabel(doubleTapSeekOverlayDeltaMs),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        activeSkipSegment?.let { skipSegment ->
            AnimatedVisibility(
                visible = !isPictureInPictureActive,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = if (controlsVisible) 140.dp else 32.dp,
                    ),
                enter = fadeIn(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(140)),
            ) {
                PlayerSkipSegmentOverlay(
                    countdownSeconds = skipCountdownSeconds,
                    autoSkipEnabled = autoSkipSegments,
                    onSkipClick = {
                        skipToSegmentEnd(skipSegment)
                    },
                    onWatchClick = {
                        activeSkipSegmentKey?.let { hiddenSkipSegmentKey = it }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = !isPictureInPictureActive &&
                !controlsLocked &&
                (controlsVisible || state.isLoading || state.errorMessage != null),
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerTopOverlay(
                    title = state.animeTitle,
                    subtitle = currentEpisodeSubtitle(state),
                    onBackClick = handleBackClick,
                    onPlaylistClick = {
                        keepControlsVisible()
                        playlistVisible = true
                    },
                    playlistEnabled = state.episodes.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                AnimatedVisibility(
                    visible = !seekOverlayActive && !state.isLoading && !isBuffering,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(90)),
                ) {
                    PlayerCenterControls(
                        isPlaying = isPlaying,
                        hasPreviousEpisode = hasPreviousEpisode,
                        hasNextEpisode = hasNextEpisode,
                        onTogglePlay = {
                            keepControlsVisible()
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onPreviousEpisode = {
                            runPlaybackSwitch(viewModel::playPreviousEpisode)
                        },
                        onNextEpisode = {
                            runPlaybackSwitch(viewModel::playNextEpisode)
                        },
                    )
                }

                PlayerBottomOverlay(
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
                    videoScaleMode = videoScaleMode,
                    onVideoScaleModeClick = {
                        keepControlsVisible()
                        appPreferences.setVideoScaleMode(videoScaleMode.next())
                    },
                    settingsEnabled = true,
                    onSettingsClick = {
                        keepControlsVisible()
                        settingsDestination = PlayerSettingsDestination.Root
                        settingsVisible = true
                        viewModel.loadSettingsOptions()
                    },
                    pictureInPictureEnabled = pictureInPictureSupported && state.playback != null,
                    onPictureInPictureClick = {
                        isEnteringPictureInPicture = true
                        controlsVisible = false
                        val entered = runCatching {
                            activity?.enterPictureInPictureMode(pictureInPictureParams()) ?: false
                        }.getOrDefault(false)
                        isPictureInPictureActive = entered
                        if (!entered) {
                            isEnteringPictureInPicture = false
                        }
                    },
                    onLockClick = {
                        controlsLocked = true
                        controlsVisible = false
                        unlockButtonVisible = true
                        unlockButtonInteractionTick += 1
                        playlistVisible = false
                        settingsVisible = false
                        gestureSeekActive = false
                        gestureSeekDeltaMs = 0L
                        gestureSeekDragPx = 0f
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsLocked && unlockButtonVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            PlayerUnlockButton(
                onClick = {
                    controlsLocked = false
                    unlockButtonVisible = false
                    keepControlsVisible()
                }
            )
        }

        if (state.isLoading || isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(PLAYER_CENTER_PRIMARY_BUTTON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f)),
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.matchParentSize(),
                        color = Color.White,
                        strokeWidth = 4.dp,
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.84f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.watch_player_error_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = message,
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.watch_player_retry),
                            modifier = Modifier
                                .alpha(0.92f)
                                .clickable { viewModel.load(forceRefresh = true) },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (playlistVisible) {
            PlayerOverlayPanel(
                onDismissRequest = {
                    playlistVisible = false
                    keepControlsVisible()
                },
                widthFraction = PLAYER_PLAYLIST_PANEL_WIDTH_FRACTION,
                maxWidth = PLAYER_PLAYLIST_PANEL_MAX_WIDTH,
                swipeToDismissEnabled = false,
            ) { dismissPanel ->
                PlaylistBottomSheet(
                    currentEpisodeId = state.currentEpisodeId,
                    episodes = state.episodes,
                    onEpisodeClick = { episodeId ->
                        dismissPanel()
                        runPlaybackSwitch { viewModel.selectEpisode(episodeId) }
                    }
                )
            }
        }

        if (settingsVisible) {
            PlayerOverlayPanel(
                onDismissRequest = {
                    settingsVisible = false
                    settingsDestination = PlayerSettingsDestination.Root
                    keepControlsVisible()
                },
                widthFraction = PLAYER_SETTINGS_PANEL_WIDTH_FRACTION,
                maxWidth = PLAYER_SETTINGS_SHEET_MAX_WIDTH,
                restingOffsetY = PLAYER_SETTINGS_PANEL_RESTING_OFFSET_Y,
                swipeToDismissEnabled = false,
            ) { dismissPanel ->
                PlayerSettingsSheet(
                    destination = settingsDestination,
                    selectedSpeed = playbackSpeed,
                    selectedSourceId = state.currentSourceId,
                    selectedProviderId = state.selectedProviderId,
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
                    onSelectVoiceover = {
                        runPlaybackSwitch { viewModel.selectVoiceover(it) }
                    },
                    onSelectBackend = {
                        keepControlsVisible()
                        viewModel.selectBackend(it)
                    },
                    onSelectPlayer = {
                        keepControlsVisible()
                        viewModel.selectPlayer(it)
                    },
                    onSelectQuality = {
                        keepControlsVisible()
                        viewModel.selectQuality(it)
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
private fun PlayerOverlayPanel(
    onDismissRequest: () -> Unit,
    widthFraction: Float,
    maxWidth: Dp,
    restingOffsetY: Dp = 0.dp,
    swipeToDismissEnabled: Boolean = true,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    val density = LocalDensity.current
    val openedAtMs = remember { SystemClock.elapsedRealtime() }

    var animatingIn by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val exitOffsetPx = with(density) { PLAYER_OVERLAY_PANEL_EXIT_OFFSET.toPx() }
    val dismissThresholdPx = with(density) { PLAYER_PANEL_DISMISS_DRAG_THRESHOLD.toPx() }

    val scrimBaseAlpha by animateFloatAsState(
        targetValue = if (animatingIn) PLAYER_OVERLAY_SCRIM_ALPHA else 0f,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayScrimAlpha"
    )

    val panelAlpha by animateFloatAsState(
        targetValue = if (animatingIn) 1f else 0f,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayPanelAlpha"
    )

    val panelScale by animateFloatAsState(
        targetValue = if (animatingIn) 1f else 0.96f,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayPanelScale"
    )

    val panelBaseOffsetPx by animateFloatAsState(
        targetValue = if (animatingIn) 0f else exitOffsetPx,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayPanelBaseOffset"
    )

    val animatedDragOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 160),
        label = "playerOverlayDragOffset"
    )

    val dragProgress = (animatedDragOffsetPx / dismissThresholdPx).coerceIn(0f, 1f)
    val effectiveScrimAlpha = scrimBaseAlpha * (1f - dragProgress * 0.45f)
    val effectivePanelScale = panelScale * (1f - dragProgress * 0.02f)
    val effectivePanelAlpha = panelAlpha * (1f - dragProgress * 0.12f)

    LaunchedEffect(Unit) {
        animatingIn = true
    }

    fun dismissPanel() {
        if (dismissing) return
        dismissing = true
        isDragging = false
        animatingIn = false
    }

    fun finishPanelDrag() {
        if (dismissing) return

        isDragging = false
        if (dragOffsetPx >= dismissThresholdPx) {
            dismissPanel()
        } else {
            dragOffsetPx = 0f
        }
    }

    val nestedScrollConnection = remember(dismissing, dismissThresholdPx, swipeToDismissEnabled) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                consumed
                if (
                    !swipeToDismissEnabled ||
                    dismissing ||
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f
                ) {
                    return Offset.Zero
                }

                isDragging = true
                dragOffsetPx = (dragOffsetPx + available.y).coerceAtLeast(0f)
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (swipeToDismissEnabled && dragOffsetPx > 0f) {
                    val shouldDismiss =
                        dragOffsetPx >= dismissThresholdPx || available.y >= PLAYER_PANEL_DISMISS_FLING_VELOCITY
                    if (shouldDismiss) {
                        dismissPanel()
                    } else {
                        finishPanelDrag()
                    }
                    return Velocity.Zero
                }

                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(dismissing) {
        if (dismissing) {
            delay(PLAYER_OVERLAY_ANIMATION_MS.toLong())
            onDismissRequest()
        }
    }

    BackHandler(enabled = !dismissing, onBack = ::dismissPanel)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = effectiveScrimAlpha))
                .clickable(enabled = !dismissing) {
                    val canDismiss =
                        SystemClock.elapsedRealtime() - openedAtMs >= PLAYER_OVERLAY_TAP_GUARD_MS
                    if (canDismiss) {
                        dismissPanel()
                    }
                }
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .widthIn(max = maxWidth)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .offset(y = restingOffsetY)
                .then(
                    if (swipeToDismissEnabled) {
                        Modifier.nestedScroll(nestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    alpha = effectivePanelAlpha
                    scaleX = effectivePanelScale
                    scaleY = effectivePanelScale
                    translationY = panelBaseOffsetPx + animatedDragOffsetPx
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // Consume clicks inside dialog so scrim does not receive them.
                },
            shape = RoundedCornerShape(20.dp),
            color = PLAYER_SHEET_COLOR,
            contentColor = Color.White,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (swipeToDismissEnabled) {
                    PlayerOverlayHandle(
                        onDragDelta = { deltaY ->
                            if (dismissing) return@PlayerOverlayHandle

                            isDragging = true
                            dragOffsetPx = (dragOffsetPx + deltaY).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dismissing) return@PlayerOverlayHandle

                            finishPanelDrag()
                        }
                    )
                } else {
                    SpacerBox(12.dp)
                }

                content(::dismissPanel)
            }
        }
    }
}

@Composable
private fun PlayerOverlayHandle(
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.28f))
        )
    }
}

@Composable
private fun PlayerSkipSegmentOverlay(
    countdownSeconds: Int,
    autoSkipEnabled: Boolean,
    onSkipClick: () -> Unit,
    onWatchClick: () -> Unit,
) {
    val skipLabel = stringResource(R.string.watch_player_skip)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (autoSkipEnabled) {
                PlayerSkipSegmentButton(
                text = stringResource(R.string.watch_player_watch),
                onClick = onWatchClick,
                primary = false,
            )
        }
        PlayerSkipSegmentButton(
            text = "$skipLabel (${countdownSeconds.coerceIn(0, SKIP_SEGMENT_COUNTDOWN_SECONDS)})",
            onClick = onSkipClick,
            primary = true,
        )
    }
}

@Composable
private fun PlayerSkipSegmentButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (primary) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.58f),
        contentColor = if (primary) Color.Black else Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerTopOverlay(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    playlistEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    1f to Color.Transparent
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        WatchBackButton(
            onBackClick = onBackClick,
            darkStyle = true,
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (playlistEnabled) {
            AppFilledIconButton(
                onClick = onPlaylistClick,
                modifier = Modifier.align(Alignment.TopEnd),
                style = AppFilledIconButtonStyle.DarkOverlay,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    contentDescription = stringResource(R.string.watch_player_playlist),
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 92.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title.preventTrailingOrphanWrap(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun String.preventTrailingOrphanWrap(): String {
    val trimmed = trim()
    val lastSpaceIndex = trimmed.indexOfLast { it.isWhitespace() }
    if (lastSpaceIndex <= 0 || lastSpaceIndex >= trimmed.lastIndex) return this
    return buildString(trimmed.length) {
        append(trimmed, 0, lastSpaceIndex)
        append('\u00A0')
        append(trimmed, lastSpaceIndex + 1, trimmed.length)
    }
}

@Composable
private fun PlayerSettingsSheet(
    destination: PlayerSettingsDestination,
    selectedSpeed: Float,
    selectedSourceId: String,
    selectedProviderId: String?,
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
    onSelectBackend: (String?) -> Unit,
    onSelectPlayer: (String?) -> Unit,
    onSelectQuality: (String?) -> Unit,
    onAutoSkipSegmentsChange: (Boolean) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
) {
    val speedValues = PLAYBACK_SPEEDS.map { speed ->
        SelectableValue(
            id = speed.toString(),
            label = if (speed == 1f) "1x" else "${speed}x",
            selected = selectedSpeed == speed,
            onClick = { onSelectSpeed(speed) },
        )
    }
    val backendValues = options.backends.map { backend ->
        SelectableValue(
            id = backend.providerId,
            label = backend.providerName,
            selected = selectedProviderId == backend.providerId || (selectedProviderId == null && options.backends.firstOrNull()?.providerId == backend.providerId),
            onClick = { onSelectBackend(backend.providerId) },
        )
    }
    val voiceoverValues = options.voiceovers.map { source ->
        SelectableValue(
            id = source.sourceId,
            label = source.title.ifBlank { source.sourceId },
            description = source.qualityLabel,
            selected = selectedSourceId == source.sourceId,
            onClick = { onSelectVoiceover(source) },
        )
    }
    val playerValues = options.links.mapNotNull { link ->
        val name = link.playerName ?: return@mapNotNull null
        SelectableValue(
            id = name,
            label = name,
            selected = selectedPlayerName == name || (selectedPlayerName == null && options.links.firstOrNull()?.playerName == name),
            onClick = { onSelectPlayer(name) },
        )
    }.distinctBy { it.id }
    val qualityValues = (options.links.mapNotNull { it.qualityLabel } + availableQualityLabels)
        .mapNotNull { it.trim().takeIf(String::isNotBlank) }
        .distinct()
        .sortedByDescending { value -> value.filter(Char::isDigit).toIntOrNull() ?: 0 }
        .map { quality ->
            SelectableValue(
                id = quality,
                label = quality,
                selected = selectedQualityLabel == quality,
                onClick = { onSelectQuality(quality) },
            )
        }
    val rootEntries = buildList {
        if (voiceoverValues.size > 1) {
            add(
                PlayerSettingsEntryItem(
                    id = PlayerSettingsDestination.Voiceover.name,
                    title = stringResource(R.string.watch_player_settings_voiceover),
                    value = voiceoverValues.firstSelectedLabelOrDefault(),
                    onClick = { onNavigate(PlayerSettingsDestination.Voiceover) },
                )
            )
        }
        if (qualityValues.size > 1) {
            add(
                PlayerSettingsEntryItem(
                    id = PlayerSettingsDestination.Quality.name,
                    title = stringResource(R.string.watch_player_settings_quality),
                    value = qualityValues.firstSelectedLabelOrDefault(),
                    onClick = { onNavigate(PlayerSettingsDestination.Quality) },
                )
            )
        }
        add(
            PlayerSettingsEntryItem(
                id = PlayerSettingsDestination.Speed.name,
                title = stringResource(R.string.watch_player_settings_speed),
                value = speedValues.firstSelectedLabelOrDefault(defaultLabel = "1x"),
                onClick = { onNavigate(PlayerSettingsDestination.Speed) },
            )
        )
        add(
            PlayerSettingsEntryItem(
                id = "auto_skip",
                title = stringResource(R.string.watch_player_settings_auto_skip),
                value = if (autoSkipSegments) {
                    stringResource(R.string.watch_player_settings_on)
                } else {
                    stringResource(R.string.watch_player_settings_off)
                },
                onClick = { onAutoSkipSegmentsChange(!autoSkipSegments) },
            )
        )
        add(
            PlayerSettingsEntryItem(
                id = "auto_play_next",
                title = stringResource(R.string.watch_player_settings_auto_play_next),
                value = if (autoPlayNextEpisode) {
                    stringResource(R.string.watch_player_settings_on)
                } else {
                    stringResource(R.string.watch_player_settings_off)
                },
                onClick = { onAutoPlayNextEpisodeChange(!autoPlayNextEpisode) },
            )
        )
        if (backendValues.isNotEmpty()) {
            add(
                PlayerSettingsEntryItem(
                    id = PlayerSettingsDestination.Backend.name,
                    title = stringResource(R.string.watch_player_settings_backend),
                    value = backendValues.firstSelectedLabelOrDefault(),
                    onClick = { onNavigate(PlayerSettingsDestination.Backend) },
                )
            )
        }
        if (playerValues.isNotEmpty()) {
            add(
                PlayerSettingsEntryItem(
                    id = PlayerSettingsDestination.Player.name,
                    title = stringResource(R.string.watch_player_settings_player),
                    value = playerValues.firstSelectedLabelOrDefault(),
                    onClick = { onNavigate(PlayerSettingsDestination.Player) },
                )
            )
        }
    }

    BackHandler(enabled = destination != PlayerSettingsDestination.Root) {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { playerSettingsPageTransition() },
            label = "PlayerSettingsPage",
        ) { targetDestination ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (targetDestination != PlayerSettingsDestination.Root) {
                    PlayerSettingsHeader(
                        title = stringResource(targetDestination.titleResId),
                        showBack = true,
                        onBack = onBack,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = PLAYER_SETTINGS_PANEL_MAX_HEIGHT),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    userScrollEnabled = true,
                ) {
                    when (targetDestination) {
                        PlayerSettingsDestination.Root -> {
                            items(rootEntries, key = PlayerSettingsEntryItem::id) { entry ->
                                PlayerSettingsEntry(
                                    title = entry.title,
                                    value = entry.value,
                                    onClick = entry.onClick,
                                )
                            }
                        }

                        PlayerSettingsDestination.Speed -> {
                            items(speedValues, key = SelectableValue::id) { value ->
                                PlayerSettingsChoiceRow(value = value)
                            }
                        }

                        PlayerSettingsDestination.Voiceover -> {
                            items(voiceoverValues, key = SelectableValue::id) { value ->
                                PlayerSettingsChoiceRow(value = value)
                            }
                        }

                        PlayerSettingsDestination.Backend -> {
                            items(backendValues, key = SelectableValue::id) { value ->
                                PlayerSettingsChoiceRow(value = value)
                            }
                        }

                        PlayerSettingsDestination.Player -> {
                            items(playerValues, key = SelectableValue::id) { value ->
                                PlayerSettingsChoiceRow(value = value)
                            }
                        }

                        PlayerSettingsDestination.Quality -> {
                            items(qualityValues, key = SelectableValue::id) { value ->
                                PlayerSettingsChoiceRow(value = value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, end = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            WatchBackButton(
                onBackClick = onBack,
                darkStyle = true,
            )
        } else {
            SpacerBox(8.dp)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerSettingsEntry(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.52f),
            )
        }
    }
}

@Composable
private fun PlayerSettingsChoiceRow(
    value: SelectableValue,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = value.onClick),
        color = if (value.selected) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = if (value.selected) 1f else 0.86f),
                fontWeight = if (value.selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            value.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.54f),
                    maxLines = 1,
                )
            }
            if (value.selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<PlayerSettingsDestination>.playerSettingsPageTransition(): ContentTransform {
    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
    return (
        slideInHorizontally(animationSpec = tween(180)) { width -> direction * width / 5 } +
            fadeIn(animationSpec = tween(140))
        ).togetherWith(
            slideOutHorizontally(animationSpec = tween(180)) { width -> -direction * width / 5 } +
                fadeOut(animationSpec = tween(120))
        ).using(SizeTransform(clip = false))
}

@Composable
private fun SpacerBox(size: Dp) {
    Box(modifier = Modifier.size(size))
}

private data class SelectableValue(
    val id: String,
    val label: String,
    val description: String? = null,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private data class PlayerSettingsEntryItem(
    val id: String,
    val title: String,
    val value: String,
    val onClick: () -> Unit,
)

private fun List<SelectableValue>.firstSelectedLabelOrDefault(defaultLabel: String = first().label): String {
    return firstOrNull { it.selected }?.label ?: defaultLabel
}

private enum class PlayerSettingsDestination(@param:StringRes val titleResId: Int) {
    Root(R.string.watch_player_settings_root),
    Speed(R.string.watch_player_settings_speed),
    Voiceover(R.string.watch_player_settings_voiceover),
    Backend(R.string.watch_player_settings_backend),
    Player(R.string.watch_player_settings_player),
    Quality(R.string.watch_player_settings_quality),
}

@Composable
private fun PlayerBottomOverlay(
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    videoScaleMode: VideoScaleMode,
    onVideoScaleModeClick: () -> Unit,
    settingsEnabled: Boolean,
    onSettingsClick: () -> Unit,
    pictureInPictureEnabled: Boolean,
    onPictureInPictureClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f)
                )
            )
            .navigationBarsPadding()
            .padding(
                start = PLAYER_CONTROLS_HORIZONTAL_PADDING,
                end = PLAYER_CONTROLS_HORIZONTAL_PADDING,
                top = 8.dp,
                bottom = 18.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            PlayerTimeline(
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                sliderPositionMs = sliderPositionMs,
                onSeekPreview = onSliderValueChange,
                onSeekFinished = onSliderValueChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 0.dp)
                    .offset(y = (-3).dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${formatDuration(sliderPositionMs)} / ${formatDuration(durationMs)}",
                    modifier = Modifier.padding(top = 1.dp),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppFilledIconButton(
                        onClick = onVideoScaleModeClick,
                        modifier = Modifier.size(46.dp),
                        style = AppFilledIconButtonStyle.DarkOverlay,
                    ) {
                        Icon(
                            painter = painterResource(videoScaleMode.iconResId()),
                            contentDescription = stringResource(videoScaleMode.contentDescriptionResId()),
                            tint = Color.White,
                        )
                    }
                    AppFilledIconButton(
                        onClick = onLockClick,
                        modifier = Modifier.size(46.dp),
                        style = AppFilledIconButtonStyle.DarkOverlay,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.watch_player_lock),
                            tint = Color.White,
                        )
                    }
                    AppFilledIconButton(
                        onClick = onPictureInPictureClick,
                        enabled = pictureInPictureEnabled,
                        modifier = Modifier.size(46.dp),
                        style = AppFilledIconButtonStyle.DarkOverlay,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_picture_in_picture_24),
                            contentDescription = stringResource(R.string.watch_player_picture_in_picture),
                            tint = Color.White,
                        )
                    }
                    AppFilledIconButton(
                        onClick = onSettingsClick,
                        enabled = settingsEnabled,
                        modifier = Modifier.size(46.dp),
                        style = AppFilledIconButtonStyle.DarkOverlay,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.watch_player_settings),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

    }
}

private fun PlayerView.applyVideoScale(mode: VideoScaleMode, videoAspectRatio: Float) {
    val textureView = videoSurfaceView as? TextureView ?: return
    if (!textureView.isLaidOut || textureView.width == 0 || textureView.height == 0) {
        textureView.doOnLayout { applyVideoScale(mode, videoAspectRatio) }
        return
    }

    val containerAspectRatio = textureView.width.toFloat() / textureView.height
    val aspectRatioFactor = videoAspectRatio / containerAspectRatio
    val (scaleX, scaleY) = when (mode) {
        VideoScaleMode.FIT -> min(1f, aspectRatioFactor) to min(1f, 1f / aspectRatioFactor)
        VideoScaleMode.CROP -> max(1f, aspectRatioFactor) to max(1f, 1f / aspectRatioFactor)
        VideoScaleMode.STRETCH -> 1f to 1f
    }
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

private fun VideoScaleMode.iconResId(): Int = when (this) {
    VideoScaleMode.FIT -> R.drawable.ic_player_fit_to_screen_24
    VideoScaleMode.CROP -> R.drawable.ic_player_settings_overscan_24
    VideoScaleMode.STRETCH -> R.drawable.ic_player_aspect_ratio_24
}

@StringRes
private fun VideoScaleMode.contentDescriptionResId(): Int = when (this) {
    VideoScaleMode.FIT -> R.string.watch_player_video_scale_fit
    VideoScaleMode.CROP -> R.string.watch_player_video_scale_crop
    VideoScaleMode.STRETCH -> R.string.watch_player_video_scale_stretch
}

@Composable
private fun AutoHideVisibilityEffect(
    enabled: Boolean,
    visible: Boolean,
    interactionTick: Int,
    blocked: Boolean,
    onHide: () -> Unit,
) {
    LaunchedEffect(enabled, visible, interactionTick, blocked) {
        if (!enabled || !visible || blocked) return@LaunchedEffect
        delay(PLAYER_CONTROLS_AUTO_HIDE_DELAY_MS)
        onHide()
    }
}

@Composable
private fun PlayerUnlockButton(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.watch_player_unlock),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    onTogglePlay: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerControlButton(
            painter = painterResource(R.drawable.ic_player_media_skip_previous_24),
            enabled = hasPreviousEpisode,
            onClick = onPreviousEpisode,
        )
        AppFilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(PLAYER_CENTER_PRIMARY_BUTTON_SIZE),
            style = AppFilledIconButtonStyle.DarkOverlay,
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) {
                        R.drawable.ic_player_media_pause_24
                    } else {
                        R.drawable.ic_player_media_play_arrow_24
                    }
                ),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
        }
        PlayerControlButton(
            painter = painterResource(R.drawable.ic_player_media_skip_next_24),
            enabled = hasNextEpisode,
            onClick = onNextEpisode,
        )
    }
}

@Composable
private fun PlayerTimeline(
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val playedFraction = (sliderPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val playedColor = Color(0xFFE53935)
    val bufferedColor = playedColor.copy(alpha = 0.34f)
    val trackColor = Color.White.copy(alpha = 0.18f)
    val thumbRadiusPx = with(LocalDensity.current) { PLAYER_TIMELINE_THUMB_RADIUS.toPx() }

    fun seekFromX(x: Float) {
        val fraction = (x / trackWidthPx).coerceIn(0f, 1f)
        onSeekPreview((safeDuration * fraction).toLong())
    }

    Box(
        modifier = modifier
            .height(18.dp)
            .pointerInput(safeDuration, trackWidthPx) {
                detectTapGestures(
                    onTap = { offset ->
                        seekFromX(offset.x)
                        onSeekFinished()
                    }
                )
            }
            .pointerInput(safeDuration, trackWidthPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        seekFromX(offset.x)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        seekFromX(change.position.x)
                    },
                    onDragEnd = onSeekFinished,
                    onDragCancel = onSeekFinished,
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PLAYER_TIMELINE_TRACK_HEIGHT)
                .clip(RoundedCornerShape(999.dp))
                .background(trackColor)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(bufferedColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(playedColor)
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = (trackWidthPx * playedFraction) - thumbRadiusPx
                }
                .size(PLAYER_TIMELINE_THUMB_SIZE)
                .clip(CircleShape)
                .background(playedColor)
        )
    }
}

@Composable
private fun PlayerControlButton(
    painter: Painter,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AppFilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(52.dp),
        style = AppFilledIconButtonStyle.DarkOverlay,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun PlaylistBottomSheet(
    currentEpisodeId: String,
    episodes: List<WatchEpisode>,
    onEpisodeClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PLAYER_PLAYLIST_SHEET_MAX_HEIGHT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = UiDimens.ScreenPadding,
                top = 4.dp,
                end = UiDimens.ScreenPadding,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(episodes, key = WatchEpisode::id) { episode ->
                EpisodeSheetRow(
                    episode = episode,
                    selected = episode.id == currentEpisodeId,
                    onClick = { onEpisodeClick(episode.id) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeSheetRow(
    episode: WatchEpisode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = if (selected) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.92f)
    }
    val subtitleColor = if (selected) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(18.dp)),
        color = if (selected) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.03f)
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = buildEpisodeTitle(episode),
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            episode.title?.takeIf(String::isNotBlank)?.let { subtitle ->
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
private fun buildEpisodeTitle(episode: WatchEpisode): String {
    val number = if (episode.number % 1.0 == 0.0) {
        episode.number.toInt().toString()
    } else {
        episode.number.toString()
    }
    return stringResource(R.string.watch_episode_number, number)
}

@Composable
private fun currentEpisodeSubtitle(state: PlayerUiState): String {
    val playbackTitle = state.playback?.episodeTitle.orEmpty().trim()
    if (playbackTitle.isNotBlank()) {
        return localizedEpisodeTitle(playbackTitle)
    }
    val currentEpisode = state.episodes.firstOrNull { it.id == state.currentEpisodeId }
        ?: return ""
    return buildEpisodeTitle(currentEpisode)
}

@Composable
private fun localizedEpisodeTitle(title: String): String {
    val fallbackEpisodeNumber = Regex("""^Episode\s+(.+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(title.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)
    return if (fallbackEpisodeNumber != null) {
        stringResource(R.string.watch_episode_number, fallbackEpisodeNumber)
    } else {
        title
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) {
        return "00:00"
    }
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun buildSkipSegmentKey(
    episodeId: String,
    segment: PlaybackSegment,
): String = "$episodeId:${segment.type}:${segment.startMs}:${segment.endMs}"

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
private const val SEEK_GESTURE_THRESHOLD_MS = 2_000L
private const val SEEK_GESTURE_TOUCH_SLOP_MULTIPLIER = 1.8f
private const val SEEK_GESTURE_HORIZONTAL_DOMINANCE = 1.35f
private const val SEEK_GESTURE_FULL_WIDTH_MS = 90_000L
private const val WATCHED_SECONDS_TRACKING_MAX_DELTA_MS = 2_500L
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
private val PLAYER_SHEET_COLOR = Color(0xFF121212)
private val PLAYER_SETTINGS_SHEET_MAX_WIDTH = 460.dp
private val PLAYER_SETTINGS_PANEL_MAX_HEIGHT = 300.dp
private val PLAYER_SETTINGS_PANEL_RESTING_OFFSET_Y = 0.dp
private const val PLAYER_SETTINGS_PANEL_WIDTH_FRACTION = 0.68f
private val PLAYER_PLAYLIST_PANEL_MAX_WIDTH = 380.dp
private const val PLAYER_PLAYLIST_PANEL_WIDTH_FRACTION = 0.58f
private val PLAYER_PLAYLIST_SHEET_MAX_HEIGHT = 360.dp
private val PLAYER_OVERLAY_PANEL_EXIT_OFFSET = 40.dp
private val PLAYER_PANEL_DISMISS_DRAG_THRESHOLD = 72.dp
private const val PLAYER_PANEL_DISMISS_FLING_VELOCITY = 900f
private const val PLAYER_OVERLAY_ANIMATION_MS = 220
private const val PLAYER_VIDEO_SCALE_ANIMATION_DURATION_MS = 220L
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private const val PLAYER_OVERLAY_TAP_GUARD_MS = 120L
private const val PLAYER_OVERLAY_SCRIM_ALPHA = 0.48f
private val PLAYER_CONTROLS_HORIZONTAL_PADDING = 24.dp
private val PLAYER_CENTER_PRIMARY_BUTTON_SIZE = 72.dp
private val PLAYER_TIMELINE_TRACK_HEIGHT = 4.dp
private val PLAYER_TIMELINE_THUMB_SIZE = 8.dp
private val PLAYER_TIMELINE_THUMB_RADIUS = 4.dp
private val PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun String?.shortUrl(): String {
    if (this.isNullOrBlank()) return "null"
    return substringBefore('?').substringAfterLast('/')
}

private fun String?.safeHost(): String {
    if (this.isNullOrBlank()) return "unknown"
    return runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "unknown"
}

private fun Map<String, String>.safeHeaderNames(): String {
    if (isEmpty()) return "[]"
    return keys
        .filter(String::isNotBlank)
        .sorted()
        .joinToString(prefix = "[", postfix = "]")
}

private fun buildSeekDeltaLabel(deltaMs: Long): String {
    val sign = if (deltaMs >= 0L) "+" else "-"
    return sign + formatDuration(kotlin.math.abs(deltaMs))
}
