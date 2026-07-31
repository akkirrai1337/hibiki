package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
fun AppPlaybackControls(
    transport: PlaybackTransport,
    playback: PlaybackStream,
    context: PlaybackContext,
    scaleMode: VideoScaleMode,
    onScaleClick: () -> Unit,
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
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var sliderPositionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

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
            positionMs = transport.positionMs()
            durationMs = transport.durationMs()
            bufferedPositionMs = transport.bufferedPositionMs()
            isPlaying = transport.rate() > 0f
            if (!isSeeking) sliderPositionMs = positionMs
            delay(AppPlaybackPositionPollMillis)
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
        seekOverlayActive = false,
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
        scaleContentDescription = null,
        onScaleClick = { onScaleClick(); keepControlsVisible() },
        onLockClick = onLockClick,
        lockContentDescription = lockContentDescription,
        pictureInPictureEnabled = pictureInPictureEnabled,
        onPictureInPictureClick = onPictureInPictureClick,
        pictureInPictureContentDescription = pictureInPictureContentDescription,
        onSettingsClick = { onSettingsClick(); keepControlsVisible() },
        settingsContentDescription = settingsContentDescription,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = {
                controlsVisible = !controlsVisible
                if (controlsVisible) interactionTick += 1
            })
        },
    )
}

private const val AppPlaybackPositionPollMillis = 500L
private const val AppPlaybackControlsAutoHideMillis = 4_000L
