package org.akkirrai.hibiki.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.akkirrai.beakokit.api.SourceId
typealias LanguageMode = org.akkirrai.hibiki.shared.settings.LanguageMode

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}


enum class VideoScaleMode {
    FIT,
    CROP,
    STRETCH;

    fun next(): VideoScaleMode = entries[(ordinal + 1) % entries.size]
}

enum class NotificationPermissionState {
    NOT_ASKED,
    GRANTED,
    DENIED,
}

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useSystemColorScheme: Boolean = true,
    val useAmoledTheme: Boolean = false,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val animeSource: SourceId = AppPreferences.DEFAULT_ANIME_SOURCE_ID,
    val hasExplicitAnimeSource: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val notificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    val autoSkipSegments: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val playbackSpeed: Float = 1f,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val discordRpcEnabled: Boolean = false,
    val discordRpcExcludedTitleIds: Set<String> = emptySet(),
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { preferences ->
        initializeOnboardingState(preferences)
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME_MODE,
            KEY_USE_SYSTEM_COLOR_SCHEME,
            KEY_USE_AMOLED_THEME,
            KEY_LANGUAGE_MODE,
            KEY_ANIME_SOURCE,
            KEY_ONBOARDING_COMPLETED,
            KEY_NOTIFICATION_PERMISSION_STATE,
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

    fun setAnimeSource(source: SourceId) {
        prefs.edit().putString(KEY_ANIME_SOURCE, source.value).apply()
        _state.value = readState(prefs)
        _animeSourceChanges.tryEmit(source)
    }

    fun completeOnboarding(source: SourceId) {
        prefs.edit()
            .putString(KEY_ANIME_SOURCE, source.value)
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
        _state.value = readState(prefs)
        _animeSourceChanges.tryEmit(source)
    }

    fun setNotificationPermissionState(state: NotificationPermissionState) {
        prefs.edit().putString(KEY_NOTIFICATION_PERMISSION_STATE, state.name).apply()
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
        private val _animeSourceChanges = MutableSharedFlow<SourceId>(extraBufferCapacity = 1)
        val animeSourceChanges: SharedFlow<SourceId> = _animeSourceChanges.asSharedFlow()

        val DEFAULT_ANIME_SOURCE_ID = SourceId("yummy-anime")

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
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_NOTIFICATION_PERMISSION_STATE = "notification_permission_state"

        private fun initializeOnboardingState(prefs: SharedPreferences) {
            if (prefs.contains(KEY_ONBOARDING_COMPLETED)) return
            // A cleared app data directory must behave like a fresh install. Existing
            // users are recognized only when their old preferences are still present.
            val isExistingInstall = prefs.all.isNotEmpty()
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, isExistingInstall).apply()
        }

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
                animeSource = SourceId.parseStored(
                    prefs.getString(KEY_ANIME_SOURCE, DEFAULT_ANIME_SOURCE_ID.value),
                ) ?: DEFAULT_ANIME_SOURCE_ID,
                hasExplicitAnimeSource = prefs.contains(KEY_ANIME_SOURCE),
                onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
                notificationPermissionState = prefs
                    .getString(KEY_NOTIFICATION_PERMISSION_STATE, NotificationPermissionState.NOT_ASKED.name)
                    ?.let { stored ->
                        runCatching { NotificationPermissionState.valueOf(stored) }.getOrNull()
                    }
                    ?: NotificationPermissionState.NOT_ASKED,
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
