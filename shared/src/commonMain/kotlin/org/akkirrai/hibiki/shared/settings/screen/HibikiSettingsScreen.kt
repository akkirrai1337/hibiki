package org.akkirrai.hibiki.shared.settings

import org.akkirrai.hibiki.shared.app.textKey

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.settings.AppSettingsScreen
import org.akkirrai.hibiki.shared.settings.AppSettingsScreenLabels
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun SettingsScreen(
    profileData: LocalProfileData,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    versionName: String,
    useSystemColorScheme: Boolean,
    useAmoledTheme: Boolean,
    autoSkipSegments: Boolean,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onAutoSkipChange: (Boolean) -> Unit,
    onConfigureNotifications: () -> Unit,
    notificationPermissionState: NotificationPermissionState,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onGitHubClick: () -> Unit = {},
    discordEnabled: Boolean = false,
    discordAvailable: Boolean = true,
    onDiscordClick: () -> Unit = {},
    onDiscordChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    notificationsAvailable: Boolean = true,
    externalSourceRepositoryState: ExternalSourceRepositoryUiState? = null,
    onExternalSourcesClick: () -> Unit = {},
    listState: LazyListState,
    bottomContentPadding: Dp,
) {
    AppSettingsScreen(
        languageMode = languageMode,
        darkTheme = darkTheme,
        themeMode = themeMode,
        useSystemColorScheme = useSystemColorScheme,
        useAmoledTheme = useAmoledTheme,
        autoSkipSegments = autoSkipSegments,
        discordEnabled = discordEnabled,
        discordAvailable = discordAvailable,
        labels = AppSettingsScreenLabels(
            title = appText(AppTextKey.Settings),
            appearance = appText(AppTextKey.SettingsAppearance),
            theme = appText(AppTextKey.SettingsTheme),
            themeSystem = appText(AppTextKey.ThemeSystem),
            themeLight = appText(AppTextKey.ThemeLight),
            themeDark = appText(AppTextKey.ThemeDark),
            systemColorScheme = appText(AppTextKey.SettingsSystemColorScheme),
            amoled = appText(AppTextKey.SettingsAmoled),
            preferences = appText(AppTextKey.SettingsPreferences),
            language = appText(AppTextKey.SettingsLanguage),
            languageSystem = appText(AppTextKey.LanguageSystem),
            languageRussian = appText(AppTextKey.LanguageRussian),
            languageEnglish = appText(AppTextKey.LanguageEnglish),
            notifications = appText(AppTextKey.SettingsNotifications),
            notificationsStatus = appText(notificationPermissionState.textKey()),
            externalSources = appText(AppTextKey.SettingsExternalSources),
            externalSourcesSubtitle = appText(AppTextKey.SettingsExternalSourcesCount)
                .replace("%s", (externalSourceRepositoryState?.repositories?.size ?: 0).toString()),
            player = appText(AppTextKey.SettingsPlayer),
            autoSkip = appText(AppTextKey.SettingsAutoSkip),
            experimental = appText(AppTextKey.SettingsExperimental),
            discord = appText(AppTextKey.SettingsDiscord),
            updates = appText(AppTextKey.SettingsUpdates),
            checkUpdates = appText(AppTextKey.SettingsCheckUpdates),
            support = appText(AppTextKey.SettingsSupport),
            exportLogs = appText(AppTextKey.SettingsExportLogs),
            about = appText(AppTextKey.SettingsAbout),
            appName = appText(AppTextKey.AppName),
            versionName = versionName,
        ),
        onLanguageModeChange = onLanguageModeChange,
        onThemeChange = onThemeChange,
        onThemeModeChange = onThemeModeChange,
        onSystemColorSchemeChange = onSystemColorSchemeChange,
        onAmoledChange = onAmoledChange,
        onAutoSkipChange = onAutoSkipChange,
        onDiscordClick = onDiscordClick,
        onDiscordChange = onDiscordChange,
        onCheckForUpdates = onCheckForUpdates,
        onExportLogs = onExportLogs,
        notificationsAvailable = notificationsAvailable,
        onNotificationsClick = onConfigureNotifications,
        onGitHubClick = onGitHubClick,
        externalSourcesCount = externalSourceRepositoryState?.repositories?.size ?: 0,
        onExternalSourcesClick = onExternalSourcesClick,
        listState = listState,
        modifier = Modifier.fillMaxSize(),
        bottomContentPadding = bottomContentPadding,
        showBackButton = showBackButton,
        onBackClick = onBackClick,
        backContentDescription = appText(AppTextKey.Back),
    )
}
