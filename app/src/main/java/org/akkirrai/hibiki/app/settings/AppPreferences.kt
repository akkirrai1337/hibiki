package org.akkirrai.hibiki.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow

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

enum class VideoScaleMode {
    FIT,
    CROP,
    STRETCH;

    fun next(): VideoScaleMode = entries[(ordinal + 1) % entries.size]
}

enum class AnimeSourceId {
    YUMMY_ANIME,
    ANI_LIBERTY,
}

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useSystemColorScheme: Boolean = true,
    val useAmoledTheme: Boolean = false,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val animeSource: AnimeSourceId = AnimeSourceId.YUMMY_ANIME,
    val autoSkipSegments: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val playbackSpeed: Float = 1f,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val discordRpcEnabled: Boolean = false,
    val discordRpcExcludedTitleIds: Set<String> = emptySet(),
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME_MODE,
            KEY_USE_SYSTEM_COLOR_SCHEME,
            KEY_USE_AMOLED_THEME,
            KEY_LANGUAGE_MODE,
            KEY_ANIME_SOURCE,
            KEY_AUTO_SKIP_SEGMENTS,
            KEY_AUTO_PLAY_NEXT_EPISODE,
            KEY_PLAYBACK_SPEED,
            KEY_VIDEO_SCALE_MODE,
            KEY_DISCORD_RPC_ENABLED,
            KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS -> {
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

    fun setUseSystemColorScheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SYSTEM_COLOR_SCHEME, enabled).apply()
    }

    fun setUseAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_AMOLED_THEME, enabled).apply()
    }

    fun setLanguageMode(mode: LanguageMode) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.name).apply()
    }

    fun setAnimeSource(source: AnimeSourceId) {
        prefs.edit().putString(KEY_ANIME_SOURCE, source.name).apply()
        _state.value = readState(prefs)
        _animeSourceChanges.tryEmit(source)
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

    fun setVideoScaleMode(mode: VideoScaleMode) {
        prefs.edit().putString(KEY_VIDEO_SCALE_MODE, mode.name).apply()
    }

    fun setDiscordRpcEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISCORD_RPC_ENABLED, enabled).apply()
    }

    fun setDiscordRpcExcluded(titleId: String, excluded: Boolean) {
        val normalizedTitleId = titleId.trim().takeIf(String::isNotBlank) ?: return
        val excludedIds = prefs.getStringSet(KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS, emptySet())
            .orEmpty()
            .toMutableSet()
        if (excluded) {
            excludedIds += normalizedTitleId
        } else {
            excludedIds -= normalizedTitleId
        }
        prefs.edit().putStringSet(KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS, excludedIds).apply()
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    companion object {
        private val _animeSourceChanges = MutableSharedFlow<AnimeSourceId>(extraBufferCapacity = 1)
        val animeSourceChanges: SharedFlow<AnimeSourceId> = _animeSourceChanges.asSharedFlow()

        const val PREFS_NAME = "hibiki_app_preferences"
        const val KEY_AUTO_SKIP_SEGMENTS = "auto_skip_segments"
        const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_VIDEO_SCALE_MODE = "video_scale_mode"
        const val KEY_DISCORD_RPC_ENABLED = "discord_rpc_enabled"
        const val KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS = "discord_rpc_excluded_title_ids"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_USE_SYSTEM_COLOR_SCHEME = "use_system_color_scheme"
        private const val KEY_USE_AMOLED_THEME = "use_amoled_theme"
        private const val KEY_LANGUAGE_MODE = "language_mode"
        private const val KEY_ANIME_SOURCE = "anime_source"

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
                useSystemColorScheme = prefs.getBoolean(KEY_USE_SYSTEM_COLOR_SCHEME, true),
                useAmoledTheme = prefs.getBoolean(KEY_USE_AMOLED_THEME, false),
                languageMode = prefs.getString(KEY_LANGUAGE_MODE, LanguageMode.SYSTEM.name)
                    ?.let(LanguageMode::valueOf)
                    ?: LanguageMode.SYSTEM,
                animeSource = prefs.getString(KEY_ANIME_SOURCE, AnimeSourceId.YUMMY_ANIME.name)
                    ?.let { runCatching { AnimeSourceId.valueOf(it) }.getOrNull() }
                    ?: AnimeSourceId.YUMMY_ANIME,
                autoSkipSegments = prefs.getBoolean(KEY_AUTO_SKIP_SEGMENTS, false),
                autoPlayNextEpisode = prefs.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true),
                playbackSpeed = normalizePlaybackSpeed(prefs.getFloat(KEY_PLAYBACK_SPEED, 1f)),
                videoScaleMode = prefs.getString(KEY_VIDEO_SCALE_MODE, VideoScaleMode.FIT.name)
                    ?.let { runCatching { VideoScaleMode.valueOf(it) }.getOrNull() }
                    ?: VideoScaleMode.FIT,
                discordRpcEnabled = prefs.getBoolean(KEY_DISCORD_RPC_ENABLED, false),
                discordRpcExcludedTitleIds = prefs
                    .getStringSet(KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS, emptySet())
                    .orEmpty()
                    .toSet(),
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
