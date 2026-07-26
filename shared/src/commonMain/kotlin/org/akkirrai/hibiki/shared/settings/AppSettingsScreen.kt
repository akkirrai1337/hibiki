package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppSettingsScreenState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useSystemColorScheme: Boolean = true,
    val useAmoledTheme: Boolean = false,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val notificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    val autoSkipSegments: Boolean = false,
    val discordRpcEnabled: Boolean = false,
    val showUpdates: Boolean = false,
)

data class AppSettingsScreenLabels(
    val appearance: String,
    val theme: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val useSystemColorScheme: String,
    val amoled: String,
    val preferences: String,
    val language: String,
    val languageSystem: String,
    val languageEnglish: String,
    val languageRussian: String,
    val notifications: String,
    val notificationsNotAsked: String,
    val notificationsEnabled: String,
    val notificationsDisabled: String,
    val player: String,
    val autoSkipSegments: String,
    val experimental: String,
    val discordRpc: String,
    val updates: String,
    val checkForUpdates: String,
    val support: String,
    val exportLogs: String,
    val about: String,
    val appName: String,
    val versionName: String,
)

data class AppSettingsScreenIcons(
    val theme: ImageVector,
    val systemColorScheme: ImageVector,
    val amoled: ImageVector,
    val language: ImageVector,
    val notifications: ImageVector,
    val autoSkipSegments: ImageVector,
    val discordRpc: ImageVector,
    val update: ImageVector,
    val exportLogs: ImageVector,
    val chevron: ImageVector,
)

/** Stateless production Settings screen. Platform actions are explicit callbacks. */
@Composable
fun AppSettingsScreen(
    state: AppSettingsScreenState,
    labels: AppSettingsScreenLabels,
    icons: AppSettingsScreenIcons,
    appIconContent: @Composable () -> Unit,
    githubIconContent: @Composable () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onLanguageModeChange: (LanguageMode) -> Unit,
    onConfigureNotifications: () -> Unit,
    onAutoSkipSegmentsChange: (Boolean) -> Unit,
    onDiscordClick: () -> Unit,
    onDiscordEnabledChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onExportLogs: () -> Unit,
    onGitHubClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 24.dp, end = 18.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = SettingsSection.Appearance.key) {
            AppSettingsSection(title = labels.appearance) {
                AppSettingsItems(count = 2) { index, shape ->
                    when (index) {
                        0 -> AppSettingsIconVerticalItem(
                            icon = icons.theme,
                            title = labels.theme,
                            shape = shape,
                        ) {
                            AppSettingsSegmentedControl(
                                options = themeModeOptions,
                                selectedOption = state.themeMode,
                                label = { mode ->
                                    when (mode) {
                                        ThemeMode.SYSTEM -> labels.themeSystem
                                        ThemeMode.LIGHT -> labels.themeLight
                                        ThemeMode.DARK -> labels.themeDark
                                    }
                                },
                                onSelect = onThemeModeChange,
                            )
                        }

                        1 -> AppSettingsIconSwitchItem(
                            icon = icons.systemColorScheme,
                            title = labels.useSystemColorScheme,
                            checked = state.useSystemColorScheme,
                            shape = shape,
                            onCheckedChange = onSystemColorSchemeChange,
                        )
                    }
                }
            }
        }

        item(key = SettingsSection.Preferences.key) {
            AppSettingsSection(title = labels.preferences) {
                AppSettingsItems(count = 2) { index, shape ->
                    when (index) {
                        0 -> AppSettingsIconVerticalItem(
                            icon = icons.language,
                            title = labels.language,
                            shape = shape,
                        ) {
                            AppSettingsSegmentedControl(
                                options = languageModeOptions,
                                selectedOption = state.languageMode,
                                label = { mode ->
                                    when (mode) {
                                        LanguageMode.SYSTEM -> labels.languageSystem
                                        LanguageMode.ENGLISH -> labels.languageEnglish
                                        LanguageMode.RUSSIAN -> labels.languageRussian
                                    }
                                },
                                onSelect = onLanguageModeChange,
                            )
                        }

                        1 -> AppSettingsIconActionItem(
                            icon = icons.notifications,
                            title = labels.notifications,
                            subtitle = when (state.notificationPermissionState) {
                                NotificationPermissionState.NOT_ASKED -> labels.notificationsNotAsked
                                NotificationPermissionState.GRANTED -> labels.notificationsEnabled
                                NotificationPermissionState.DENIED -> labels.notificationsDisabled
                            },
                            shape = shape,
                            trailing = {
                                Icon(
                                    imageVector = icons.chevron,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = onConfigureNotifications,
                        )
                    }
                }
            }
        }

        item(key = SettingsSection.Player.key) {
            AppSettingsSection(title = labels.player) {
                AppSettingsItems(count = 1) { _, shape ->
                    AppSettingsIconSwitchItem(
                        icon = icons.autoSkipSegments,
                        title = labels.autoSkipSegments,
                        checked = state.autoSkipSegments,
                        shape = shape,
                        onCheckedChange = onAutoSkipSegmentsChange,
                    )
                }
            }
        }

        item(key = SettingsSection.Experimental.key) {
            AppSettingsSection(title = labels.experimental) {
                AppSettingsItems(count = 1) { _, shape ->
                    AppSettingsIconToggleItem(
                        icon = icons.discordRpc,
                        title = labels.discordRpc,
                        checked = state.discordRpcEnabled,
                        shape = shape,
                        onClick = onDiscordClick,
                        onCheckedChange = onDiscordEnabledChange,
                    )
                }
            }
        }

        if (state.showUpdates) {
            item(key = SettingsSection.Updates.key) {
                AppSettingsSection(title = labels.updates) {
                    AppSettingsItems(count = 1) { _, shape ->
                        AppSettingsIconActionItem(
                            icon = icons.update,
                            title = labels.checkForUpdates,
                            shape = shape,
                            onClick = onCheckForUpdates,
                        )
                    }
                }
            }
        }

        item(key = SettingsSection.Support.key) {
            AppSettingsSection(title = labels.support) {
                AppSettingsItems(count = 1) { _, shape ->
                    AppSettingsIconActionItem(
                        icon = icons.exportLogs,
                        title = labels.exportLogs,
                        shape = shape,
                        onClick = onExportLogs,
                    )
                }
            }
        }

        item(key = SettingsSection.About.key) {
            AppSettingsSection(title = labels.about) {
                AppSettingsAboutCard(
                    appName = labels.appName,
                    versionName = labels.versionName,
                    appIconContent = appIconContent,
                    githubIconContent = githubIconContent,
                    onGitHubClick = onGitHubClick,
                )
            }
        }
    }
}
