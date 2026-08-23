package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsSection(val key: String) {
    Appearance("appearance"),
    Preferences("preferences"),
    Player("player"),
    Experimental("experimental"),
    Updates("updates"),
    Support("support"),
    About("about"),
}

@Composable
fun AppSettingsAppearanceSection(
    sectionTitle: String,
    themeTitle: String,
    themeOptions: List<ThemeMode>,
    selectedTheme: ThemeMode,
    themeLabel: @Composable (ThemeMode) -> String,
    onThemeSelected: (ThemeMode) -> Unit,
    systemColorSchemeTitle: String,
    useSystemColorScheme: Boolean,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    amoledTitle: String,
    useAmoledTheme: Boolean,
    onAmoledChange: (Boolean) -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 3) { index, shape ->
            when (index) {
                0 -> AppSettingsIconVerticalItem(
                    icon = SettingsThemeIcon,
                    title = themeTitle,
                    shape = shape,
                ) {
                    AppSettingsSegmentedControl(
                        options = themeOptions,
                        selectedOption = selectedTheme,
                        label = themeLabel,
                        onSelect = onThemeSelected,
                    )
                }
                1 -> AppSettingsIconSwitchItem(
                    icon = SettingsSystemColorSchemeIcon,
                    title = systemColorSchemeTitle,
                    checked = useSystemColorScheme,
                    shape = shape,
                    onCheckedChange = onSystemColorSchemeChange,
                )
                2 -> AppSettingsIconSwitchItem(
                    icon = SettingsAmoledIcon,
                    title = amoledTitle,
                    checked = useAmoledTheme,
                    shape = shape,
                    onCheckedChange = onAmoledChange,
                )
            }
        }
    }
}

@Composable
fun AppSettingsPreferencesSection(
    sectionTitle: String,
    languageTitle: String,
    languageOptions: List<LanguageMode>,
    selectedLanguage: LanguageMode,
    languageLabel: @Composable (LanguageMode) -> String,
    onLanguageSelected: (LanguageMode) -> Unit,
    notificationsTitle: String,
    notificationsSubtitle: String,
    notificationsAvailable: Boolean = true,
    onNotificationsClick: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = if (notificationsAvailable) 2 else 1) { index, shape ->
            when {
                index == 0 -> AppSettingsIconVerticalItem(
                    icon = SettingsLanguageIcon,
                    title = languageTitle,
                    shape = shape,
                ) {
                    AppSettingsSegmentedControl(
                        options = languageOptions,
                        selectedOption = selectedLanguage,
                        label = languageLabel,
                        onSelect = onLanguageSelected,
                    )
                }
                notificationsAvailable && index == 1 -> AppSettingsIconActionItem(
                    icon = SettingsNotificationsIcon,
                    title = notificationsTitle,
                    subtitle = notificationsSubtitle,
                    shape = shape,
                    showChevron = true,
                    onClick = onNotificationsClick,
                )
            }
        }
    }
}

@Composable
fun AppSettingsPlayerSection(
    sectionTitle: String,
    autoSkipTitle: String,
    autoSkipEnabled: Boolean,
    onAutoSkipChange: (Boolean) -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, _ ->
            AppSettingsIconSwitchItem(
                icon = SettingsAutoSkipIcon,
                title = autoSkipTitle,
                checked = autoSkipEnabled,
                shape = CircleShape,
                onCheckedChange = onAutoSkipChange,
            )
        }
    }
}

@Composable
fun AppSettingsExperimentalSection(
    sectionTitle: String,
    discordIcon: ImageVector = Icons.Outlined.Code,
    discordIconContent: (@Composable (Modifier) -> Unit)? = null,
    discordTitle: String,
    discordEnabled: Boolean,
    onDiscordClick: () -> Unit,
    onDiscordChange: (Boolean) -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, shape ->
            AppSettingsIconToggleItem(
                icon = discordIcon,
                title = discordTitle,
                checked = discordEnabled,
                shape = shape,
                onClick = onDiscordClick,
                onCheckedChange = onDiscordChange,
                iconContent = discordIconContent,
            )
        }
    }
}

@Composable
fun AppSettingsUpdatesSection(
    sectionTitle: String,
    checkForUpdatesTitle: String,
    onCheckForUpdates: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, _ ->
            AppSettingsIconActionItem(
                icon = SettingsUpdatesIcon,
                title = checkForUpdatesTitle,
                shape = CircleShape,
                onClick = onCheckForUpdates,
            )
        }
    }
}

@Composable
fun AppSettingsSupportSection(
    sectionTitle: String,
    exportLogsTitle: String,
    onExportLogs: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, _ ->
            AppSettingsIconActionItem(
                icon = SettingsExportLogsIcon,
                title = exportLogsTitle,
                shape = CircleShape,
                onClick = onExportLogs,
            )
        }
    }
}
