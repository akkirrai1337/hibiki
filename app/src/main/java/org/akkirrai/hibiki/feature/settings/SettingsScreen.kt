package org.akkirrai.hibiki.feature.settings

import android.graphics.Bitmap
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.LocalizedAppContext
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
import org.akkirrai.hibiki.shared.settings.AppSettingsScreen
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenIcons
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenLabels
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp,
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
    val appIcon = remember(context) {
        context.packageManager
            .getApplicationIcon(context.packageName)
            .toBitmap(config = Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    LaunchedEffect(Unit) {
        PerfLogger.mark("SettingsScreen composed")
    }

    AppSettingsScreen(
        state = AppSettingsScreenState(
            themeMode = preferences.themeMode,
            useSystemColorScheme = preferences.useSystemColorScheme,
            useAmoledTheme = preferences.useAmoledTheme,
            languageMode = preferences.languageMode,
            notificationPermissionState = preferences.notificationPermissionState,
            autoSkipSegments = preferences.autoSkipSegments,
            discordRpcEnabled = preferences.discordRpcEnabled,
            showUpdates = BuildConfig.GITHUB_UPDATES_ENABLED,
        ),
        labels = AppSettingsScreenLabels(
            appearance = stringResource(R.string.settings_appearance),
            theme = stringResource(R.string.settings_theme),
            themeSystem = stringResource(R.string.settings_theme_system),
            themeLight = stringResource(R.string.settings_theme_light),
            themeDark = stringResource(R.string.settings_theme_dark),
            useSystemColorScheme = stringResource(R.string.settings_use_system_color_scheme),
            amoled = stringResource(R.string.settings_amoled),
            preferences = stringResource(R.string.settings_preferences),
            language = stringResource(R.string.settings_language),
            languageSystem = stringResource(R.string.settings_language_system),
            languageEnglish = stringResource(R.string.settings_language_english),
            languageRussian = stringResource(R.string.settings_language_russian),
            notifications = stringResource(R.string.settings_notifications),
            notificationsNotAsked = stringResource(R.string.settings_notifications_not_asked),
            notificationsEnabled = stringResource(R.string.settings_notifications_enabled),
            notificationsDisabled = stringResource(R.string.settings_notifications_disabled),
            player = stringResource(R.string.settings_player),
            autoSkipSegments = stringResource(R.string.settings_auto_skip_segments),
            experimental = stringResource(R.string.settings_experimental),
            discordRpc = stringResource(R.string.discord_rpc_title),
            updates = stringResource(R.string.settings_updates),
            checkForUpdates = stringResource(R.string.settings_check_updates),
            support = stringResource(R.string.settings_support),
            exportLogs = stringResource(R.string.settings_export_logs),
            about = stringResource(R.string.settings_about),
            appName = stringResource(R.string.app_name),
            versionName = versionName,
        ),
        icons = AppSettingsScreenIcons(
            theme = Icons.Outlined.DarkMode,
            systemColorScheme = Icons.Outlined.Palette,
            amoled = Icons.Outlined.Contrast,
            language = Icons.Outlined.Language,
            notifications = Icons.Outlined.Notifications,
            autoSkipSegments = Icons.Outlined.SkipNext,
            discordRpc = ImageVector.vectorResource(R.drawable.ic_discord),
            update = Icons.Outlined.Update,
            exportLogs = Icons.Outlined.Share,
            chevron = Icons.Outlined.ChevronRight,
        ),
        appIconContent = {
            Image(
                bitmap = appIcon,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(48.dp),
            )
        },
        githubIconContent = {
            Image(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = stringResource(R.string.settings_github),
                modifier = Modifier.size(26.dp),
            )
        },
        onThemeModeChange = { mode ->
            appPreferences.setThemeMode(mode)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        },
        onSystemColorSchemeChange = appPreferences::setUseSystemColorScheme,
        onAmoledChange = appPreferences::setUseAmoledTheme,
        onLanguageModeChange = { mode ->
            appPreferences.setLanguageMode(mode)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        },
        onConfigureNotifications = onConfigureNotifications,
        onAutoSkipSegmentsChange = { enabled ->
            appPreferences.setAutoSkipSegments(enabled)
            haptic.performHapticFeedback(if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        },
        onDiscordClick = { isDiscordAuthDialogOpen = true },
        onDiscordEnabledChange = { enabled ->
            if (!enabled) {
                appPreferences.setDiscordRpcEnabled(false)
            } else if (discordRpcManager.hasToken()) {
                discordRpcManager.refreshAuthentication(enableOnSuccess = true)
            } else {
                isDiscordAuthDialogOpen = true
            }
            haptic.performHapticFeedback(if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LocalizedAppContext(languageMode = appLanguage) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_discord),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.discord_rpc_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listOfNotNull(
                                state.account?.displayName,
                                discordRpcStatusLabel(state.status),
                            ).distinct().joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = {
                                manualToken = it
                                manualTokenFailed = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.discord_rpc_manual_token)) },
                            supportingText = if (manualTokenFailed) {
                                { Text(stringResource(R.string.discord_rpc_invalid_token)) }
                            } else {
                                null
                            },
                            isError = manualTokenFailed,
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isChecking,
                            shape = RoundedCornerShape(16.dp),
                        )
                        if (isSignedIn) {
                            OutlinedButton(
                                onClick = {
                                    manager.signOut()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = !isChecking,
                            ) {
                                Text(stringResource(R.string.discord_rpc_disconnect))
                            }
                        } else {
                            Button(
                                onClick = onBrowserSignIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = !isChecking,
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_discord),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.discord_rpc_sign_in_browser),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = !isChecking,
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                manager.authenticate(manualToken)
                                    .onSuccess { onDismiss() }
                                    .onFailure { manualTokenFailed = true }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = manualToken.isNotBlank() && !isChecking,
                    ) {
                        Text(stringResource(R.string.settings_apply))
                    }
                }
                }
            }
        }
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

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
