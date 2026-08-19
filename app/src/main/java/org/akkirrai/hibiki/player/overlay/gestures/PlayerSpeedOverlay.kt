package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppPlayerSpeedOverlay(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    AppPlayerFeedbackOverlay(
        visible = visible,
        label = label,
        horizontalPadding = PlayerSpeedOverlayHorizontalPadding,
        modifier = modifier,
    )
}
