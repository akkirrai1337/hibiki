package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared player surface/control composition around a platform media surface. */
@Composable
fun AppPlayerChrome(
    surface: @Composable () -> Unit,
    controlsEnabled: Boolean,
    controls: @Composable () -> Unit,
    // Drawn above the surface but below controls/overlayContent, so a loading indicator dims
    // only the video -- the controls and any settings/playlist panel on top of it stay fully lit.
    loadingContent: @Composable () -> Unit = {},
    overlayContent: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        surface()
        loadingContent()
        if (controlsEnabled) {
            controls()
        }
        overlayContent()
    }
}
