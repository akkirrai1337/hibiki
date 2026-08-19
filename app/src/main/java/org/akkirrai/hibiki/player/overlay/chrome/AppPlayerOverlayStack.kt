package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
