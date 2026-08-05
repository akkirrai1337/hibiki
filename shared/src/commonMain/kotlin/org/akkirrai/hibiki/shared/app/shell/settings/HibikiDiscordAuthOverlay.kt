package org.akkirrai.hibiki.shared.app.shell.settings

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.ic_discord
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialog
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState
import org.akkirrai.hibiki.shared.settings.isBusy
import org.akkirrai.hibiki.shared.settings.resolveDiscordRpcStatusLabel
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HibikiDiscordAuthOverlay(
    controller: DiscordRpcController,
    state: DiscordRpcUiState,
    pendingToken: String?,
    onPendingTokenChange: (String?) -> Unit,
    onBrowserSignIn: ((String) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    AppDiscordAuthDialog(
        initialToken = pendingToken ?: controller.tokenForEditing().orEmpty(),
        isSignedIn = controller.hasToken(),
        statusText = listOfNotNull(
            state.accountName,
            resolveDiscordRpcStatusLabel(
                status = state.status,
                disabledLabel = appText(AppTextKey.DiscordStatusDisabled),
                signedOutLabel = appText(AppTextKey.DiscordStatusSignedOut),
                checkingLabel = appText(AppTextKey.DiscordStatusChecking),
                connectingLabel = appText(AppTextKey.DiscordStatusConnecting),
                connectedLabel = appText(AppTextKey.DiscordStatusConnected),
                errorLabel = appText(AppTextKey.DiscordStatusError),
            ),
        ).distinct().joinToString(" · "),
        isChecking = state.status.isBusy(),
        iconContent = { iconModifier: Modifier ->
            Image(
                painter = painterResource(Res.drawable.ic_discord),
                contentDescription = null,
                modifier = iconModifier,
            )
        },
        title = appText(AppTextKey.SettingsDiscord),
        manualTokenLabel = appText(AppTextKey.DiscordManualToken),
        invalidTokenLabel = appText(AppTextKey.DiscordInvalidToken),
        disconnectLabel = appText(AppTextKey.DiscordDisconnect),
        browserSignInLabel = appText(AppTextKey.DiscordBrowserSignIn),
        cancelLabel = appText(AppTextKey.Cancel),
        applyLabel = appText(AppTextKey.Apply),
        onBrowserSignIn = {
            onBrowserSignIn { token ->
                onPendingTokenChange(token)
            }
        },
        onDisconnect = {
            controller.signOut()
            onClose()
        },
        onDismiss = {
            onPendingTokenChange(null)
            onClose()
        },
        onAuthenticate = controller::authenticate,
    )
}
