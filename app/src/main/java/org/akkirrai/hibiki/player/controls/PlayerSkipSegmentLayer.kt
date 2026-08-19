package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
