package org.akkirrai.hibiki.shared.app.shell.settings

import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.ThemeMode

internal class HibikiSettingsActions(
    val onLanguageModeChange: (LanguageMode) -> Unit,
    val onThemeChange: (Boolean) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onSystemColorSchemeChange: (Boolean) -> Unit,
    val onAmoledChange: (Boolean) -> Unit,
    val onAutoSkipChange: (Boolean) -> Unit,
)

internal fun createHibikiSettingsActions(
    saveSettings: () -> Unit,
    setLanguageMode: (LanguageMode) -> Unit,
    setDarkTheme: (Boolean) -> Unit,
    setThemeMode: (ThemeMode) -> Unit,
    setUseSystemColorScheme: (Boolean) -> Unit,
    setUseAmoledTheme: (Boolean) -> Unit,
    setAutoSkipSegments: (Boolean) -> Unit,
): HibikiSettingsActions = HibikiSettingsActions(
    onLanguageModeChange = { mode ->
        setLanguageMode(mode)
        saveSettings()
    },
    onThemeChange = { dark ->
        setDarkTheme(dark)
        saveSettings()
    },
    onThemeModeChange = { mode ->
        setThemeMode(mode)
        if (mode != ThemeMode.SYSTEM) setDarkTheme(mode == ThemeMode.DARK)
        saveSettings()
    },
    onSystemColorSchemeChange = { enabled ->
        setUseSystemColorScheme(enabled)
        saveSettings()
    },
    onAmoledChange = { enabled ->
        setUseAmoledTheme(enabled)
        saveSettings()
    },
    onAutoSkipChange = { enabled ->
        setAutoSkipSegments(enabled)
        saveSettings()
    },
)
