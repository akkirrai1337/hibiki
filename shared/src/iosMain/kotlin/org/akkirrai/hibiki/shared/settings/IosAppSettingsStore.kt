package org.akkirrai.hibiki.shared.settings

import platform.Foundation.NSUserDefaults
import org.akkirrai.hibiki.shared.player.VideoScaleMode

internal class IosAppSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppSettingsStore {
    override fun load(): AppSettingsState = AppSettingsState(
        languageMode = defaults.stringForKey(LANGUAGE_MODE_KEY)
            ?.let { raw -> runCatching { LanguageMode.valueOf(raw) }.getOrNull() }
            ?: LanguageMode.SYSTEM,
        darkTheme = defaults.boolForKey(DARK_THEME_KEY),
        themeMode = defaults.stringForKey(THEME_MODE_KEY)
            ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
            ?: if (defaults.boolForKey(DARK_THEME_KEY)) ThemeMode.DARK else ThemeMode.SYSTEM,
        useSystemColorScheme = if (defaults.objectForKey(USE_SYSTEM_COLOR_SCHEME_KEY) == null) {
            true
        } else {
            defaults.boolForKey(USE_SYSTEM_COLOR_SCHEME_KEY)
        },
        useAmoledTheme = defaults.boolForKey(AMOLED_THEME_KEY),
        autoSkipSegments = defaults.boolForKey(AUTO_SKIP_SEGMENTS_KEY),
        autoPlayNextEpisode = if (defaults.objectForKey(AUTO_PLAY_NEXT_KEY) == null) {
            true
        } else {
            defaults.boolForKey(AUTO_PLAY_NEXT_KEY)
        },
        playbackSpeed = defaults.doubleForKey(PLAYBACK_SPEED_KEY).toFloat().takeIf { it > 0f } ?: 1f,
        videoScaleMode = defaults.stringForKey(VIDEO_SCALE_MODE_KEY)
            ?.let { raw -> runCatching { VideoScaleMode.valueOf(raw) }.getOrNull() }
            ?: VideoScaleMode.FIT,
        onboardingCompleted = defaults.boolForKey(ONBOARDING_COMPLETED_KEY),
        selectedSourceId = defaults.stringForKey(SELECTED_SOURCE_ID_KEY),
        notificationPermissionState = defaults.stringForKey(NOTIFICATION_PERMISSION_STATE_KEY)
            ?.let { raw -> runCatching { NotificationPermissionState.valueOf(raw) }.getOrNull() }
            ?: NotificationPermissionState.NOT_ASKED,
    )

    override fun save(state: AppSettingsState) {
        defaults.setObject(state.languageMode.name, forKey = LANGUAGE_MODE_KEY)
        defaults.setBool(state.darkTheme, forKey = DARK_THEME_KEY)
        defaults.setObject(state.themeMode.name, forKey = THEME_MODE_KEY)
        defaults.setBool(state.useSystemColorScheme, forKey = USE_SYSTEM_COLOR_SCHEME_KEY)
        defaults.setBool(state.useAmoledTheme, forKey = AMOLED_THEME_KEY)
        defaults.setBool(state.autoSkipSegments, forKey = AUTO_SKIP_SEGMENTS_KEY)
        defaults.setBool(state.autoPlayNextEpisode, forKey = AUTO_PLAY_NEXT_KEY)
        defaults.setDouble(state.playbackSpeed.toDouble(), forKey = PLAYBACK_SPEED_KEY)
        defaults.setObject(state.videoScaleMode.name, forKey = VIDEO_SCALE_MODE_KEY)
        defaults.setBool(state.onboardingCompleted, forKey = ONBOARDING_COMPLETED_KEY)
        state.selectedSourceId?.let { sourceId ->
            defaults.setObject(sourceId, forKey = SELECTED_SOURCE_ID_KEY)
        } ?: defaults.removeObjectForKey(SELECTED_SOURCE_ID_KEY)
        defaults.setObject(state.notificationPermissionState.name, forKey = NOTIFICATION_PERMISSION_STATE_KEY)
    }

    private companion object {
        const val LANGUAGE_MODE_KEY = "hibiki.language_mode"
        const val DARK_THEME_KEY = "hibiki.dark_theme"
        const val THEME_MODE_KEY = "hibiki.theme_mode"
        const val USE_SYSTEM_COLOR_SCHEME_KEY = "hibiki.use_system_color_scheme"
        const val AMOLED_THEME_KEY = "hibiki.amoled_theme"
        const val AUTO_SKIP_SEGMENTS_KEY = "hibiki.auto_skip_segments"
        const val AUTO_PLAY_NEXT_KEY = "hibiki.auto_play_next"
        const val PLAYBACK_SPEED_KEY = "hibiki.playback_speed"
        const val VIDEO_SCALE_MODE_KEY = "hibiki.video_scale_mode"
        const val ONBOARDING_COMPLETED_KEY = "hibiki.onboarding_completed"
        const val SELECTED_SOURCE_ID_KEY = "hibiki.selected_source_id"
        const val NOTIFICATION_PERMISSION_STATE_KEY = "hibiki.notification_permission_state"
    }
}
