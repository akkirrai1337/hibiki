package org.akkirrai.hibiki.core.settings

import android.content.Context
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore

/** Android adapter for the shared settings contract backed by existing preferences. */
class AndroidAppSettingsStore(context: Context) : AppSettingsStore, AutoCloseable {
    private val preferences = AppPreferences(context.applicationContext)

    override fun load(): AppSettingsState = preferences.state.value.toSharedState()

    override fun save(state: AppSettingsState) {
        preferences.setLanguageMode(state.languageMode)
        preferences.setThemeMode(if (state.darkTheme) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    override fun close() {
        preferences.close()
    }
}

private fun org.akkirrai.hibiki.app.settings.AppPreferencesState.toSharedState(): AppSettingsState =
    AppSettingsState(
        languageMode = languageMode,
        darkTheme = themeMode == ThemeMode.DARK || useAmoledTheme,
    )
