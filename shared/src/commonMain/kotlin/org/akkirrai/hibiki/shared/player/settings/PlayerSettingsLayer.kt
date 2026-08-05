package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable

/** Shared settings-panel shell; platform code supplies settings content/actions. */
@Composable
fun AppPlayerSettingsLayer(
    onDismissRequest: () -> Unit,
    nowMs: () -> Long,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    AppPlayerOverlayPanel(
        onDismissRequest = onDismissRequest,
        widthFraction = PlayerSettingsPanelWidthFraction,
        maxWidth = PlayerSettingsPanelMaxWidth,
        restingOffsetY = PlayerSettingsPanelRestingOffsetY,
        swipeToDismissEnabled = false,
        nowMs = nowMs,
        backHandler = backHandler,
        content = content,
    )
}
