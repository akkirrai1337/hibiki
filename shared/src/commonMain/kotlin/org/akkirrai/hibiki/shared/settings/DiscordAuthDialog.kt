package org.akkirrai.hibiki.shared.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch

@Composable
fun AppDiscordAuthDialog(
    initialToken: String,
    isSignedIn: Boolean,
    statusText: String,
    isChecking: Boolean,
    icon: ImageVector,
    title: String,
    manualTokenLabel: String,
    invalidTokenLabel: String,
    disconnectLabel: String,
    browserSignInLabel: String,
    cancelLabel: String,
    applyLabel: String,
    onBrowserSignIn: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onAuthenticate: suspend (String) -> Result<*>,
) {
    val scope = rememberCoroutineScope()
    var manualToken by remember(initialToken) { mutableStateOf(initialToken) }
    var manualTokenFailed by remember { mutableStateOf(false) }

    AppDiscordAuthDialogSurface(
        onDismissRequest = onDismiss,
        headerContent = {
            AppDiscordAuthDialogHeader(icon = icon, title = title, statusText = statusText)
        },
        tokenContent = {
            AppDiscordAuthTokenCard(
                icon = icon,
                manualToken = manualToken,
                onManualTokenChange = {
                    manualToken = it
                    manualTokenFailed = false
                },
                manualTokenLabel = manualTokenLabel,
                invalidTokenLabel = invalidTokenLabel,
                manualTokenFailed = manualTokenFailed,
                isChecking = isChecking,
                isSignedIn = isSignedIn,
                disconnectLabel = disconnectLabel,
                browserSignInLabel = browserSignInLabel,
                onDisconnect = onDisconnect,
                onBrowserSignIn = onBrowserSignIn,
            )
        },
        actionsContent = {
            AppDiscordAuthDialogActions(
                cancelLabel = cancelLabel,
                applyLabel = applyLabel,
                cancelEnabled = !isChecking,
                applyEnabled = manualToken.isNotBlank() && !isChecking,
                onCancel = onDismiss,
                onApply = {
                    scope.launch {
                        onAuthenticate(manualToken)
                            .onSuccess { onDismiss() }
                            .onFailure { manualTokenFailed = true }
                    }
                },
            )
        },
    )
}
