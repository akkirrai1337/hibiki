package org.akkirrai.hibiki.shared.settings

import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.ThemeMode

internal fun saveHibikiAppSettings(
    settingsStore: AppSettingsStore,
    languageMode: LanguageMode,
    darkTheme: Boolean,
    themeMode: ThemeMode,
    useSystemColorScheme: Boolean,
    useAmoledTheme: Boolean,
    autoSkipSegments: Boolean,
    autoPlayNextEpisode: Boolean,
    onboardingCompleted: Boolean,
    selectedSourceId: String?,
) {
    settingsStore.save(
        settingsStore.load().copy(
            languageMode = languageMode,
            darkTheme = darkTheme,
            themeMode = themeMode,
            useSystemColorScheme = useSystemColorScheme,
            useAmoledTheme = useAmoledTheme,
            autoSkipSegments = autoSkipSegments,
            autoPlayNextEpisode = autoPlayNextEpisode,
            onboardingCompleted = onboardingCompleted,
            selectedSourceId = selectedSourceId,
        ),
    )
}
