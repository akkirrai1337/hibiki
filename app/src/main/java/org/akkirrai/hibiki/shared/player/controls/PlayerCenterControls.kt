package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.component.controls.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.controls.AppFilledIconButtonStyle

@Composable
fun AppPlayerCenterControls(
    visible: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    onTogglePlay: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    previousContent: @Composable (Modifier) -> Unit = { iconModifier ->
        Icon(
            painter = painterResource(R.drawable.ic_player_media_skip_previous_24),
            contentDescription = null,
            modifier = iconModifier,
        )
    },
    isPlaying: Boolean = false,
    // While true, the primary button shows a spinner instead of play/pause -- e.g. the next
    // episode's stream is loading, so there's nothing meaningful to toggle yet.
    isLoading: Boolean = false,
    playContentDescription: String? = null,
    playContent: @Composable (Modifier) -> Unit = { iconModifier ->
        if (isLoading) {
            CircularProgressIndicator(
                modifier = iconModifier,
                color = Color.White,
                strokeWidth = PlayerLoadingIndicatorStrokeWidth,
            )
        } else {
            Icon(
                painter = painterResource(
                    if (isPlaying) {
                        R.drawable.ic_player_media_pause_24
                    } else {
                        R.drawable.ic_player_media_play_arrow_24
                    },
                ),
                contentDescription = playContentDescription,
                modifier = iconModifier,
            )
        }
    },
    nextContent: @Composable (Modifier) -> Unit = { iconModifier ->
        Icon(
            painter = painterResource(R.drawable.ic_player_media_skip_next_24),
            contentDescription = null,
            modifier = iconModifier,
        )
    },
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(90)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PlayerCenterControlsGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppPlayerControlButton(
                enabled = hasPreviousEpisode,
                onClick = onPreviousEpisode,
                iconContent = { previousContent(Modifier.size(PlayerCenterSideIconSize)) },
            )
            AppFilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(PlayerCenterPrimaryButtonSize),
                style = AppFilledIconButtonStyle.DarkOverlay,
                content = { playContent(Modifier.size(PlayerCenterPlayIconSize)) },
            )
            AppPlayerControlButton(
                enabled = hasNextEpisode,
                onClick = onNextEpisode,
                iconContent = { nextContent(Modifier.size(PlayerCenterSideIconSize)) },
            )
        }
    }
}
