package org.akkirrai.hibiki.shared.app.shell.settings

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState

@Composable
internal fun HibikiDiscordAuthOverlayLayer(
    isOpen: Boolean,
    controller: DiscordRpcController?,
    state: DiscordRpcUiState,
    pendingToken: String?,
    onPendingTokenChange: (String?) -> Unit,
    onBrowserSignIn: (((String) -> Unit) -> Unit),
    onClose: () -> Unit,
) {
    if (isOpen && controller != null) {
        HibikiDiscordAuthOverlay(
            controller = controller,
            state = state,
            pendingToken = pendingToken,
            onPendingTokenChange = onPendingTokenChange,
            onBrowserSignIn = onBrowserSignIn,
            onClose = onClose,
        )
    }
}
