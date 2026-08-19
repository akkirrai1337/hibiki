package org.akkirrai.hibiki.app.destination.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.profile.LocalProfileData
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.app.settings.SettingsScreen
import org.akkirrai.hibiki.app.settings.ThemeMode

internal data class AppDestinationSettingsActions(
    val onLanguageModeChange: (LanguageMode) -> Unit,
    val onThemeChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onSystemColorSchemeChange: (Boolean) -> Unit,
    val onAmoledChange: (Boolean) -> Unit,
    val onAutoSkipChange: (Boolean) -> Unit,
    val onConfigureNotifications: () -> Unit,
    val onDiscordClick: () -> Unit,
    val onDiscordChange: (Boolean) -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onExportLogs: () -> Unit,
)

internal data class AppDestinationSettingsState(
    val darkTheme: Boolean,
    val themeMode: ThemeMode,
    val appVersionName: String,
    val useSystemColorScheme: Boolean,
    val useAmoledTheme: Boolean,
    val autoSkipSegments: Boolean,
    val notificationPermissionState: NotificationPermissionState,
    val showBackButton: Boolean,
    val discordEnabled: Boolean,
    val discordAvailable: Boolean,
    val notificationsAvailable: Boolean,
)

internal data class AppDestinationSettingsListsState(
    val settings: LazyListState,
)

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
    settingsListState: LazyListState,
    bottomContentPadding: Dp,
) {
    SettingsScreen(
        profileData = profileData,
        languageMode = languageMode,
        onLanguageModeChange = onLanguageModeChange,
        darkTheme = darkTheme,
        onThemeChange = onThemeChange,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        versionName = appVersionName,
        useSystemColorScheme = useSystemColorScheme,
        useAmoledTheme = useAmoledTheme,
        autoSkipSegments = autoSkipSegments,
        onSystemColorSchemeChange = onSystemColorSchemeChange,
        onAmoledChange = onAmoledChange,
        onAutoSkipChange = onAutoSkipChange,
        onConfigureNotifications = onConfigureNotifications,
        notificationPermissionState = notificationPermissionState,
        showBackButton = showSettingsBackButton,
        onBackClick = onSettingsBack,
        onGitHubClick = onGitHubClick,
        discordEnabled = discordEnabled,
        discordAvailable = discordAvailable,
        onDiscordClick = onDiscordClick,
        onDiscordChange = onDiscordChange,
        onCheckForUpdates = onCheckForUpdates,
        onExportLogs = onExportLogs,
        notificationsAvailable = notificationsAvailable,
        listState = settingsListState,
        bottomContentPadding = bottomContentPadding,
    )
}
