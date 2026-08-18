package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.navigation.AppBackButton

/** The shared top, center, and bottom controls of the player. */
@Composable
fun AppPlayerControlsLayer(
    visible: Boolean,
    title: String,
    subtitle: String,
    playlistEnabled: Boolean,
    onBackClick: () -> Unit,
    backContentDescription: String?,
    onPlaylistClick: () -> Unit,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    isPlaying: Boolean,
    // True while a transient gesture overlay (seek delta, hold-for-2x) is showing -- the center
    // play/pause + episode cluster hides so it doesn't sit underneath that overlay.
    centerControlsSuppressed: Boolean,
    onTogglePlay: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    positionLabel: String,
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    scaleMode: VideoScaleMode,
    scaleContentDescription: String?,
    onScaleClick: () -> Unit,
    onLockClick: () -> Unit,
    lockContentDescription: String?,
    pictureInPictureEnabled: Boolean,
    onPictureInPictureClick: () -> Unit,
    pictureInPictureContentDescription: String?,
    onSettingsClick: () -> Unit,
    settingsContentDescription: String?,
    topContentInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    AppPlayerControlsOverlay(
        visible = visible,
        modifier = modifier,
    ) {
        AppPlayerTopOverlay(
            title = title,
            subtitle = subtitle,
            playlistEnabled = playlistEnabled,
            backContent = {
                AppBackButton(
                    onClick = onBackClick,
                    contentDescription = backContentDescription,
                )
            },
            playlistContent = {
                AppPlayerPlaylistButton(
                    onClick = onPlaylistClick,
                    contentDescription = null,
                )
            },
            topContentInset = topContentInset,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AppPlayerCenterControls(
            visible = !centerControlsSuppressed,
            hasPreviousEpisode = hasPreviousEpisode,
            hasNextEpisode = hasNextEpisode,
            onTogglePlay = onTogglePlay,
            onPreviousEpisode = onPreviousEpisode,
            onNextEpisode = onNextEpisode,
            isPlaying = isPlaying,
            modifier = Modifier.align(Alignment.Center),
        )

        AppPlayerBottomOverlay(
            positionLabel = positionLabel,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            sliderPositionMs = sliderPositionMs,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            controlsContent = {
                AppPlayerActionControls(
                    onScaleClick = onScaleClick,
                    scaleMode = scaleMode,
                    scaleContentDescription = scaleContentDescription,
                    onLockClick = onLockClick,
                    lockContentDescription = lockContentDescription,
                    pictureInPictureEnabled = pictureInPictureEnabled,
                    onPictureInPictureClick = onPictureInPictureClick,
                    pictureInPictureContentDescription = pictureInPictureContentDescription,
                    onSettingsClick = onSettingsClick,
                    settingsContentDescription = settingsContentDescription,
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
