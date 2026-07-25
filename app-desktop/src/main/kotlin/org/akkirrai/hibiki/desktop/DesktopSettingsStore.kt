package org.akkirrai.hibiki.desktop

import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.LanguageMode

class DesktopSettingsStore : AppSettingsStore {
    private val preferences = Preferences.userNodeForPackage(DesktopSettingsStore::class.java)

    override fun load(): AppSettingsState {
        val languageMode = when (preferences.get(LANGUAGE_KEY, LanguageMode.SYSTEM.name)) {
            LanguageMode.RUSSIAN.name -> LanguageMode.RUSSIAN
            LanguageMode.ENGLISH.name -> LanguageMode.ENGLISH
            else -> LanguageMode.SYSTEM
        }
        return AppSettingsState(
            languageMode = languageMode,
            darkTheme = preferences.getBoolean(DARK_THEME_KEY, false),
        )
    }

    override fun save(state: AppSettingsState) {
        preferences.put(LANGUAGE_KEY, state.languageMode.name)
        preferences.putBoolean(DARK_THEME_KEY, state.darkTheme)
        preferences.flush()
    }

    private companion object {
        const val LANGUAGE_KEY = "languageMode"
        const val DARK_THEME_KEY = "darkTheme"
    }
}
