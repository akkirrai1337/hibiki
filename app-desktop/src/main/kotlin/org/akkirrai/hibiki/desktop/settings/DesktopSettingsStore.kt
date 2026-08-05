package org.akkirrai.hibiki.desktop.settings

import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
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
            themeMode = preferences.get(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
                .let { value -> runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM) },
            useSystemColorScheme = preferences.getBoolean(USE_SYSTEM_COLOR_SCHEME_KEY, true),
            useAmoledTheme = preferences.getBoolean(AMOLED_THEME_KEY, false),
            autoSkipSegments = preferences.getBoolean(AUTO_SKIP_KEY, false),
            autoPlayNextEpisode = preferences.getBoolean(AUTO_PLAY_NEXT_KEY, true),
            playbackSpeed = preferences.getFloat(PLAYBACK_SPEED_KEY, 1f),
            videoScaleMode = preferences.get(SCALE_MODE_KEY, VideoScaleMode.FIT.name)
                .let { value -> runCatching { VideoScaleMode.valueOf(value) }.getOrDefault(VideoScaleMode.FIT) },
            onboardingCompleted = preferences.getBoolean(ONBOARDING_COMPLETED_KEY, false),
            selectedSourceId = preferences.get(SELECTED_SOURCE_ID_KEY, "").ifBlank { null },
            notificationPermissionState = preferences.get(NOTIFICATION_PERMISSION_STATE_KEY, NotificationPermissionState.NOT_ASKED.name)
                .let { value -> runCatching { NotificationPermissionState.valueOf(value) }.getOrDefault(NotificationPermissionState.NOT_ASKED) },
        )
    }

    override fun save(state: AppSettingsState) {
        preferences.put(LANGUAGE_KEY, state.languageMode.name)
        preferences.putBoolean(DARK_THEME_KEY, state.darkTheme)
        preferences.put(THEME_MODE_KEY, state.themeMode.name)
        preferences.putBoolean(USE_SYSTEM_COLOR_SCHEME_KEY, state.useSystemColorScheme)
        preferences.putBoolean(AMOLED_THEME_KEY, state.useAmoledTheme)
        preferences.putBoolean(AUTO_SKIP_KEY, state.autoSkipSegments)
        preferences.putBoolean(AUTO_PLAY_NEXT_KEY, state.autoPlayNextEpisode)
        preferences.putFloat(PLAYBACK_SPEED_KEY, state.playbackSpeed)
        preferences.put(SCALE_MODE_KEY, state.videoScaleMode.name)
        preferences.putBoolean(ONBOARDING_COMPLETED_KEY, state.onboardingCompleted)
        state.selectedSourceId?.let { preferences.put(SELECTED_SOURCE_ID_KEY, it) }
        preferences.put(NOTIFICATION_PERMISSION_STATE_KEY, state.notificationPermissionState.name)
        preferences.flush()
    }

    private companion object {
        const val LANGUAGE_KEY = "languageMode"
        const val DARK_THEME_KEY = "darkTheme"
        const val THEME_MODE_KEY = "themeMode"
        const val USE_SYSTEM_COLOR_SCHEME_KEY = "useSystemColorScheme"
        const val AMOLED_THEME_KEY = "useAmoledTheme"
        const val AUTO_SKIP_KEY = "autoSkipSegments"
        const val AUTO_PLAY_NEXT_KEY = "autoPlayNextEpisode"
        const val PLAYBACK_SPEED_KEY = "playbackSpeed"
        const val SCALE_MODE_KEY = "videoScaleMode"
        const val ONBOARDING_COMPLETED_KEY = "onboardingCompleted"
        const val SELECTED_SOURCE_ID_KEY = "selectedSourceId"
        const val NOTIFICATION_PERMISSION_STATE_KEY = "notificationPermissionState"
    }
}
