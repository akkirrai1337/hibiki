package org.akkirrai.hibiki.shared.settings

import platform.Foundation.NSUserDefaults

internal class IosAppSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppSettingsStore {
    override fun load(): AppSettingsState = AppSettingsState(
        languageMode = defaults.stringForKey(LANGUAGE_MODE_KEY)
            ?.let { raw -> runCatching { LanguageMode.valueOf(raw) }.getOrNull() }
            ?: LanguageMode.SYSTEM,
        darkTheme = defaults.boolForKey(DARK_THEME_KEY),
    )

    override fun save(state: AppSettingsState) {
        defaults.setObject(state.languageMode.name, forKey = LANGUAGE_MODE_KEY)
        defaults.setBool(state.darkTheme, forKey = DARK_THEME_KEY)
    }

    private companion object {
        const val LANGUAGE_MODE_KEY = "hibiki.language_mode"
        const val DARK_THEME_KEY = "hibiki.dark_theme"
    }
}
