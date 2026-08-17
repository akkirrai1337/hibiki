package org.akkirrai.hibiki.core.settings

import android.content.Context
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore

/** Android adapter for the shared settings contract backed by existing preferences. */
class AndroidAppSettingsStore(
    private val preferences: AppPreferences,
) : AppSettingsStore, AutoCloseable {
    constructor(context: Context) : this(AppPreferences(context.applicationContext))

    override fun load(): AppSettingsState = preferences.state.value.toSharedState()

    override fun save(state: AppSettingsState) {
        // Only persist fields that actually changed: every setter here writes through a
        // separate SharedPreferences.Editor.apply() call, and each one fires the shared
        // OnSharedPreferenceChangeListener and re-derives AppPreferencesState. Rewriting all
        // fields on every single settings toggle turns one change into ~9 redundant
        // writes/listener callbacks.
        val current = preferences.state.value.toSharedState()
        if (state.languageMode != current.languageMode) preferences.setLanguageMode(state.languageMode)
        if (state.themeMode != current.themeMode) preferences.setThemeMode(state.themeMode)
        if (state.useSystemColorScheme != current.useSystemColorScheme) {
            preferences.setUseSystemColorScheme(state.useSystemColorScheme)
        }
        if (state.useAmoledTheme != current.useAmoledTheme) preferences.setUseAmoledTheme(state.useAmoledTheme)
        if (state.autoSkipSegments != current.autoSkipSegments) {
            preferences.setAutoSkipSegments(state.autoSkipSegments)
        }
        if (state.autoPlayNextEpisode != current.autoPlayNextEpisode) {
            preferences.setAutoPlayNextEpisode(state.autoPlayNextEpisode)
        }
        if (state.playbackSpeed != current.playbackSpeed) preferences.setPlaybackSpeed(state.playbackSpeed)
        if (state.videoScaleMode != current.videoScaleMode) preferences.setVideoScaleMode(state.videoScaleMode)
        if (state.notificationPermissionState != current.notificationPermissionState) {
            preferences.setNotificationPermissionState(state.notificationPermissionState)
        }
        val selectedSourceId = state.selectedSourceId
        if (state.onboardingCompleted && selectedSourceId != null &&
            (!current.onboardingCompleted || selectedSourceId != current.selectedSourceId)
        ) {
            preferences.completeOnboarding(SourceId(selectedSourceId))
        }
    }

    override fun close() {
        preferences.close()
    }
}

private fun org.akkirrai.hibiki.app.settings.AppPreferencesState.toSharedState(): AppSettingsState =
    AppSettingsState(
        languageMode = languageMode,
        darkTheme = themeMode == ThemeMode.DARK || useAmoledTheme,
        themeMode = themeMode,
        useSystemColorScheme = useSystemColorScheme,
        useAmoledTheme = useAmoledTheme,
        autoSkipSegments = autoSkipSegments,
        autoPlayNextEpisode = autoPlayNextEpisode,
        playbackSpeed = playbackSpeed,
        videoScaleMode = videoScaleMode,
        onboardingCompleted = onboardingCompleted,
        selectedSourceId = animeSource.value.takeIf { hasExplicitAnimeSource },
        notificationPermissionState = notificationPermissionState,
    )
