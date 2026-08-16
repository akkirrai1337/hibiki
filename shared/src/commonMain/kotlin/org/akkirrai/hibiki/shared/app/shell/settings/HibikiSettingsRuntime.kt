package org.akkirrai.hibiki.shared.app.shell.settings

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.ic_discord
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.activeOverlay
import org.akkirrai.hibiki.shared.navigation.reduceOverlayVisibilityChange
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialog
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.isBusy
import org.akkirrai.hibiki.shared.settings.resolveDiscordRpcStatusLabel
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.jetbrains.compose.resources.painterResource

internal class HibikiSettingsActions(
    val onLanguageModeChange: (LanguageMode) -> Unit,
    val onThemeChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onSystemColorSchemeChange: (Boolean) -> Unit,
    val onAmoledChange: (Boolean) -> Unit,
    val onAutoSkipChange: (Boolean) -> Unit,
)

internal fun createHibikiSettingsActions(
    saveSettings: () -> Unit,
    setLanguageMode: (LanguageMode) -> Unit,
    setDarkTheme: (Boolean) -> Unit,
    setThemeMode: (ThemeMode) -> Unit,
    setUseSystemColorScheme: (Boolean) -> Unit,
    setUseAmoledTheme: (Boolean) -> Unit,
    setAutoSkipSegments: (Boolean) -> Unit,
): HibikiSettingsActions = HibikiSettingsActions(
    onLanguageModeChange = { mode ->
        setLanguageMode(mode)
        saveSettings()
    },
    onThemeChange = { dark ->
        setDarkTheme(dark)
        saveSettings()
    },
    onThemeModeChange = { mode ->
        setThemeMode(mode)
        if (mode != ThemeMode.SYSTEM) setDarkTheme(mode == ThemeMode.DARK)
        saveSettings()
    },
    onSystemColorSchemeChange = { enabled ->
        setUseSystemColorScheme(enabled)
        saveSettings()
    },
    onAmoledChange = { enabled ->
        setUseAmoledTheme(enabled)
        saveSettings()
    },
    onAutoSkipChange = { enabled ->
        setAutoSkipSegments(enabled)
        saveSettings()
    },
)

internal class HibikiDiscordSettingsActions(
    val onClick: () -> Unit,
    val onChange: (Boolean) -> Unit,
)

internal fun createHibikiDiscordSettingsActions(
    controller: DiscordRpcController?,
    openAuthDialog: () -> Unit,
): HibikiDiscordSettingsActions = HibikiDiscordSettingsActions(
    onClick = { if (controller != null) openAuthDialog() },
    onChange = { enabled ->
        controller?.let {
            if (enabled && !it.hasToken()) openAuthDialog()
            else it.setEnabled(enabled)
        }
    },
)

internal class HibikiDiscordAuthState {
    val overlay = AppOverlay.Dialog("discord-auth")
    var pendingToken by mutableStateOf<String?>(null)

    fun isOpen(navigationState: AppNavigationState): Boolean =
        navigationState.activeOverlay == overlay

    fun open(
        navigationState: AppNavigationState,
        setNavigationState: (AppNavigationState) -> Unit,
    ) {
        if (!isOpen(navigationState)) {
            setNavigationState(
                navigationState.reduceOverlayVisibilityChange(
                    overlay = overlay,
                    visible = true,
                ),
            )
        }
    }

    fun close(
        navigationState: AppNavigationState,
        setNavigationState: (AppNavigationState) -> Unit,
    ) {
        if (isOpen(navigationState)) {
            setNavigationState(
                navigationState.reduceOverlayVisibilityChange(
                    overlay = overlay,
                    visible = false,
                ),
            )
        }
    }
}

@Composable
internal fun rememberHibikiDiscordAuthState(): HibikiDiscordAuthState = remember {
    HibikiDiscordAuthState()
}

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
