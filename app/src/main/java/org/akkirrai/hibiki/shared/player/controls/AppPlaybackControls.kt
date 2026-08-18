package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
fun AppPlaybackControls(
    transport: PlaybackTransport,
    playback: PlaybackStream,
    context: PlaybackContext,
    scaleMode: VideoScaleMode,
    onScaleClick: () -> Unit,
    scaleContentDescription: String? = null,
    onBack: () -> Unit,
    playlistEnabled: Boolean = false,
    onPlaylistClick: () -> Unit = {},
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onLockClick: () -> Unit = {},
    lockContentDescription: String? = null,
    pictureInPictureEnabled: Boolean = false,
    onPictureInPictureClick: () -> Unit = {},
    pictureInPictureContentDescription: String? = null,
    onSettingsClick: () -> Unit = {},
    settingsContentDescription: String? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    playbackLoading: Boolean = false,
    topContentInset: Dp = 0.dp,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var sliderPositionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekOverlayVisible by remember { mutableStateOf(false) }
    var seekOverlayDeltaMs by remember { mutableLongStateOf(0L) }
    var seekOverlayJob by remember { mutableStateOf<Job?>(null) }
    var holdSpeedOverlayVisible by remember { mutableStateOf(false) }
    val gestureScope = rememberCoroutineScope()

    fun keepControlsVisible() {
        controlsVisible = true
        interactionTick += 1
    }

    DisposableEffect(transport) {
        transport.play()
        onDispose { transport.pause() }
    }
    LaunchedEffect(transport, isSeeking) {
        while (true) {
            val transportState = transport.readState()
            positionMs = transportState.positionMs
            durationMs = transportState.durationMs
            bufferedPositionMs = transportState.bufferedPositionMs
            isPlaying = transportState.isPlaying
            if (!isSeeking) sliderPositionMs = positionMs
            delay(AppPlaybackPositionPollMillis)
        }
    }
    LaunchedEffect(controlsVisible) {
        onControlsVisibilityChanged(controlsVisible)
    }
    fun handleDoubleTapSeek(x: Float, width: Int) {
        val direction = if (x < width / 2f) -1L else 1L
        val deltaMs = 10_000L * direction
        val target = (positionMs + deltaMs).let { candidate ->
            if (durationMs > 0L) candidate.coerceIn(0L, durationMs) else candidate.coerceAtLeast(0L)
        }
        transport.seekToMs(target)
        positionMs = target
        sliderPositionMs = target
        seekOverlayDeltaMs = deltaMs
        seekOverlayVisible = true
        seekOverlayJob?.cancel()
        seekOverlayJob = gestureScope.launch {
            delay(700L)
            seekOverlayVisible = false
        }
    }
    AppAutoHideVisibilityEffect(
        enabled = true,
        visible = controlsVisible,
        interactionTick = interactionTick,
        blocked = isSeeking,
        hideDelayMillis = AppPlaybackControlsAutoHideMillis,
        onHide = { controlsVisible = false },
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(transport) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var lastEventTimeMillis = firstDown.uptimeMillis
                    var holdSpeedEligible = true
                    var holdSpeedActive = false
                    var heldPlaybackRate = 1f

                    while (true) {
                        val event = if (holdSpeedEligible && !holdSpeedActive) {
                            val remainingMillis = (
                                viewConfiguration.longPressTimeoutMillis -
                                    (lastEventTimeMillis - firstDown.uptimeMillis)
                                ).coerceAtLeast(1L)
                            withTimeoutOrNull(remainingMillis) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            holdSpeedActive = true
                            holdSpeedOverlayVisible = true
                            heldPlaybackRate = transport.rate().takeIf { it > 0f } ?: 1f
                            transport.setRate(2f)
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
                        lastEventTimeMillis = change.uptimeMillis
                        if (!change.pressed) break

                        val movedTooFar =
                            abs(change.position.x - firstDown.position.x) >= viewConfiguration.touchSlop ||
                                abs(change.position.y - firstDown.position.y) >= viewConfiguration.touchSlop
                        if (movedTooFar) holdSpeedEligible = false
                    }

                    if (holdSpeedActive) {
                        holdSpeedOverlayVisible = false
                        transport.setRate(heldPlaybackRate)
                        return@awaitEachGesture
                    }

                    val secondDown = withTimeoutOrNull(AppPlaybackControlsDoubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (secondDown == null) {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) interactionTick += 1
                        return@awaitEachGesture
                    }

                    var secondTapCompleted = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == secondDown.id } ?: break
                        if (!change.pressed) {
                            secondTapCompleted = true
                            break
                        }
                    }
                    if (secondTapCompleted) {
                        handleDoubleTapSeek(secondDown.position.x, size.width)
                    }
                }
            },
        )
        AppPlayerControlsLayer(
            visible = controlsVisible,
            title = playback.animeTitle,
            subtitle = appText(AppTextKey.PlayerEpisodeNumber).replace("%s", formatEpisodeNumber(context.episodeNumber)),
            playlistEnabled = playlistEnabled,
            onBackClick = onBack,
            backContentDescription = appText(AppTextKey.Back),
            onPlaylistClick = onPlaylistClick,
            hasPreviousEpisode = hasPreviousEpisode,
            hasNextEpisode = hasNextEpisode,
            isPlaying = isPlaying,
            centerControlsSuppressed = seekOverlayVisible || holdSpeedOverlayVisible,
            playbackLoading = playbackLoading,
            onTogglePlay = {
                keepControlsVisible()
                if (isPlaying) transport.pause() else transport.play()
                isPlaying = !isPlaying
            },
            onPreviousEpisode = onPreviousEpisode,
            onNextEpisode = onNextEpisode,
            positionLabel = "${formatEpisodeDuration(sliderPositionMs)} / ${formatEpisodeDuration(durationMs)}",
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            sliderPositionMs = sliderPositionMs,
            onSliderValueChange = { value ->
                keepControlsVisible()
                isSeeking = true
                sliderPositionMs = value
            },
            onSliderValueChangeFinished = {
                transport.seekToMs(sliderPositionMs)
                isSeeking = false
                keepControlsVisible()
            },
            scaleMode = scaleMode,
            scaleContentDescription = scaleContentDescription,
            onScaleClick = { onScaleClick(); keepControlsVisible() },
            onLockClick = onLockClick,
            lockContentDescription = lockContentDescription,
            pictureInPictureEnabled = pictureInPictureEnabled,
            onPictureInPictureClick = onPictureInPictureClick,
            pictureInPictureContentDescription = pictureInPictureContentDescription,
            onSettingsClick = { onSettingsClick(); keepControlsVisible() },
            settingsContentDescription = settingsContentDescription,
            topContentInset = topContentInset,
            modifier = Modifier.fillMaxSize(),
        )
        AppPlayerSeekOverlay(
            visible = seekOverlayVisible,
            label = formatSeekDeltaLabel(seekOverlayDeltaMs),
            modifier = Modifier.align(Alignment.Center),
        )
        AppPlayerSpeedOverlay(
            visible = holdSpeedOverlayVisible,
            label = "2×",
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private const val AppPlaybackPositionPollMillis = 500L
private const val AppPlaybackControlsAutoHideMillis = 4_000L
private const val AppPlaybackControlsDoubleTapTimeoutMillis = 260L
