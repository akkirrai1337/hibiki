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
    val forceAdvanceTrendingSlotOnRefresh: Boolean = false,
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_AUTO_SKIP_SEGMENTS,
            KEY_AUTO_PLAY_NEXT_EPISODE,
            KEY_FORCE_ADVANCE_TRENDING_SLOT_ON_REFRESH -> {
                _state.value = _state.value.copy(
                    autoSkipSegments = prefs.getBoolean(KEY_AUTO_SKIP_SEGMENTS, false),
                    autoPlayNextEpisode = prefs.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true),
                    forceAdvanceTrendingSlotOnRefresh = prefs.getBoolean(
                        KEY_FORCE_ADVANCE_TRENDING_SLOT_ON_REFRESH,
                        false,
                    ),
                )
            }
        }
    }
    private val _state = MutableStateFlow(
        AppPreferencesState(
            themeMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
                ?.let(ThemeMode::valueOf)
                ?: ThemeMode.SYSTEM,
            languageMode = prefs.getString(KEY_LANGUAGE_MODE, LanguageMode.SYSTEM.name)
                ?.let(LanguageMode::valueOf)
                ?: LanguageMode.SYSTEM,
            autoSkipSegments = prefs.getBoolean(KEY_AUTO_SKIP_SEGMENTS, false),
            autoPlayNextEpisode = prefs.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true),
            forceAdvanceTrendingSlotOnRefresh = prefs.getBoolean(
                KEY_FORCE_ADVANCE_TRENDING_SLOT_ON_REFRESH,
                false,
            ),
        )
    )

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    val state: StateFlow<AppPreferencesState> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setLanguageMode(mode: LanguageMode) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.name).apply()
        _state.value = _state.value.copy(languageMode = mode)
    }

    fun setAutoSkipSegments(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SKIP_SEGMENTS, enabled).apply()
        _state.value = _state.value.copy(autoSkipSegments = enabled)
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, enabled).apply()
        _state.value = _state.value.copy(autoPlayNextEpisode = enabled)
    }

    fun setForceAdvanceTrendingSlotOnRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_ADVANCE_TRENDING_SLOT_ON_REFRESH, enabled).apply()
        _state.value = _state.value.copy(forceAdvanceTrendingSlotOnRefresh = enabled)
    }

    companion object {
        const val PREFS_NAME = "hibiki_app_preferences"
        const val KEY_AUTO_SKIP_SEGMENTS = "auto_skip_segments"
        const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode"
        const val KEY_FORCE_ADVANCE_TRENDING_SLOT_ON_REFRESH = "force_advance_trending_slot_on_refresh"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE_MODE = "language_mode"
    }
}
