package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.SettingsDestinationContent
import org.akkirrai.hibiki.shared.settings.ThemeMode

@Composable
internal fun SettingsDestinationRoute(
    profileData: LocalProfileData,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appVersionName: String,
    useSystemColorScheme: Boolean,
    useAmoledTheme: Boolean,
    autoSkipSegments: Boolean,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onAutoSkipChange: (Boolean) -> Unit,
    onConfigureNotifications: () -> Unit,
    notificationPermissionState: NotificationPermissionState,
    showSettingsBackButton: Boolean,
    onSettingsBack: () -> Unit,
    onGitHubClick: () -> Unit,
    discordEnabled: Boolean,
    discordAvailable: Boolean,
    onDiscordClick: () -> Unit,
    onDiscordChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onExportLogs: () -> Unit,
    notificationsAvailable: Boolean,
    settingsListState: androidx.compose.foundation.lazy.LazyListState,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
) {
    SettingsDestinationContent(
        profileData = profileData,
        languageMode = languageMode,
        onLanguageModeChange = onLanguageModeChange,
        darkTheme = darkTheme,
        onThemeChange = onThemeChange,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        appVersionName = appVersionName,
        useSystemColorScheme = useSystemColorScheme,
        useAmoledTheme = useAmoledTheme,
        autoSkipSegments = autoSkipSegments,
        onSystemColorSchemeChange = onSystemColorSchemeChange,
        onAmoledChange = onAmoledChange,
        onAutoSkipChange = onAutoSkipChange,
        onConfigureNotifications = onConfigureNotifications,
        notificationPermissionState = notificationPermissionState,
        showSettingsBackButton = showSettingsBackButton,
        onSettingsBack = onSettingsBack,
        onGitHubClick = onGitHubClick,
        discordEnabled = discordEnabled,
        discordAvailable = discordAvailable,
        onDiscordClick = onDiscordClick,
        onDiscordChange = onDiscordChange,
        onCheckForUpdates = onCheckForUpdates,
        onExportLogs = onExportLogs,
        notificationsAvailable = notificationsAvailable,
        settingsListState = settingsListState,
        bottomContentPadding = bottomContentPadding,
    )
}
