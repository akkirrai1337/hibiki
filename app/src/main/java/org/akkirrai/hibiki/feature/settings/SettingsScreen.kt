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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
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
import org.akkirrai.hibiki.shared.settings.AppSettingsSwitch
import org.akkirrai.hibiki.shared.settings.AppSettingsSection
import org.akkirrai.hibiki.shared.settings.AppSettingsItems
import org.akkirrai.hibiki.shared.settings.AppSettingsItemHeader
import org.akkirrai.hibiki.shared.settings.AppSettingsItemRow
import org.akkirrai.hibiki.shared.settings.AppSettingsActionItem
import org.akkirrai.hibiki.shared.settings.AppSettingsAboutCard
import org.akkirrai.hibiki.shared.settings.AppSettingsSwitchItem
import org.akkirrai.hibiki.shared.settings.AppSettingsToggleItem
import org.akkirrai.hibiki.shared.settings.AppSettingsVerticalItem
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp,
    onCheckForUpdates: () -> Unit = {},
    onOpenSources: () -> Unit = {},
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 24.dp,
            end = 18.dp,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "appearance") {
            AppSettingsSection(title = stringResource(R.string.settings_appearance)) {
                AppSettingsItems(count = 3) { index, shape ->
                    when (index) {
                        0 -> SettingsVerticalItem(
                            icon = Icons.Outlined.DarkMode,
                            title = stringResource(R.string.settings_theme),
                            shape = shape,
                        ) {
                            AppSettingsSegmentedControl(
                                options = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM),
                                selectedOption = preferences.themeMode,
                                label = ::themeModeLabel,
                                onSelect = { mode ->
                                    appPreferences.setThemeMode(mode)
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                },
                            )
                        }

                        1 -> SettingsSwitchItem(
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_use_system_color_scheme),
                            checked = preferences.useSystemColorScheme,
                            shape = shape,
                            onCheckedChange = appPreferences::setUseSystemColorScheme,
                        )

                        2 -> SettingsSwitchItem(
                            icon = Icons.Outlined.Contrast,
                            title = stringResource(R.string.settings_amoled),
                            checked = preferences.useAmoledTheme,
                            shape = shape,
                            onCheckedChange = appPreferences::setUseAmoledTheme,
                        )
                    }
                }
            }
        }

        item(key = "preferences") {
            AppSettingsSection(title = stringResource(R.string.settings_preferences)) {
                AppSettingsItems(count = 3) { index, shape ->
                    when (index) {
                        0 -> SettingsVerticalItem(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_language),
                            shape = shape,
                        ) {
                            AppSettingsSegmentedControl(
                                options = listOf(LanguageMode.RUSSIAN, LanguageMode.ENGLISH, LanguageMode.SYSTEM),
                                selectedOption = preferences.languageMode,
                                label = ::languageModeLabel,
                                onSelect = { mode ->
                                    appPreferences.setLanguageMode(mode)
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                },
                            )
                        }

                        1 -> SettingsActionItem(
                            icon = Icons.Outlined.Storage,
                            title = stringResource(R.string.settings_sources),
                            shape = shape,
                            showNavigationArrow = true,
                            onClick = onOpenSources,
                        )

                        2 -> SettingsActionItem(
                            icon = Icons.Outlined.Notifications,
                            title = stringResource(R.string.settings_notifications),
                            subtitle = notificationPermissionLabel(preferences.notificationPermissionState),
                            shape = shape,
                            showNavigationArrow = true,
                            onClick = onConfigureNotifications,
                        )

                    }
                }
            }
        }

        item(key = "player") {
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

        item(key = "experimental") {
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
            item(key = "updates") {
                AppSettingsSection(title = stringResource(R.string.settings_updates)) {
                    AppSettingsItems(count = 1) { _, _ ->
                        SettingsActionItem(
                            icon = Icons.Outlined.Update,
                            title = stringResource(R.string.settings_check_updates),
                            shape = CircleShape,
                            onClick = onCheckForUpdates,
                        )
                    }
                }
            }
        }

        item(key = "support") {
            AppSettingsSection(title = stringResource(R.string.settings_support)) {
                AppSettingsItems(count = 1) { _, _ ->
                    SettingsActionItem(
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

        item(key = "about") {
            AppSettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsAboutItem(
                    versionName = versionName,
                    onGitHubClick = { uriHandler.openUri(HIBIKI_GITHUB_URL) },
                )
            }
        }
    }

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
private fun SettingsVerticalItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    AppSettingsVerticalItem(
        headerContent = {
            AppSettingsItemHeader(
                iconContent = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = title,
            )
        },
        shape = shape,
        content = content,
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    AppSettingsSwitchItem(
        iconContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        },
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
    AppSettingsToggleItem(
        iconContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        },
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
private fun SettingsActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    shape: Shape,
    showNavigationArrow: Boolean = false,
    onClick: () -> Unit,
) {
    AppSettingsActionItem(
        iconContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        },
        title = title,
        subtitle = subtitle,
        shape = shape,
        trailing = if (showNavigationArrow) {
            {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        onClick = onClick,
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
