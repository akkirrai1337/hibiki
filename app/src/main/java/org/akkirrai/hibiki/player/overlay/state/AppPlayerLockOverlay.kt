package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Common player lock overlay orchestration shared by every playback host. */
@Composable
fun AppPlayerLockOverlay(
    state: PlayerLockState,
    label: String,
    onUnlock: () -> Unit,
    modifier: Modifier,
    includeSystemBottomInset: Boolean,
) {
    AppPlayerUnlockOverlay(
        visible = state.isLocked && state.isUnlockButtonVisible,
        label = label,
        onClick = onUnlock,
        contentDescription = null,
        modifier = modifier,
        includeSystemBottomInset = includeSystemBottomInset,
    )
}
