package org.akkirrai.hibiki.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.ThemeMode

class HibikiSettingsPersistenceTest {
    @Test
    fun savesShellSettingsWithoutResettingPlaybackPreferences() {
        val store = InMemoryAppSettingsStore(
            AppSettingsState(
                playbackSpeed = 1.5f,
                videoScaleMode = VideoScaleMode.CROP,
                selectedSourceId = "old-source",
            ),
        )

        saveHibikiAppSettings(
            settingsStore = store,
            languageMode = LanguageMode.RUSSIAN,
            darkTheme = true,
            themeMode = ThemeMode.DARK,
            useSystemColorScheme = false,
            useAmoledTheme = true,
            autoSkipSegments = true,
            autoPlayNextEpisode = false,
            onboardingCompleted = true,
            selectedSourceId = "new-source",
        )

        assertEquals(
            AppSettingsState(
                languageMode = LanguageMode.RUSSIAN,
                darkTheme = true,
                themeMode = ThemeMode.DARK,
                useSystemColorScheme = false,
                useAmoledTheme = true,
                autoSkipSegments = true,
                autoPlayNextEpisode = false,
                playbackSpeed = 1.5f,
                videoScaleMode = VideoScaleMode.CROP,
                onboardingCompleted = true,
                selectedSourceId = "new-source",
            ),
            store.load(),
        )
    }
}
