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
internal fun IosComposePlayerControls(
    session: IosPlayerSession,
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
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

    DisposableEffect(session) {
        session.transport.play()
        onDispose { session.release() }
    }
    LaunchedEffect(session, isSeeking) {
        while (true) {
            positionMs = session.transport.positionMs()
            durationMs = session.transport.durationMs()
            bufferedPositionMs = session.transport.bufferedPositionMs()
            isPlaying = session.transport.rate() > 0f
            if (!isSeeking) sliderPositionMs = positionMs
            delay(IosPlayerPositionPollMillis)
        }
    }
    AppAutoHideVisibilityEffect(
        enabled = true,
        visible = controlsVisible,
        interactionTick = interactionTick,
        blocked = isSeeking,
        hideDelayMillis = IosPlayerControlsAutoHideMillis,
        onHide = { controlsVisible = false },
    )
    AppPlayerControlsLayer(
        visible = controlsVisible,
        title = playback.animeTitle,
        subtitle = appText(AppTextKey.PlayerEpisodeNumber).replace("%s", formatEpisodeNumber(context.episodeNumber)),
        playlistEnabled = false,
        onBackClick = onBack,
        backContentDescription = appText(AppTextKey.Back),
        onPlaylistClick = {},
        hasPreviousEpisode = false,
        hasNextEpisode = false,
        isPlaying = isPlaying,
        seekOverlayActive = false,
        onTogglePlay = {
            keepControlsVisible()
            if (isPlaying) session.transport.pause() else session.transport.play()
            isPlaying = !isPlaying
        },
        onPreviousEpisode = {},
        onNextEpisode = {},
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
            session.transport.seekToMs(sliderPositionMs)
            isSeeking = false
            keepControlsVisible()
        },
        scaleMode = session.scaleMode,
        scaleContentDescription = null,
        onScaleClick = { session.scaleMode = session.scaleMode.next(); keepControlsVisible() },
        onLockClick = {},
        lockContentDescription = null,
        pictureInPictureEnabled = false,
        onPictureInPictureClick = {},
        pictureInPictureContentDescription = null,
        onSettingsClick = { keepControlsVisible() },
        settingsContentDescription = null,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = {
                controlsVisible = !controlsVisible
                if (controlsVisible) interactionTick += 1
            })
        },
    )
}

private const val IosPlayerPositionPollMillis = 500L
private const val IosPlayerControlsAutoHideMillis = 4_000L
