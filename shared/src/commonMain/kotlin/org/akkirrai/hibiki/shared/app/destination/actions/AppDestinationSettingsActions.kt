package org.akkirrai.hibiki.shared.app.destination.actions

import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.ThemeMode

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
