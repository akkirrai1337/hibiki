package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.layout.LocalAppLayoutEnvironment

data class AppSettingsScreenLabels(
    val title: String,
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
    val externalSources: String,
    val externalSourcesSubtitle: String,
    val player: String,
    val autoSkip: String,
    val experimental: String,
    val discord: String,
    val updates: String,
    val checkUpdates: String,
    val support: String,
    val exportLogs: String,
    val about: String,
    val appName: String,
    val versionName: String,
)

data class SettingsUiState(
    val languageMode: LanguageMode,
    val darkTheme: Boolean,
    val useSystemColorScheme: Boolean,
    val useAmoledTheme: Boolean,
    val autoSkipSegments: Boolean,
    val themeMode: ThemeMode? = null,
    val discordAvailable: Boolean = true,
    val notificationsAvailable: Boolean = true,
    val discordEnabled: Boolean = false,
    val showUpdates: Boolean = true,
    val externalSourcesCount: Int = 0,
)

data class SettingsActions(
    val onLanguageModeChange: (LanguageMode) -> Unit,
    val onThemeChange: (Boolean) -> Unit,
    val onThemeModeChange: ((ThemeMode) -> Unit)? = null,
    val onSystemColorSchemeChange: (Boolean) -> Unit = {},
    val onAmoledChange: (Boolean) -> Unit = {},
    val onNotificationsClick: () -> Unit = {},
    val onAutoSkipChange: (Boolean) -> Unit = {},
    val onDiscordClick: () -> Unit = {},
    val onDiscordChange: (Boolean) -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onExportLogs: () -> Unit = {},
    val onGitHubClick: () -> Unit = {},
    val onExternalSourcesClick: () -> Unit = {},
)

@Composable
fun AppSettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    labels: AppSettingsScreenLabels,
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = SettingsScreenDefaultBottomContentPadding,
    listState: LazyListState = rememberLazyListState(),
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    backContentDescription: String = "Back",
) {
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    val topSystemInset = if (layoutEnvironment.isProvided) {
        layoutEnvironment.topSystemInset
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    Box(modifier = modifier.fillMaxSize()) {
        AppSettingsContentList(
            bottomContentPadding = bottomContentPadding,
            state = listState,
            topContentPadding = if (showBackButton) {
                settingsContentTopPaddingWithBackButton(topSystemInset)
            } else {
                SettingsContentTopPadding
            },
            modifier = Modifier.fillMaxSize(),
            content = {
        if (!showBackButton) {
            item(key = "settings-header") {
                Text(
                    text = labels.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = SettingsSection.Appearance.key) {
            AppSettingsAppearanceSection(
                sectionTitle = labels.appearance,
                themeTitle = labels.theme,
                themeOptions = themeModeOptions,
                selectedTheme = state.themeMode ?: if (state.darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
                themeLabel = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> labels.themeSystem
                        ThemeMode.LIGHT -> labels.themeLight
                        ThemeMode.DARK -> labels.themeDark
                    }
                },
                onThemeSelected = { mode ->
                    actions.onThemeModeChange?.invoke(mode) ?: actions.onThemeChange(mode == ThemeMode.DARK)
                },
                systemColorSchemeTitle = labels.systemColorScheme,
                useSystemColorScheme = state.useSystemColorScheme,
                onSystemColorSchemeChange = actions.onSystemColorSchemeChange,
                amoledTitle = labels.amoled,
                useAmoledTheme = state.useAmoledTheme,
                onAmoledChange = actions.onAmoledChange,
            )
        }
        item(key = SettingsSection.Preferences.key) {
            AppSettingsPreferencesSection(
                sectionTitle = labels.preferences,
                languageTitle = labels.language,
                languageOptions = languageModeOptions,
                selectedLanguage = state.languageMode,
                languageLabel = { mode ->
                    when (mode) {
                        LanguageMode.SYSTEM -> labels.languageSystem
                        LanguageMode.RUSSIAN -> labels.languageRussian
                        LanguageMode.ENGLISH -> labels.languageEnglish
                    }
                },
                onLanguageSelected = actions.onLanguageModeChange,
                notificationsTitle = labels.notifications,
                notificationsSubtitle = labels.notificationsStatus,
                notificationsAvailable = state.notificationsAvailable,
                onNotificationsClick = actions.onNotificationsClick,
                externalSourcesTitle = labels.externalSources,
                externalSourcesSubtitle = labels.externalSourcesSubtitle,
                externalSourcesCount = state.externalSourcesCount,
                onExternalSourcesClick = actions.onExternalSourcesClick,
            )
        }
        item(key = SettingsSection.Player.key) {
            AppSettingsPlayerSection(
                sectionTitle = labels.player,
                autoSkipTitle = labels.autoSkip,
                autoSkipEnabled = state.autoSkipSegments,
                onAutoSkipChange = actions.onAutoSkipChange,
            )
        }
        if (state.discordAvailable) {
            item(key = SettingsSection.Experimental.key) {
                AppSettingsExperimentalSection(
                    sectionTitle = labels.experimental,
                    discordIconContent = { iconModifier ->
                        Image(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = null,
                            modifier = iconModifier,
                        )
                    },
                    discordTitle = labels.discord,
                    discordEnabled = state.discordEnabled,
                    onDiscordClick = actions.onDiscordClick,
                    onDiscordChange = actions.onDiscordChange,
                )
            }
        }
        if (state.showUpdates) {
            item(key = SettingsSection.Updates.key) {
                AppSettingsUpdatesSection(
                    sectionTitle = labels.updates,
                    checkForUpdatesTitle = labels.checkUpdates,
                    onCheckForUpdates = actions.onCheckForUpdates,
                )
            }
        }
        item(key = SettingsSection.Support.key) {
            AppSettingsSupportSection(
                sectionTitle = labels.support,
                exportLogsTitle = labels.exportLogs,
                onExportLogs = actions.onExportLogs,
            )
        }
        item(key = SettingsSection.About.key) {
            AppSettingsSection(title = labels.about) {
                AppSettingsAboutCard(
                    appName = labels.appName,
                    versionName = labels.versionName,
                    appIconContent = { iconModifier ->
                        Image(
                            painter = painterResource(R.drawable.hibiki_app_icon),
                            contentDescription = null,
                            modifier = iconModifier,
                        )
                    },
                    githubIconContent = { iconModifier ->
                        Image(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = null,
                            modifier = iconModifier,
                        )
                    },
                    onGitHubClick = actions.onGitHubClick,
                )
            }
        }
            },
        )
        if (showBackButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .height(topSystemInset + SettingsBackButtonTopPadding + SettingsBackButtonSize),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = UiDimens.ScreenPadding)
                        .height(SettingsBackButtonSize),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backContentDescription,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        text = labels.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = SettingsBackButtonContentGap),
                    )
                }
            }
        }
    }
}
