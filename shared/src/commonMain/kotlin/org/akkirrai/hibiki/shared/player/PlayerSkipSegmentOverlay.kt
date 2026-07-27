package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerSkipSegmentOverlay(
    countdownSeconds: Int,
    maxCountdownSeconds: Int,
    autoSkipEnabled: Boolean,
    skipLabel: String,
    watchLabel: String,
    onSkipClick: () -> Unit,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
