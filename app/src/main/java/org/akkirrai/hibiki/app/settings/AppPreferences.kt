package org.akkirrai.hibiki.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class LanguageMode(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    ENGLISH("en")
}

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val autoSkipSegments: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val playbackSpeed: Float = 1f,
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME_MODE,
            KEY_LANGUAGE_MODE,
            KEY_AUTO_SKIP_SEGMENTS,
            KEY_AUTO_PLAY_NEXT_EPISODE,
            KEY_PLAYBACK_SPEED -> {
                _state.value = readState(prefs)
            }
        }
    }
    private val _state = MutableStateFlow(readState(prefs))

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    val state: StateFlow<AppPreferencesState> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setLanguageMode(mode: LanguageMode) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.name).apply()
    }

    fun setAutoSkipSegments(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SKIP_SEGMENTS, enabled).apply()
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, enabled).apply()
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, normalizePlaybackSpeed(speed)).apply()
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    companion object {
        const val PREFS_NAME = "hibiki_app_preferences"
        const val KEY_AUTO_SKIP_SEGMENTS = "auto_skip_segments"
        const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE_MODE = "language_mode"

        fun readState(context: Context): AppPreferencesState {
            return readState(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        }

        private fun readState(prefs: SharedPreferences): AppPreferencesState {
            return AppPreferencesState(
                themeMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
                    ?.let(ThemeMode::valueOf)
                    ?: ThemeMode.SYSTEM,
                languageMode = prefs.getString(KEY_LANGUAGE_MODE, LanguageMode.SYSTEM.name)
                    ?.let(LanguageMode::valueOf)
                    ?: LanguageMode.SYSTEM,
                autoSkipSegments = prefs.getBoolean(KEY_AUTO_SKIP_SEGMENTS, false),
                autoPlayNextEpisode = prefs.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true),
                playbackSpeed = normalizePlaybackSpeed(prefs.getFloat(KEY_PLAYBACK_SPEED, 1f)),
            )
        }

        private fun normalizePlaybackSpeed(speed: Float): Float {
            return when (speed) {
                0.75f, 1f, 1.25f, 1.5f, 2f -> speed
                else -> 1f
            }
        }
    }
}
