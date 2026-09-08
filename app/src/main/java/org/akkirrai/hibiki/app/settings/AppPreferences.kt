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
import org.akkirrai.hibiki.core.source.extension.ExtensionMarketplaceClient

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class LanguageMode(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    ENGLISH("en")
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
    // Seconds the skip-segment countdown runs for. Two independent values, not one shared timer:
    // "how long before it skips for you" and "how long the button stays on screen before going
    // away" are different trade-offs, and only one of them is ever in play at a time (whichever
    // autoSkipSegments selects). Mirrors the desktop app's playerPrefsStore.
    val autoSkipDelaySeconds: Int = AppPreferences.DEFAULT_SKIP_TIMER_SECONDS,
    val skipButtonTimeoutSeconds: Int = AppPreferences.DEFAULT_SKIP_TIMER_SECONDS,
    // How far into an episode (percent of its duration) counts as watched - was hardcoded at 90%
    // in two different places before this existed.
    val watchedThresholdPercent: Int = AppPreferences.DEFAULT_WATCHED_THRESHOLD_PERCENT,
    val autoPlayNextEpisode: Boolean = true,
    val playbackSpeed: Float = 1f,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val discordRpcEnabled: Boolean = false,
    val discordRpcExcludedTitleIds: Set<String> = emptySet(),
    val hideNsfwSources: Boolean = false,
    val sourceRepositoryUrls: List<String> = listOf(ExtensionMarketplaceClient.DEFAULT_INDEX_URL),
)

data class RememberedAnimeSourceAppearance(
    val name: String,
    val iconUrl: String?,
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
            KEY_AUTO_SKIP_DELAY_SECONDS,
            KEY_SKIP_BUTTON_TIMEOUT_SECONDS,
            KEY_WATCHED_THRESHOLD_PERCENT,
            KEY_AUTO_PLAY_NEXT_EPISODE,
            KEY_PLAYBACK_SPEED,
            KEY_VIDEO_SCALE_MODE,
            KEY_DISCORD_RPC_ENABLED,
            KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS,
            KEY_HIDE_NSFW_SOURCES,
            KEY_SOURCE_REPOSITORY_URLS -> {
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

    fun completeOnboarding() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
        _state.value = readState(prefs)
    }

    fun setNotificationPermissionState(state: NotificationPermissionState) {
        prefs.edit().putString(KEY_NOTIFICATION_PERMISSION_STATE, state.name).apply()
    }

    fun setAutoSkipSegments(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SKIP_SEGMENTS, enabled).apply()
    }

    fun setAutoSkipDelaySeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_SKIP_DELAY_SECONDS, clampSkipTimer(seconds)).apply()
    }

    fun setSkipButtonTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_BUTTON_TIMEOUT_SECONDS, clampSkipTimer(seconds)).apply()
    }

    fun setWatchedThresholdPercent(percent: Int) {
        prefs.edit().putInt(KEY_WATCHED_THRESHOLD_PERCENT, clampWatchedThreshold(percent)).apply()
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

    fun setHideNsfwSources(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_NSFW_SOURCES, hide).apply()
    }

    fun addSourceRepository(url: String) {
        val normalized = url.trim().takeIf(String::isNotBlank) ?: return
        val urls = readSourceRepositoryUrls(prefs)
        if (normalized in urls) return
        prefs.edit().putString(KEY_SOURCE_REPOSITORY_URLS, (urls + normalized).joinToString("\n")).apply()
    }

    /** The built-in repository can't be removed - it's the only one guaranteed to always work,
     * so a user who removes every repository would otherwise be left with a broken, empty
     * marketplace and no obvious way back in short of reinstalling the app. */
    fun removeSourceRepository(url: String) {
        if (url == ExtensionMarketplaceClient.DEFAULT_INDEX_URL) return
        val urls = readSourceRepositoryUrls(prefs) - url
        prefs.edit().putString(KEY_SOURCE_REPOSITORY_URLS, urls.joinToString("\n")).apply()
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    companion object {
        private val _animeSourceChanges = MutableSharedFlow<SourceId>(extraBufferCapacity = 1)
        val animeSourceChanges: SharedFlow<SourceId> = _animeSourceChanges.asSharedFlow()

        val DEFAULT_ANIME_SOURCE_ID = SourceId("yummy-anime")

        // Bounds for the two skip timers - letting either reach 0 defeats the point (an instant,
        // unannounced auto-skip; or a button gone before it can be pressed), and anything past
        // half a minute outlives the segment it belongs to. Same range the desktop app uses.
        const val SKIP_TIMER_MIN_SECONDS = 1
        const val SKIP_TIMER_MAX_SECONDS = 30
        const val DEFAULT_SKIP_TIMER_SECONDS = 10

        // Below 50% would mark something watched after barely starting it; above 95% leaves no
        // room for stopping a few seconds before the credits.
        const val WATCHED_THRESHOLD_MIN_PERCENT = 50
        const val WATCHED_THRESHOLD_MAX_PERCENT = 95
        const val DEFAULT_WATCHED_THRESHOLD_PERCENT = 90

        /** Last value read from storage, for the non-composable, context-less watched checks in
         * WatchStateRepository and the episode lists. readState() below keeps it current: it runs
         * on construction and on every change this class is notified about. */
        @Volatile
        private var cachedWatchedThresholdPercent = DEFAULT_WATCHED_THRESHOLD_PERCENT
        val watchedThresholdPercent: Int get() = cachedWatchedThresholdPercent

        private fun clampSkipTimer(seconds: Int): Int =
            seconds.coerceIn(SKIP_TIMER_MIN_SECONDS, SKIP_TIMER_MAX_SECONDS)

        private fun clampWatchedThreshold(percent: Int): Int =
            percent.coerceIn(WATCHED_THRESHOLD_MIN_PERCENT, WATCHED_THRESHOLD_MAX_PERCENT)

        const val PREFS_NAME = "hibiki_app_preferences"
        const val KEY_AUTO_SKIP_SEGMENTS = "auto_skip_segments"
        const val KEY_AUTO_SKIP_DELAY_SECONDS = "auto_skip_delay_seconds"
        const val KEY_SKIP_BUTTON_TIMEOUT_SECONDS = "skip_button_timeout_seconds"
        const val KEY_WATCHED_THRESHOLD_PERCENT = "watched_threshold_percent"
        const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_VIDEO_SCALE_MODE = "video_scale_mode"
        const val KEY_DISCORD_RPC_ENABLED = "discord_rpc_enabled"
        const val KEY_DISCORD_RPC_EXCLUDED_TITLE_IDS = "discord_rpc_excluded_title_ids"
        const val KEY_HIDE_NSFW_SOURCES = "hide_nsfw_sources"
        const val KEY_SOURCE_REPOSITORY_URLS = "source_repository_urls"
        private const val KEY_KNOWN_ANIME_SOURCE_NAMES = "known_anime_source_names"
        private const val NAME_SEPARATOR = '\u001F'
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

        fun readCatalogSort(context: Context, source: SourceId): String? {
            return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(catalogSortKey(source), null)
        }

        fun saveCatalogSort(context: Context, source: SourceId, sort: String) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(catalogSortKey(source), sort)
                .apply()
        }

        /** Keeps source names and icons available for stored cards after an extension is removed. */
        fun rememberAnimeSourceAppearances(
            context: Context,
            appearances: Map<String, RememberedAnimeSourceAppearance>,
        ) {
            if (appearances.isEmpty()) return
            val preferences = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedAppearances = preferences.getStringSet(KEY_KNOWN_ANIME_SOURCE_NAMES, emptySet())
                .orEmpty()
                .associate { entry ->
                    val parts = entry.split(NAME_SEPARATOR, limit = 3)
                    parts.first() to RememberedAnimeSourceAppearance(
                        name = parts.getOrElse(1) { "" },
                        iconUrl = parts.getOrNull(2)?.takeIf(String::isNotBlank),
                    )
                }
                .toMutableMap()
            appearances.forEach { (id, appearance) ->
                if (id.isNotBlank() && appearance.name.isNotBlank()) {
                    storedAppearances[id] = appearance
                }
            }
            preferences.edit()
                .putStringSet(
                    KEY_KNOWN_ANIME_SOURCE_NAMES,
                    storedAppearances.mapTo(linkedSetOf()) { (id, appearance) ->
                        "$id$NAME_SEPARATOR${appearance.name}$NAME_SEPARATOR${appearance.iconUrl.orEmpty()}"
                    },
                )
                .apply()
        }

        fun rememberedAnimeSourceAppearance(
            context: Context,
            sourceId: SourceId,
        ): RememberedAnimeSourceAppearance? =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_KNOWN_ANIME_SOURCE_NAMES, emptySet())
                .orEmpty()
                .firstOrNull { it.substringBefore(NAME_SEPARATOR) == sourceId.value }
                ?.let { entry ->
                    val parts = entry.split(NAME_SEPARATOR, limit = 3)
                    RememberedAnimeSourceAppearance(
                        name = parts.getOrElse(1) { "" },
                        iconUrl = parts.getOrNull(2)?.takeIf(String::isNotBlank),
                    )
                }
                ?.takeIf { it.name.isNotBlank() }

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
                autoSkipDelaySeconds = clampSkipTimer(
                    prefs.getInt(KEY_AUTO_SKIP_DELAY_SECONDS, DEFAULT_SKIP_TIMER_SECONDS),
                ),
                skipButtonTimeoutSeconds = clampSkipTimer(
                    prefs.getInt(KEY_SKIP_BUTTON_TIMEOUT_SECONDS, DEFAULT_SKIP_TIMER_SECONDS),
                ),
                watchedThresholdPercent = clampWatchedThreshold(
                    prefs.getInt(KEY_WATCHED_THRESHOLD_PERCENT, DEFAULT_WATCHED_THRESHOLD_PERCENT),
                ).also { cachedWatchedThresholdPercent = it },
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
                hideNsfwSources = prefs.getBoolean(KEY_HIDE_NSFW_SOURCES, false),
                sourceRepositoryUrls = readSourceRepositoryUrls(prefs),
            )
        }

        private fun readSourceRepositoryUrls(prefs: SharedPreferences): List<String> {
            if (!prefs.contains(KEY_SOURCE_REPOSITORY_URLS)) return listOf(ExtensionMarketplaceClient.DEFAULT_INDEX_URL)
            return prefs.getString(KEY_SOURCE_REPOSITORY_URLS, "")
                .orEmpty()
                .split("\n")
                .map(String::trim)
                .filter(String::isNotBlank)
        }

        private fun normalizePlaybackSpeed(speed: Float): Float {
            return when (speed) {
                0.75f, 1f, 1.25f, 1.5f, 2f -> speed
                else -> 1f
            }
        }

        private fun catalogSortKey(source: SourceId): String = "catalog_sort_${source.value}"
    }
}
