package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle

@Composable
fun AppPlayerCenterControls(
    visible: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    onTogglePlay: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    previousContent: @Composable (Modifier) -> Unit,
    playContent: @Composable (Modifier) -> Unit,
    nextContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(90)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppPlayerControlButton(
                enabled = hasPreviousEpisode,
                onClick = onPreviousEpisode,
                iconContent = { previousContent(Modifier.size(30.dp)) },
            )
            AppFilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(72.dp),
                style = AppFilledIconButtonStyle.DarkOverlay,
                content = { playContent(Modifier.size(40.dp)) },
            )
            AppPlayerControlButton(
                enabled = hasNextEpisode,
                onClick = onNextEpisode,
                iconContent = { nextContent(Modifier.size(30.dp)) },
            )
        }
    }
}
