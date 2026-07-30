package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.ic_discord
import hibiki.shared.generated.resources.ic_github
import hibiki.shared.generated.resources.hibiki_app_icon
import org.jetbrains.compose.resources.painterResource

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
    useSystemColorScheme: Boolean,
    useAmoledTheme: Boolean,
    autoSkipSegments: Boolean,
    themeMode: ThemeMode? = null,
    discordEnabled: Boolean = false,
    showUpdates: Boolean = true,
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = SettingsScreenDefaultBottomContentPadding,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    backContentDescription: String = "Back",
    onLanguageModeChange: (LanguageMode) -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onThemeModeChange: ((ThemeMode) -> Unit)? = null,
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
        if (showBackButton) {
            item(key = "settings_back") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backContentDescription,
                        )
                    }
                }
            }
        }
        item(key = SettingsSection.Appearance.key) {
            AppSettingsAppearanceSection(
                sectionTitle = labels.appearance,
                themeTitle = labels.theme,
                themeOptions = themeModeOptions,
                selectedTheme = themeMode ?: if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
                themeLabel = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> labels.themeSystem
                        ThemeMode.LIGHT -> labels.themeLight
                        ThemeMode.DARK -> labels.themeDark
                    }
                },
                onThemeSelected = { mode ->
                    onThemeModeChange?.invoke(mode) ?: onThemeChange(mode == ThemeMode.DARK)
                },
                systemColorSchemeTitle = labels.systemColorScheme,
                useSystemColorScheme = useSystemColorScheme,
                onSystemColorSchemeChange = onSystemColorSchemeChange,
                amoledTitle = labels.amoled,
                useAmoledTheme = useAmoledTheme,
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
                autoSkipEnabled = autoSkipSegments,
                onAutoSkipChange = onAutoSkipChange,
            )
        }
        item(key = SettingsSection.Experimental.key) {
            AppSettingsExperimentalSection(
                sectionTitle = labels.experimental,
                discordIconContent = { iconModifier ->
                    Image(
                        painter = painterResource(Res.drawable.ic_discord),
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                },
                discordTitle = labels.discord,
                discordEnabled = discordEnabled,
                onDiscordClick = onDiscordClick,
                onDiscordChange = onDiscordChange,
            )
        }
        if (showUpdates) {
            item(key = SettingsSection.Updates.key) {
                AppSettingsUpdatesSection(
                    sectionTitle = labels.updates,
                    checkForUpdatesTitle = labels.checkUpdates,
                    onCheckForUpdates = onCheckForUpdates,
                )
            }
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
                    Image(
                        painter = painterResource(Res.drawable.hibiki_app_icon),
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                },
                githubIconContent = { iconModifier ->
                    Image(
                        painter = painterResource(Res.drawable.ic_github),
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                },
                onGitHubClick = onGitHubClick,
            )
        }
        },
    )
}
