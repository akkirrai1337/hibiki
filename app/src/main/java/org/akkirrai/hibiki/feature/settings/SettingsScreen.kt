package org.akkirrai.hibiki.feature.settings

import android.graphics.Bitmap
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.LocalizedAppContext
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.discord.DiscordAuthActivity
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.shared.settings.DiscordRpcConnectionStatus
import org.akkirrai.hibiki.shared.settings.isBusy
import org.akkirrai.hibiki.shared.settings.resolveDiscordRpcStatusLabel
import org.akkirrai.hibiki.shared.settings.AppSettingsSegmentedControl
import org.akkirrai.hibiki.shared.settings.AppSettingsSection
import org.akkirrai.hibiki.shared.settings.AppSettingsItems
import org.akkirrai.hibiki.shared.settings.SettingsSection
import org.akkirrai.hibiki.shared.settings.themeModeOptions
import org.akkirrai.hibiki.shared.settings.languageModeOptions
import org.akkirrai.hibiki.shared.settings.AppSettingsIconVerticalItem
import org.akkirrai.hibiki.shared.settings.AppSettingsIconActionItem
import org.akkirrai.hibiki.shared.settings.SettingsAmoledIcon
import org.akkirrai.hibiki.shared.settings.SettingsSystemColorSchemeIcon
import org.akkirrai.hibiki.shared.settings.SettingsThemeIcon
import org.akkirrai.hibiki.shared.settings.SettingsLanguageIcon
import org.akkirrai.hibiki.shared.settings.SettingsNotificationsIcon
import org.akkirrai.hibiki.shared.settings.SettingsAutoSkipIcon
import org.akkirrai.hibiki.shared.settings.SettingsUpdatesIcon
import org.akkirrai.hibiki.shared.settings.SettingsExportLogsIcon
import org.akkirrai.hibiki.shared.settings.AppSettingsIconSwitchItem
import org.akkirrai.hibiki.shared.settings.AppSettingsIconToggleItem
import org.akkirrai.hibiki.shared.settings.AppSettingsAboutCard
import org.akkirrai.hibiki.shared.settings.AppSettingsContentList
import org.akkirrai.hibiki.shared.settings.AppSettingsAppearanceSection
import org.akkirrai.hibiki.shared.settings.AppSettingsPreferencesSection
import org.akkirrai.hibiki.shared.settings.AppSettingsPlayerSection
import org.akkirrai.hibiki.shared.settings.AppSettingsUpdatesSection
import org.akkirrai.hibiki.shared.settings.AppSettingsSupportSection
import org.akkirrai.hibiki.shared.settings.AppSettingsExperimentalSection
import org.akkirrai.hibiki.shared.settings.resolveLanguageModeLabel
import org.akkirrai.hibiki.shared.settings.resolveThemeModeLabel
import org.akkirrai.hibiki.shared.settings.resolveNotificationPermissionLabel
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialogHeader
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthTokenCard
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialogActions
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialogSurface
import org.akkirrai.hibiki.shared.settings.SettingsScreenDefaultBottomContentPadding
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = SettingsScreenDefaultBottomContentPadding,
    onCheckForUpdates: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val appPreferences = LocalAppPreferences.current
    val preferences = LocalAppPreferencesState.current
    val discordRpcManager = remember(context) { DiscordRpcManager.get(context) }
    var isDiscordAuthDialogOpen by remember { mutableStateOf(false) }
    var pendingDiscordToken by remember { mutableStateOf<String?>(null) }
    val discordAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
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

    LaunchedEffect(Unit) {
        PerfLogger.mark("SettingsScreen composed")
    }

    AppSettingsContentList(
        bottomContentPadding = bottomContentPadding,
        modifier = modifier.fillMaxSize(),
        content = {
        item(key = SettingsSection.Appearance.key) {
            AppSettingsAppearanceSection(
                sectionTitle = stringResource(R.string.settings_appearance),
                themeTitle = stringResource(R.string.settings_theme),
                themeOptions = themeModeOptions,
                selectedTheme = preferences.themeMode,
                themeLabel = ::themeModeLabel,
                onThemeSelected = { mode ->
                    appPreferences.setThemeMode(mode)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                systemColorSchemeTitle = stringResource(R.string.settings_use_system_color_scheme),
                useSystemColorScheme = preferences.useSystemColorScheme,
                onSystemColorSchemeChange = { enabled ->
                    appPreferences.setUseSystemColorScheme(enabled)
                    haptic.performHapticFeedback(
                        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                },
                amoledTitle = stringResource(R.string.settings_amoled),
                useAmoledTheme = preferences.useAmoledTheme,
                onAmoledChange = { enabled ->
                    appPreferences.setUseAmoledTheme(enabled)
                    haptic.performHapticFeedback(
                        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                },
            )
        }

        item(key = SettingsSection.Preferences.key) {
            AppSettingsPreferencesSection(
                sectionTitle = stringResource(R.string.settings_preferences),
                languageTitle = stringResource(R.string.settings_language),
                languageOptions = languageModeOptions,
                selectedLanguage = preferences.languageMode,
                languageLabel = ::languageModeLabel,
                onLanguageSelected = { mode ->
                    appPreferences.setLanguageMode(mode)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                notificationsTitle = stringResource(R.string.settings_notifications),
                notificationsSubtitle = notificationPermissionLabel(preferences.notificationPermissionState),
                onNotificationsClick = onConfigureNotifications,
            )
        }

        item(key = SettingsSection.Player.key) {
            AppSettingsPlayerSection(
                sectionTitle = stringResource(R.string.settings_player),
                autoSkipTitle = stringResource(R.string.settings_auto_skip_segments),
                autoSkipEnabled = preferences.autoSkipSegments,
                onAutoSkipChange = { enabled ->
                    appPreferences.setAutoSkipSegments(enabled)
                    haptic.performHapticFeedback(
                        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                },
            )
        }

        item(key = SettingsSection.Experimental.key) {
            AppSettingsExperimentalSection(
                sectionTitle = stringResource(R.string.settings_experimental),
                discordIcon = ImageVector.vectorResource(R.drawable.ic_discord),
                discordTitle = stringResource(R.string.discord_rpc_title),
                discordEnabled = preferences.discordRpcEnabled,
                onDiscordClick = { isDiscordAuthDialogOpen = true },
                onDiscordChange = { enabled ->
                    if (!enabled) {
                        appPreferences.setDiscordRpcEnabled(false)
                    } else if (discordRpcManager.hasToken()) {
                        discordRpcManager.refreshAuthentication(enableOnSuccess = true)
                    } else {
                        isDiscordAuthDialogOpen = true
                    }
                    haptic.performHapticFeedback(
                        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                },
            )
        }

        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            item(key = SettingsSection.Updates.key) {
                AppSettingsUpdatesSection(
                    sectionTitle = stringResource(R.string.settings_updates),
                    checkForUpdatesTitle = stringResource(R.string.settings_check_updates),
                    onCheckForUpdates = onCheckForUpdates,
                )
            }
        }

            item(key = SettingsSection.Support.key) {
                AppSettingsSupportSection(
                    sectionTitle = stringResource(R.string.settings_support),
                    exportLogsTitle = stringResource(R.string.settings_export_logs),
                    onExportLogs = {
                        AppLogger.shareLogs(context).onFailure {
                            Toast.makeText(
                                context,
                                R.string.settings_export_logs_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

        item(key = SettingsSection.About.key) {
            AppSettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsAboutItem(
                    versionName = versionName,
                    onGitHubClick = { uriHandler.openUri(HIBIKI_GITHUB_URL) },
                )
            }
        }
        },
    )

    if (isDiscordAuthDialogOpen) {
        DiscordAuthDialog(
            manager = discordRpcManager,
            initialToken = pendingDiscordToken
                ?: discordRpcManager.tokenForEditing().orEmpty(),
            isSignedIn = discordRpcManager.hasToken(),
            onBrowserSignIn = {
                discordAuthLauncher.launch(Intent(context, DiscordAuthActivity::class.java))
            },
            onDismiss = {
                pendingDiscordToken = null
                isDiscordAuthDialogOpen = false
            },
        )
    }
}

@Composable
private fun notificationPermissionLabel(state: NotificationPermissionState): String =
    resolveNotificationPermissionLabel(
        state = state,
        notAskedLabel = stringResource(R.string.settings_notifications_not_asked),
        grantedLabel = stringResource(R.string.settings_notifications_enabled),
        deniedLabel = stringResource(R.string.settings_notifications_disabled),
    )

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    AppSettingsIconSwitchItem(
        icon = icon,
        title = title,
        checked = checked,
        shape = shape,
        onCheckedChange = { enabled ->
            onCheckedChange(enabled)
            haptic.performHapticFeedback(
                if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
        },
    )
}

@Composable
private fun DiscordSettingsItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    AppSettingsIconToggleItem(
        icon = icon,
        title = title,
        checked = checked,
        shape = shape,
        onClick = onClick,
        onCheckedChange = { enabled ->
            onCheckedChange(enabled)
            haptic.performHapticFeedback(
                if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
        },
    )
}

@Composable
private fun DiscordAuthDialog(
    manager: DiscordRpcManager,
    initialToken: String,
    isSignedIn: Boolean,
    onBrowserSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appLanguage = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val state by manager.state.collectAsState()
    var manualToken by remember(initialToken) { mutableStateOf(initialToken) }
    var manualTokenFailed by remember { mutableStateOf(false) }
    val isChecking = state.status.isBusy()

    LocalizedAppContext(languageMode = appLanguage) {
        AppDiscordAuthDialogSurface(
        onDismissRequest = onDismiss,
        headerContent = {
            AppDiscordAuthDialogHeader(
                        icon = ImageVector.vectorResource(R.drawable.ic_discord),
                        title = stringResource(R.string.discord_rpc_title),
                        statusText = listOfNotNull(
                                state.account?.displayName,
                                discordRpcStatusLabel(state.status),
                            ).distinct().joinToString(" • "),
            )
        },
        tokenContent = {
            AppDiscordAuthTokenCard(
                    icon = ImageVector.vectorResource(R.drawable.ic_discord),
                    manualToken = manualToken,
                    onManualTokenChange = {
                        manualToken = it
                        manualTokenFailed = false
                    },
                    manualTokenLabel = stringResource(R.string.discord_rpc_manual_token),
                    invalidTokenLabel = stringResource(R.string.discord_rpc_invalid_token),
                    manualTokenFailed = manualTokenFailed,
                    isChecking = isChecking,
                    isSignedIn = isSignedIn,
                    disconnectLabel = stringResource(R.string.discord_rpc_disconnect),
                    browserSignInLabel = stringResource(R.string.discord_rpc_sign_in_browser),
                    onDisconnect = {
                        manager.signOut()
                        onDismiss()
                    },
                    onBrowserSignIn = onBrowserSignIn,
            )
        },
        actionsContent = {
            AppDiscordAuthDialogActions(
                    cancelLabel = stringResource(R.string.action_cancel),
                    applyLabel = stringResource(R.string.settings_apply),
                    cancelEnabled = !isChecking,
                    applyEnabled = manualToken.isNotBlank() && !isChecking,
                    onCancel = onDismiss,
                    onApply = {
                        scope.launch {
                            manager.authenticate(manualToken)
                                .onSuccess { onDismiss() }
                                .onFailure { manualTokenFailed = true }
                        }
                    },
            )
        },
        )
    }
}

@Composable
private fun discordRpcStatusLabel(status: DiscordRpcConnectionStatus): String =
    resolveDiscordRpcStatusLabel(
        status = status,
        disabledLabel = stringResource(R.string.discord_rpc_status_disabled),
        signedOutLabel = stringResource(R.string.discord_rpc_status_signed_out),
        checkingLabel = stringResource(R.string.discord_rpc_status_checking),
        connectingLabel = stringResource(R.string.discord_rpc_status_connecting),
        connectedLabel = stringResource(R.string.discord_rpc_status_connected),
        errorLabel = stringResource(R.string.discord_rpc_status_error),
    )

@Composable
private fun SettingsAboutItem(
    versionName: String,
    onGitHubClick: () -> Unit,
) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        context.packageManager
            .getApplicationIcon(context.packageName)
            .toBitmap(config = Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
    AppSettingsAboutCard(
        appName = stringResource(R.string.app_name),
        versionName = versionName,
        appIconContent = { iconModifier ->
            Image(
                bitmap = appIcon,
                contentDescription = stringResource(R.string.app_name),
                modifier = iconModifier,
            )
        },
        githubIconContent = { iconModifier ->
            Image(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = stringResource(R.string.settings_github),
                modifier = iconModifier,
            )
        },
        onGitHubClick = onGitHubClick,
    )
}

@Composable
private fun languageModeLabel(mode: LanguageMode): String {
    return resolveLanguageModeLabel(
        mode = mode,
        systemLabel = stringResource(R.string.settings_language_system),
        russianLabel = stringResource(R.string.settings_language_russian),
        englishLabel = stringResource(R.string.settings_language_english),
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return resolveThemeModeLabel(
        mode = mode,
        systemLabel = stringResource(R.string.settings_theme_system),
        lightLabel = stringResource(R.string.settings_theme_light),
        darkLabel = stringResource(R.string.settings_theme_dark),
    )
}

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
