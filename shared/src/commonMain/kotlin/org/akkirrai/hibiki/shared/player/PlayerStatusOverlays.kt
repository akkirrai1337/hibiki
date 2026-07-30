package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared loading, error, and locked-controls status layer. */
@Composable
fun AppPlayerStatusOverlays(
    controlsLocked: Boolean,
    unlockButtonVisible: Boolean,
    unlockLabel: String,
    onUnlockClick: () -> Unit,
    unlockContentDescription: String?,
    isLoading: Boolean,
    isBuffering: Boolean,
    errorMessage: String?,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    unlockModifier: Modifier = Modifier,
) {
    AppPlayerUnlockOverlay(
        visible = controlsLocked && unlockButtonVisible,
        label = unlockLabel,
        onClick = onUnlockClick,
        contentDescription = unlockContentDescription,
        modifier = unlockModifier,
    )

    AppPlayerLoadingOverlay(
        visible = isLoading || isBuffering,
    )

    errorMessage?.let { message ->
        AppPlayerErrorOverlay(
            message = message,
            title = errorTitle,
            retryLabel = retryLabel,
            onRetry = onRetry,
        )
    }
}
