package org.akkirrai.hibiki.app.destination.settings

import androidx.compose.foundation.lazy.LazyListState
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
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
