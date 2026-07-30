package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppBackButton

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
    seekOverlayActive: Boolean,
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
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AppPlayerCenterControls(
            visible = !seekOverlayActive,
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
