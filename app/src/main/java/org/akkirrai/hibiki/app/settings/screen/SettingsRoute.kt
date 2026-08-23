package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.core.source.ExternalSourceRepositoryUiState

@Composable
internal fun SettingsRoute(
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
    SettingsScreen(
        state = SettingsScreenState(
            languageMode = languageMode,
            darkTheme = darkTheme,
            themeMode = themeMode,
            versionName = versionName,
            useSystemColorScheme = useSystemColorScheme,
            useAmoledTheme = useAmoledTheme,
            autoSkipSegments = autoSkipSegments,
            notificationPermissionState = notificationPermissionState,
            discordEnabled = discordEnabled,
            discordAvailable = discordAvailable,
            notificationsAvailable = notificationsAvailable,
            externalSourcesCount = externalSourceRepositoryState?.repositories?.size ?: 0,
            showBackButton = showBackButton,
        ),
        actions = SettingsScreenActions(
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
            onNotificationsClick = onConfigureNotifications,
            onGitHubClick = onGitHubClick,
            onExternalSourcesClick = onExternalSourcesClick,
            onBackClick = onBackClick,
        ),
        listState = listState,
        modifier = Modifier.fillMaxSize(),
        bottomContentPadding = bottomContentPadding,
    )
}
