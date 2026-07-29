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
        useSystemColorScheme = if (defaults.objectForKey(USE_SYSTEM_COLOR_SCHEME_KEY) == null) {
            true
        } else {
            defaults.boolForKey(USE_SYSTEM_COLOR_SCHEME_KEY)
        },
        useAmoledTheme = defaults.boolForKey(AMOLED_THEME_KEY),
        autoSkipSegments = defaults.boolForKey(AUTO_SKIP_SEGMENTS_KEY),
        onboardingCompleted = defaults.boolForKey(ONBOARDING_COMPLETED_KEY),
        selectedSourceId = defaults.stringForKey(SELECTED_SOURCE_ID_KEY),
    )

    override fun save(state: AppSettingsState) {
        defaults.setObject(state.languageMode.name, forKey = LANGUAGE_MODE_KEY)
        defaults.setBool(state.darkTheme, forKey = DARK_THEME_KEY)
        defaults.setBool(state.useSystemColorScheme, forKey = USE_SYSTEM_COLOR_SCHEME_KEY)
        defaults.setBool(state.useAmoledTheme, forKey = AMOLED_THEME_KEY)
        defaults.setBool(state.autoSkipSegments, forKey = AUTO_SKIP_SEGMENTS_KEY)
        defaults.setBool(state.onboardingCompleted, forKey = ONBOARDING_COMPLETED_KEY)
        state.selectedSourceId?.let { sourceId ->
            defaults.setObject(sourceId, forKey = SELECTED_SOURCE_ID_KEY)
        }
    }

    private companion object {
        const val LANGUAGE_MODE_KEY = "hibiki.language_mode"
        const val DARK_THEME_KEY = "hibiki.dark_theme"
        const val USE_SYSTEM_COLOR_SCHEME_KEY = "hibiki.use_system_color_scheme"
        const val AMOLED_THEME_KEY = "hibiki.amoled_theme"
        const val AUTO_SKIP_SEGMENTS_KEY = "hibiki.auto_skip_segments"
        const val ONBOARDING_COMPLETED_KEY = "hibiki.onboarding_completed"
        const val SELECTED_SOURCE_ID_KEY = "hibiki.selected_source_id"
    }
}
