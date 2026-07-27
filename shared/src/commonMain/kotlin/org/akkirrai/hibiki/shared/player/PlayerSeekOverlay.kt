package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerSeekOverlay(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    AppPlayerFeedbackOverlay(
        visible = visible,
        label = label,
        horizontalPadding = 22.dp,
        modifier = modifier,
    )
}
