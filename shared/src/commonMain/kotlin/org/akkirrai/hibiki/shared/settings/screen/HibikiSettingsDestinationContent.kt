package org.akkirrai.hibiki.shared.settings

import org.akkirrai.hibiki.shared.app.externalSourceRepositoryLabels

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.settings.AppExternalSourcesScreen
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun SettingsDestinationContent(
    showExternalSources: Boolean,
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
    externalSourceRepositoryState: ExternalSourceRepositoryUiState?,
    externalSourceRepositoryController: ExternalSourceRepositoryController?,
    settingsListState: LazyListState,
    externalSourcesListState: LazyListState,
    bottomContentPadding: Dp,
    onExternalSourcesClick: () -> Unit,
) {
    if (showExternalSources) {
        AppExternalSourcesScreen(
            state = externalSourceRepositoryState ?: ExternalSourceRepositoryUiState(),
            labels = externalSourceRepositoryLabels(),
            bottomContentPadding = bottomContentPadding,
            listState = externalSourcesListState,
            onBackClick = onSettingsBack,
            backContentDescription = appText(AppTextKey.Back),
            onAddRepository = externalSourceRepositoryController?.let { controller ->
                { url -> controller.addRepository(url) }
            } ?: {},
            onRemoveRepository = externalSourceRepositoryController?.let { it::removeRepository } ?: {},
            onRefresh = externalSourceRepositoryController?.let { it::refreshRepositories } ?: {},
            onInstallPackage = externalSourceRepositoryController?.let { controller ->
                { sourceId -> controller.installPackage(sourceId) }
            } ?: {},
            onRollbackPackage = externalSourceRepositoryController?.let { controller ->
                { sourceId -> controller.rollbackPackage(sourceId) }
            } ?: {},
        )
    } else {
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
            externalSourceRepositoryState = externalSourceRepositoryState,
            onExternalSourcesClick = onExternalSourcesClick,
            listState = settingsListState,
            bottomContentPadding = bottomContentPadding,
        )
    }
}
