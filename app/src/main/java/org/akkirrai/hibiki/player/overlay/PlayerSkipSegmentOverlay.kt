package org.akkirrai.hibiki.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppPlayerSkipSegmentOverlay(
    visible: Boolean,
    countdownSeconds: Int,
    maxCountdownSeconds: Int,
    autoSkipEnabled: Boolean,
    skipLabel: String,
    watchLabel: String,
    onSkipClick: () -> Unit,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(140)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PlayerSkipSegmentOverlayGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (autoSkipEnabled) {
                AppPlayerSkipSegmentButton(
                    text = watchLabel,
                    onClick = onWatchClick,
                    primary = false,
                )
            }
            AppPlayerSkipSegmentButton(
                text = "$skipLabel (${countdownSeconds.coerceIn(0, maxCountdownSeconds)})",
                onClick = onSkipClick,
                primary = true,
            )
        }
    }
}
