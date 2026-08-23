package org.akkirrai.hibiki.player

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
    overlayContent: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        surface()
        if (controlsEnabled) {
            controls()
        }
        overlayContent()
    }
}

/** Shared z-order for player overlays around a platform playback surface. */
@Composable
fun AppPlayerOverlayStack(
    lockState: PlayerLockState,
    unlockLabel: String,
    onUnlock: () -> Unit,
    unlockModifier: Modifier,
    includeSystemBottomInset: Boolean,
    panelContent: @Composable () -> Unit,
) {
    AppPlayerLockOverlay(
        state = lockState,
        label = unlockLabel,
        onUnlock = onUnlock,
        modifier = unlockModifier,
        includeSystemBottomInset = includeSystemBottomInset,
    )
    panelContent()
}
