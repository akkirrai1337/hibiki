package org.akkirrai.hibiki.shared.app.destination.state

import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.ThemeMode

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
