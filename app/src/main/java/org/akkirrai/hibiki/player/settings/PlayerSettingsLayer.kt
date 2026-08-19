package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/** Shared settings-panel shell; platform code supplies settings content/actions. */
@Composable
fun AppPlayerSettingsLayer(
    onDismissRequest: () -> Unit,
    nowMs: () -> Long,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    // Keep the settings sheet above terminal playback states, including the
    // error surface emitted by AppPlaybackOverlayHost.
    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
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
}
