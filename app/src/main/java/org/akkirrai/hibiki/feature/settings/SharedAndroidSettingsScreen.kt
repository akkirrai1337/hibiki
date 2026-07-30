package org.akkirrai.hibiki.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.discord.DiscordAuthActivity
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialog
import org.akkirrai.hibiki.shared.settings.AppSettingsScreen
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenLabels
import org.akkirrai.hibiki.shared.settings.DiscordRpcConnectionStatus
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.resolveDiscordRpcStatusLabel
import org.akkirrai.hibiki.shared.settings.resolveNotificationPermissionLabel
import org.akkirrai.hibiki.shared.settings.resolveLanguageModeLabel
import org.akkirrai.hibiki.shared.settings.resolveThemeModeLabel
import org.akkirrai.hibiki.shared.settings.isBusy

@Composable
fun SharedAndroidSettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 96.dp,
    onCheckForUpdates: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val appPreferences = LocalAppPreferences.current
    val preferences = LocalAppPreferencesState.current
    val discordRpcManager = remember(context) { DiscordRpcManager.get(context) }
    var isDiscordAuthDialogOpen by remember { mutableStateOf(false) }
    var pendingDiscordToken by remember { mutableStateOf<String?>(null) }
    val discordAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            DiscordAuthActivity.tokenFromResult(result.data)?.let { token ->
                pendingDiscordToken = token
                isDiscordAuthDialogOpen = true
            }
        }
    }
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    AppSettingsScreen(
        languageMode = preferences.languageMode,
        darkTheme = preferences.themeMode == ThemeMode.DARK,
        themeMode = preferences.themeMode,
        discordEnabled = preferences.discordRpcEnabled,
        showUpdates = BuildConfig.GITHUB_UPDATES_ENABLED,
        useSystemColorScheme = preferences.useSystemColorScheme,
        useAmoledTheme = preferences.useAmoledTheme,
        autoSkipSegments = preferences.autoSkipSegments,
        labels = sharedAndroidSettingsLabels(
            versionName = versionName,
            notificationState = preferences.notificationPermissionState,
        ),
        onLanguageModeChange = { mode ->
            appPreferences.setLanguageMode(mode)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        },
        onThemeChange = { dark -> appPreferences.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) },
        onThemeModeChange = { mode ->
            appPreferences.setThemeMode(mode)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        },
        onSystemColorSchemeChange = { enabled ->
            appPreferences.setUseSystemColorScheme(enabled)
            haptic.performHapticFeedback(if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        },
        onAmoledChange = { enabled ->
            appPreferences.setUseAmoledTheme(enabled)
            haptic.performHapticFeedback(if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        },
        onNotificationsClick = onConfigureNotifications,
        onAutoSkipChange = { enabled ->
            appPreferences.setAutoSkipSegments(enabled)
            haptic.performHapticFeedback(if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        },
        onDiscordClick = { isDiscordAuthDialogOpen = true },
        onDiscordChange = { enabled ->
            if (!enabled) appPreferences.setDiscordRpcEnabled(false)
            else if (discordRpcManager.hasToken()) discordRpcManager.refreshAuthentication(enableOnSuccess = true)
            else isDiscordAuthDialogOpen = true
        },
        onCheckForUpdates = onCheckForUpdates,
        onExportLogs = {
            AppLogger.shareLogs(context).onFailure {
                Toast.makeText(context, R.string.settings_export_logs_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onGitHubClick = { uriHandler.openUri(HIBIKI_GITHUB_URL) },
        modifier = modifier,
        bottomContentPadding = bottomContentPadding,
    )

    if (isDiscordAuthDialogOpen) {
        val discordState by discordRpcManager.state.collectAsState()
        AppDiscordAuthDialog(
            initialToken = pendingDiscordToken ?: discordRpcManager.tokenForEditing().orEmpty(),
            isSignedIn = discordRpcManager.hasToken(),
            statusText = listOfNotNull(
                discordState.account?.displayName,
                resolveDiscordRpcStatusLabel(
                    status = discordState.status,
                    disabledLabel = stringResource(R.string.discord_rpc_status_disabled),
                    signedOutLabel = stringResource(R.string.discord_rpc_status_signed_out),
                    checkingLabel = stringResource(R.string.discord_rpc_status_checking),
                    connectingLabel = stringResource(R.string.discord_rpc_status_connecting),
                    connectedLabel = stringResource(R.string.discord_rpc_status_connected),
                    errorLabel = stringResource(R.string.discord_rpc_status_error),
                ),
            ).distinct().joinToString(" • "),
            isChecking = discordState.status.isBusy(),
            icon = ImageVector.vectorResource(R.drawable.ic_discord),
            title = stringResource(R.string.discord_rpc_title),
            manualTokenLabel = stringResource(R.string.discord_rpc_manual_token),
            invalidTokenLabel = stringResource(R.string.discord_rpc_invalid_token),
            disconnectLabel = stringResource(R.string.discord_rpc_disconnect),
            browserSignInLabel = stringResource(R.string.discord_rpc_sign_in_browser),
            cancelLabel = stringResource(R.string.action_cancel),
            applyLabel = stringResource(R.string.settings_apply),
            onBrowserSignIn = { discordAuthLauncher.launch(Intent(context, DiscordAuthActivity::class.java)) },
            onDisconnect = {
                discordRpcManager.signOut()
                isDiscordAuthDialogOpen = false
            },
            onDismiss = {
                pendingDiscordToken = null
                isDiscordAuthDialogOpen = false
            },
            onAuthenticate = discordRpcManager::authenticate,
        )
    }
}

@Composable
private fun sharedAndroidSettingsLabels(
    versionName: String,
    notificationState: NotificationPermissionState,
) = AppSettingsScreenLabels(
    appearance = stringResource(R.string.settings_appearance),
    theme = stringResource(R.string.settings_theme),
    themeSystem = stringResource(
        R.string.settings_theme_option,
        stringResource(R.string.settings_theme_system),
    ),
    themeLight = stringResource(
        R.string.settings_theme_option,
        stringResource(R.string.settings_theme_light),
    ),
    themeDark = stringResource(
        R.string.settings_theme_option,
        stringResource(R.string.settings_theme_dark),
    ),
    systemColorScheme = stringResource(R.string.settings_use_system_color_scheme),
    amoled = stringResource(R.string.settings_amoled),
    preferences = stringResource(R.string.settings_preferences),
    language = stringResource(R.string.settings_language),
    languageSystem = stringResource(
        R.string.settings_language_option,
        stringResource(R.string.settings_language_system),
    ),
    languageRussian = stringResource(
        R.string.settings_language_option,
        stringResource(R.string.settings_language_russian),
    ),
    languageEnglish = stringResource(
        R.string.settings_language_option,
        stringResource(R.string.settings_language_english),
    ),
    notifications = stringResource(R.string.settings_notifications),
    notificationsStatus = notificationPermissionLabel(notificationState),
    player = stringResource(R.string.settings_player),
    autoSkip = stringResource(R.string.settings_auto_skip_segments),
    experimental = stringResource(R.string.settings_experimental),
    discord = stringResource(R.string.discord_rpc_title),
    updates = stringResource(R.string.settings_updates),
    checkUpdates = stringResource(R.string.settings_check_updates),
    support = stringResource(R.string.settings_support),
    exportLogs = stringResource(R.string.settings_export_logs),
    appName = stringResource(R.string.app_name),
    versionName = versionName,
)

@Composable
private fun notificationPermissionLabel(state: NotificationPermissionState): String =
    resolveNotificationPermissionLabel(
        state = state,
        notAskedLabel = stringResource(R.string.settings_notifications_not_asked),
        grantedLabel = stringResource(R.string.settings_notifications_enabled),
        deniedLabel = stringResource(R.string.settings_notifications_disabled),
    )

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
