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
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.textKey
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

data class SettingsScreenState(
    val languageMode: LanguageMode,
    val darkTheme: Boolean,
    val useSystemColorScheme: Boolean,
    val useAmoledTheme: Boolean,
    val autoSkipSegments: Boolean,
    val versionName: String,
    val notificationPermissionState: NotificationPermissionState,
    val themeMode: ThemeMode? = null,
    val discordAvailable: Boolean = true,
    val notificationsAvailable: Boolean = true,
    val discordEnabled: Boolean = false,
    val showUpdates: Boolean = true,
    val showBackButton: Boolean = false,
)

data class SettingsScreenActions(
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
    val onBackClick: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = SettingsScreenDefaultBottomContentPadding,
    listState: LazyListState = rememberLazyListState(),
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
            topContentPadding = if (state.showBackButton) {
                settingsContentTopPaddingWithBackButton(topSystemInset)
            } else {
                SettingsContentTopPadding
            },
            modifier = Modifier.fillMaxSize(),
            content = {
        if (!state.showBackButton) {
            item(key = "settings-header") {
                Text(
                    text = appText(AppTextKey.Settings),
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
                sectionTitle = appText(AppTextKey.SettingsAppearance),
                themeTitle = appText(AppTextKey.SettingsTheme),
                themeOptions = themeModeOptions,
                selectedTheme = state.themeMode ?: if (state.darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
                themeLabel = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> appText(AppTextKey.ThemeSystem)
                        ThemeMode.LIGHT -> appText(AppTextKey.ThemeLight)
                        ThemeMode.DARK -> appText(AppTextKey.ThemeDark)
                    }
                },
                onThemeSelected = { mode ->
                    actions.onThemeModeChange?.invoke(mode) ?: actions.onThemeChange(mode == ThemeMode.DARK)
                },
                systemColorSchemeTitle = appText(AppTextKey.SettingsSystemColorScheme),
                useSystemColorScheme = state.useSystemColorScheme,
                onSystemColorSchemeChange = actions.onSystemColorSchemeChange,
                amoledTitle = appText(AppTextKey.SettingsAmoled),
                useAmoledTheme = state.useAmoledTheme,
                onAmoledChange = actions.onAmoledChange,
            )
        }
        item(key = SettingsSection.Preferences.key) {
            AppSettingsPreferencesSection(
                sectionTitle = appText(AppTextKey.SettingsPreferences),
                languageTitle = appText(AppTextKey.SettingsLanguage),
                languageOptions = languageModeOptions,
                selectedLanguage = state.languageMode,
                languageLabel = { mode ->
                    when (mode) {
                        LanguageMode.SYSTEM -> appText(AppTextKey.LanguageSystem)
                        LanguageMode.RUSSIAN -> appText(AppTextKey.LanguageRussian)
                        LanguageMode.ENGLISH -> appText(AppTextKey.LanguageEnglish)
                    }
                },
                onLanguageSelected = actions.onLanguageModeChange,
                notificationsTitle = appText(AppTextKey.SettingsNotifications),
                notificationsSubtitle = appText(state.notificationPermissionState.textKey()),
                notificationsAvailable = state.notificationsAvailable,
                onNotificationsClick = actions.onNotificationsClick,
            )
        }
        item(key = SettingsSection.Player.key) {
            AppSettingsPlayerSection(
                sectionTitle = appText(AppTextKey.SettingsPlayer),
                autoSkipTitle = appText(AppTextKey.SettingsAutoSkip),
                autoSkipEnabled = state.autoSkipSegments,
                onAutoSkipChange = actions.onAutoSkipChange,
            )
        }
        if (state.discordAvailable) {
            item(key = SettingsSection.Experimental.key) {
                AppSettingsExperimentalSection(
                    sectionTitle = appText(AppTextKey.SettingsExperimental),
                    discordIconContent = { iconModifier ->
                        Image(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = null,
                            modifier = iconModifier,
                        )
                    },
                    discordTitle = appText(AppTextKey.SettingsDiscord),
                    discordEnabled = state.discordEnabled,
                    onDiscordClick = actions.onDiscordClick,
                    onDiscordChange = actions.onDiscordChange,
                )
            }
        }
        if (state.showUpdates) {
            item(key = SettingsSection.Updates.key) {
                AppSettingsUpdatesSection(
                    sectionTitle = appText(AppTextKey.SettingsUpdates),
                    checkForUpdatesTitle = appText(AppTextKey.SettingsCheckUpdates),
                    onCheckForUpdates = actions.onCheckForUpdates,
                )
            }
        }
        item(key = SettingsSection.Support.key) {
            AppSettingsSupportSection(
                sectionTitle = appText(AppTextKey.SettingsSupport),
                exportLogsTitle = appText(AppTextKey.SettingsExportLogs),
                onExportLogs = actions.onExportLogs,
            )
        }
        item(key = SettingsSection.About.key) {
            AppSettingsSection(title = appText(AppTextKey.SettingsAbout)) {
                AppSettingsAboutCard(
                    appName = appText(AppTextKey.AppName),
                    versionName = state.versionName,
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
                )
            }
        }
            },
        )
        if (state.showBackButton) {
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
                    IconButton(onClick = actions.onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = appText(AppTextKey.Back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        text = appText(AppTextKey.Settings),
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
