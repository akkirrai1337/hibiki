package org.akkirrai.hibiki.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.component.controls.AppFilledIconButton
import org.akkirrai.hibiki.design.component.controls.AppFilledIconButtonStyle
import org.akkirrai.hibiki.design.component.navigation.AppBackButton
import org.akkirrai.hibiki.text.preventTrailingOrphanWrap

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
    // True while the next episode's stream is loading -- the primary button shows a spinner
    // instead of play/pause.
    playbackLoading: Boolean,
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
            isLoading = playbackLoading,
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

@Composable
fun AppPlayerPlaylistButton(
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppFilledIconButton(
        onClick = onClick,
        modifier = modifier,
        style = AppFilledIconButtonStyle.DarkOverlay,
        content = iconContent,
    )
}

@Composable
fun AppPlayerPlaylistButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AppPlayerPlaylistButton(
        onClick = onClick,
        modifier = modifier,
        iconContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        },
    )
}

@Composable
fun AppPlayerTopOverlay(
    title: String,
    subtitle: String,
    playlistEnabled: Boolean,
    backContent: @Composable () -> Unit,
    playlistContent: @Composable () -> Unit,
    topContentInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    1f to Color.Transparent,
                ),
            )
            .padding(
                start = PlayerTopOverlayHorizontalPadding,
                top = PlayerTopOverlayVerticalPadding + topContentInset,
                end = PlayerTopOverlayHorizontalPadding,
                bottom = PlayerTopOverlayVerticalPadding,
            ),
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            backContent()
        }

        if (playlistEnabled) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                playlistContent()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = PlayerTopOverlayTitleHorizontalPadding,
                    vertical = PlayerTopOverlayTitleVerticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlayerTopOverlayTitleGap),
        ) {
            Text(
                text = title.preventTrailingOrphanWrap(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AppPlayerBottomOverlay(
    positionLabel: String,
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    timelineEnabled: Boolean = true,
    controlsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            )
            .padding(
                start = PlayerBottomOverlayHorizontalPadding,
                end = PlayerBottomOverlayHorizontalPadding,
                top = PlayerBottomOverlayTopPadding,
                bottom = PlayerBottomOverlayBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(PlayerBottomOverlayZeroSpacing),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PlayerBottomOverlayZeroSpacing),
        ) {
            AppPlayerTimeline(
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                sliderPositionMs = sliderPositionMs,
                onSeekPreview = onSliderValueChange,
                onSeekFinished = onSliderValueChangeFinished,
                enabled = timelineEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PlayerBottomOverlayTimelineTopPadding, bottom = PlayerBottomOverlayZeroSpacing)
                    .offset(y = PlayerBottomOverlayTimelineOffset),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = PlayerBottomOverlayControlsOffset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = positionLabel,
                    modifier = Modifier.padding(top = PlayerBottomOverlayPositionTopPadding),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                controlsContent()
            }
        }
    }
}
