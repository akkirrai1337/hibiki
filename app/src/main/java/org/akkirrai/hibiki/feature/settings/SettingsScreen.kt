package org.akkirrai.hibiki.feature.settings

import android.graphics.Bitmap
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Update
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
import org.akkirrai.hibiki.core.discord.DiscordRpcConnectionStatus
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
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
import org.akkirrai.hibiki.shared.settings.AppSettingsIconSwitchItem
import org.akkirrai.hibiki.shared.settings.AppSettingsIconToggleItem
import org.akkirrai.hibiki.shared.settings.AppSettingsAboutCard
import org.akkirrai.hibiki.shared.settings.AppSettingsContentList
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
            AppSettingsSection(title = stringResource(R.string.settings_appearance)) {
                AppSettingsItems(count = 2) { index, shape ->
                    when (index) {
                        0 -> AppSettingsIconVerticalItem(
                            icon = SettingsThemeIcon,
                            title = stringResource(R.string.settings_theme),
                            shape = shape,
                        ) {
                            AppSettingsSegmentedControl(
                                options = themeModeOptions,
                                selectedOption = preferences.themeMode,
                                label = ::themeModeLabel,
                                onSelect = { mode ->
                                    appPreferences.setThemeMode(mode)
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                },
                            )
                        }

                        1 -> SettingsSwitchItem(
                            icon = SettingsSystemColorSchemeIcon,
                            title = stringResource(R.string.settings_use_system_color_scheme),
                            checked = preferences.useSystemColorScheme,
                            shape = shape,
                            onCheckedChange = appPreferences::setUseSystemColorScheme,
                        )

                        2 -> SettingsSwitchItem(
                            icon = SettingsAmoledIcon,
                            title = stringResource(R.string.settings_amoled),
                            checked = preferences.useAmoledTheme,
                            shape = shape,
                            onCheckedChange = appPreferences::setUseAmoledTheme,
                        )
                    }
                }
            }
        }

        item(key = SettingsSection.Preferences.key) {
            AppSettingsSection(title = stringResource(R.string.settings_preferences)) {
                AppSettingsItems(count = 2) { index, shape ->
                    when (index) {
                        0 -> AppSettingsIconVerticalItem(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_language),
                            shape = shape,
                        ) {
                            AppSettingsSegmentedControl(
                                options = languageModeOptions,
                                selectedOption = preferences.languageMode,
                                label = ::languageModeLabel,
                                onSelect = { mode ->
                                    appPreferences.setLanguageMode(mode)
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                },
                            )
                        }

                        1 -> AppSettingsIconActionItem(
                            icon = Icons.Outlined.Notifications,
                            title = stringResource(R.string.settings_notifications),
                            subtitle = notificationPermissionLabel(preferences.notificationPermissionState),
                            shape = shape,
                            showChevron = true,
                            onClick = onConfigureNotifications,
                        )

                    }
                }
            }
        }

        item(key = SettingsSection.Player.key) {
            AppSettingsSection(title = stringResource(R.string.settings_player)) {
                AppSettingsItems(count = 1) { _, _ ->
                    SettingsSwitchItem(
                        icon = Icons.Outlined.SkipNext,
                        title = stringResource(R.string.settings_auto_skip_segments),
                        checked = preferences.autoSkipSegments,
                        shape = CircleShape,
                        onCheckedChange = appPreferences::setAutoSkipSegments,
                    )
                }
            }
        }

        item(key = SettingsSection.Experimental.key) {
            AppSettingsSection(title = stringResource(R.string.settings_experimental)) {
                AppSettingsItems(count = 1) { _, shape ->
                    DiscordSettingsItem(
                        icon = ImageVector.vectorResource(R.drawable.ic_discord),
                        title = stringResource(R.string.discord_rpc_title),
                        checked = preferences.discordRpcEnabled,
                        shape = shape,
                        onClick = { isDiscordAuthDialogOpen = true },
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                appPreferences.setDiscordRpcEnabled(false)
                            } else if (discordRpcManager.hasToken()) {
                                discordRpcManager.refreshAuthentication(enableOnSuccess = true)
                            } else {
                                isDiscordAuthDialogOpen = true
                            }
                        },
                    )
                }
            }
        }

        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            item(key = SettingsSection.Updates.key) {
                AppSettingsSection(title = stringResource(R.string.settings_updates)) {
                    AppSettingsItems(count = 1) { _, _ ->
                        AppSettingsIconActionItem(
                            icon = Icons.Outlined.Update,
                            title = stringResource(R.string.settings_check_updates),
                            shape = CircleShape,
                            onClick = onCheckForUpdates,
                        )
                    }
                }
            }
        }

            item(key = SettingsSection.Support.key) {
            AppSettingsSection(title = stringResource(R.string.settings_support)) {
                AppSettingsItems(count = 1) { _, _ ->
                    AppSettingsIconActionItem(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.settings_export_logs),
                        shape = CircleShape,
                        onClick = {
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
            }
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
private fun notificationPermissionLabel(state: NotificationPermissionState): String = stringResource(
    when (state) {
        NotificationPermissionState.NOT_ASKED -> R.string.settings_notifications_not_asked
        NotificationPermissionState.GRANTED -> R.string.settings_notifications_enabled
        NotificationPermissionState.DENIED -> R.string.settings_notifications_disabled
    },
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
    val isChecking = state.status == DiscordRpcConnectionStatus.Checking ||
        state.status == DiscordRpcConnectionStatus.Connecting

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
private fun discordRpcStatusLabel(status: DiscordRpcConnectionStatus): String = stringResource(
    when (status) {
        DiscordRpcConnectionStatus.Disabled -> R.string.discord_rpc_status_disabled
        DiscordRpcConnectionStatus.SignedOut -> R.string.discord_rpc_status_signed_out
        DiscordRpcConnectionStatus.Checking -> R.string.discord_rpc_status_checking
        DiscordRpcConnectionStatus.Connecting -> R.string.discord_rpc_status_connecting
        DiscordRpcConnectionStatus.Connected -> R.string.discord_rpc_status_connected
        DiscordRpcConnectionStatus.Error -> R.string.discord_rpc_status_error
    },
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
    return stringResource(
        when (mode) {
            LanguageMode.SYSTEM -> R.string.settings_language_system
            LanguageMode.RUSSIAN -> R.string.settings_language_russian
            LanguageMode.ENGLISH -> R.string.settings_language_english
        },
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )
}

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
