package org.akkirrai.hibiki.app.settings

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("LocalAppPreferences not provided")
}

val LocalAppPreferencesState = staticCompositionLocalOf {
    AppPreferencesState()
}

val LocalAppLanguage = staticCompositionLocalOf {
    LanguageMode.SYSTEM
}

val LocalThemeMode = staticCompositionLocalOf {
    ThemeMode.SYSTEM
}
