package org.akkirrai.hibiki.desktop

import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.player.VideoScaleMode

class DesktopSettingsStore(
    private val preferences: Preferences = Preferences.userNodeForPackage(DesktopSettingsStore::class.java),
) : AppSettingsStore {

    override fun load(): AppSettingsState {
        val languageMode = when (preferences.get(LANGUAGE_KEY, LanguageMode.SYSTEM.name)) {
            LanguageMode.RUSSIAN.name -> LanguageMode.RUSSIAN
            LanguageMode.ENGLISH.name -> LanguageMode.ENGLISH
            else -> LanguageMode.SYSTEM
        }
        return AppSettingsState(
            languageMode = languageMode,
            darkTheme = preferences.getBoolean(DARK_THEME_KEY, false),
            autoSkipSegments = preferences.getBoolean(AUTO_SKIP_KEY, false),
            autoPlayNextEpisode = preferences.getBoolean(AUTO_PLAY_NEXT_KEY, true),
            playbackSpeed = preferences.getFloat(PLAYBACK_SPEED_KEY, 1f),
            videoScaleMode = preferences.get(SCALE_MODE_KEY, VideoScaleMode.FIT.name)
                .let { value -> runCatching { VideoScaleMode.valueOf(value) }.getOrDefault(VideoScaleMode.FIT) },
        )
    }

    override fun save(state: AppSettingsState) {
        preferences.put(LANGUAGE_KEY, state.languageMode.name)
        preferences.putBoolean(DARK_THEME_KEY, state.darkTheme)
        preferences.putBoolean(AUTO_SKIP_KEY, state.autoSkipSegments)
        preferences.putBoolean(AUTO_PLAY_NEXT_KEY, state.autoPlayNextEpisode)
        preferences.putFloat(PLAYBACK_SPEED_KEY, state.playbackSpeed)
        preferences.put(SCALE_MODE_KEY, state.videoScaleMode.name)
        preferences.flush()
    }

    private companion object {
        const val LANGUAGE_KEY = "languageMode"
        const val DARK_THEME_KEY = "darkTheme"
        const val AUTO_SKIP_KEY = "autoSkipSegments"
        const val AUTO_PLAY_NEXT_KEY = "autoPlayNextEpisode"
        const val PLAYBACK_SPEED_KEY = "playbackSpeed"
        const val SCALE_MODE_KEY = "videoScaleMode"
    }
}
