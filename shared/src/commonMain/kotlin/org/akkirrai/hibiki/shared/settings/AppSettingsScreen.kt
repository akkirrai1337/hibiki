package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class AppSettingsScreenLabels(
    val appearance: String,
    val theme: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val systemColorScheme: String,
    val amoled: String,
    val preferences: String,
    val language: String,
    val languageSystem: String,
    val languageRussian: String,
    val languageEnglish: String,
    val notifications: String,
    val notificationsStatus: String,
    val player: String,
    val autoSkip: String,
    val experimental: String,
    val discord: String,
    val updates: String,
    val checkUpdates: String,
    val support: String,
    val exportLogs: String,
    val appName: String,
    val versionName: String,
)

@Composable
fun AppSettingsScreen(
    languageMode: LanguageMode,
    darkTheme: Boolean,
    labels: AppSettingsScreenLabels,
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = SettingsScreenDefaultBottomContentPadding,
    onLanguageModeChange: (LanguageMode) -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onSystemColorSchemeChange: (Boolean) -> Unit = {},
    onAmoledChange: (Boolean) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAutoSkipChange: (Boolean) -> Unit = {},
    onDiscordClick: () -> Unit = {},
    onDiscordChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onGitHubClick: () -> Unit = {},
) {
    AppSettingsContentList(
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
        content = {
        item(key = SettingsSection.Appearance.key) {
            AppSettingsAppearanceSection(
                sectionTitle = labels.appearance,
                themeTitle = labels.theme,
                themeOptions = themeModeOptions,
                selectedTheme = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
                themeLabel = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> labels.themeSystem
                        ThemeMode.LIGHT -> labels.themeLight
                        ThemeMode.DARK -> labels.themeDark
                    }
                },
                onThemeSelected = { mode -> onThemeChange(mode == ThemeMode.DARK) },
                systemColorSchemeTitle = labels.systemColorScheme,
                useSystemColorScheme = false,
                onSystemColorSchemeChange = onSystemColorSchemeChange,
                amoledTitle = labels.amoled,
                useAmoledTheme = false,
                onAmoledChange = onAmoledChange,
            )
        }
        item(key = SettingsSection.Preferences.key) {
            AppSettingsPreferencesSection(
                sectionTitle = labels.preferences,
                languageTitle = labels.language,
                languageOptions = languageModeOptions,
                selectedLanguage = languageMode,
                languageLabel = { mode ->
                    when (mode) {
                        LanguageMode.SYSTEM -> labels.languageSystem
                        LanguageMode.RUSSIAN -> labels.languageRussian
                        LanguageMode.ENGLISH -> labels.languageEnglish
                    }
                },
                onLanguageSelected = onLanguageModeChange,
                notificationsTitle = labels.notifications,
                notificationsSubtitle = labels.notificationsStatus,
                onNotificationsClick = onNotificationsClick,
            )
        }
        item(key = SettingsSection.Player.key) {
            AppSettingsPlayerSection(
                sectionTitle = labels.player,
                autoSkipTitle = labels.autoSkip,
                autoSkipEnabled = false,
                onAutoSkipChange = onAutoSkipChange,
            )
        }
        item(key = SettingsSection.Experimental.key) {
            AppSettingsExperimentalSection(
                sectionTitle = labels.experimental,
                discordIcon = Icons.Outlined.Code,
                discordTitle = labels.discord,
                discordEnabled = false,
                onDiscordClick = onDiscordClick,
                onDiscordChange = onDiscordChange,
            )
        }
        item(key = SettingsSection.Updates.key) {
            AppSettingsUpdatesSection(
                sectionTitle = labels.updates,
                checkForUpdatesTitle = labels.checkUpdates,
                onCheckForUpdates = onCheckForUpdates,
            )
        }
        item(key = SettingsSection.Support.key) {
            AppSettingsSupportSection(
                sectionTitle = labels.support,
                exportLogsTitle = labels.exportLogs,
                onExportLogs = onExportLogs,
            )
        }
        item(key = SettingsSection.About.key) {
            AppSettingsAboutCard(
                appName = labels.appName,
                versionName = labels.versionName,
                appIconContent = { iconModifier ->
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = iconModifier)
                },
                githubIconContent = { iconModifier ->
                    Icon(Icons.Outlined.Code, contentDescription = null, modifier = iconModifier)
                },
                onGitHubClick = onGitHubClick,
            )
        }
        },
    )
}
