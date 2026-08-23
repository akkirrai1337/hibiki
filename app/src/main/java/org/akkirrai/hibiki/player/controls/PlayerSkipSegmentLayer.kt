package org.akkirrai.hibiki.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppPlayerSkipSegmentLayer(
    visible: Boolean,
    controlsVisible: Boolean,
    countdownSeconds: Int,
    maxCountdownSeconds: Int,
    autoSkipEnabled: Boolean,
    skipLabel: String,
    watchLabel: String,
    onSkipClick: () -> Unit,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPlayerSkipSegmentOverlay(
        visible = visible,
        modifier = modifier
            .padding(
                end = PlayerSkipSegmentEndPadding,
                bottom = if (controlsVisible) {
                    PlayerSkipSegmentControlsBottomPadding
                } else {
                    PlayerSkipSegmentBottomPadding
                },
            ),
        countdownSeconds = countdownSeconds,
        maxCountdownSeconds = maxCountdownSeconds,
        autoSkipEnabled = autoSkipEnabled,
        skipLabel = skipLabel,
        watchLabel = watchLabel,
        onSkipClick = onSkipClick,
        onWatchClick = onWatchClick,
    )
}

@Composable
private fun AppPlayerSkipSegmentOverlay(
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

@Composable
private fun AppPlayerSkipSegmentButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(PlayerSkipSegmentButtonCornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PlayerSkipSegmentButtonCornerRadius),
        color = if (primary) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.58f),
        contentColor = if (primary) Color.Black else Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = PlayerSkipSegmentButtonHorizontalPadding,
                vertical = PlayerSkipSegmentButtonVerticalPadding,
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
