package org.akkirrai.hibiki.core.settings

import android.content.Context
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore

/** Android adapter for the shared settings contract backed by existing preferences. */
class AndroidAppSettingsStore(context: Context) : AppSettingsStore, AutoCloseable {
    private val preferences = AppPreferences(context.applicationContext)

    override fun load(): AppSettingsState = preferences.state.value.toSharedState()

    override fun save(state: AppSettingsState) {
        preferences.setLanguageMode(state.languageMode)
        preferences.setThemeMode(state.themeMode)
        preferences.setUseSystemColorScheme(state.useSystemColorScheme)
        preferences.setUseAmoledTheme(state.useAmoledTheme)
        preferences.setAutoSkipSegments(state.autoSkipSegments)
        preferences.setAutoPlayNextEpisode(state.autoPlayNextEpisode)
        preferences.setPlaybackSpeed(state.playbackSpeed)
        preferences.setVideoScaleMode(state.videoScaleMode)
        preferences.setNotificationPermissionState(state.notificationPermissionState)
        val selectedSourceId = state.selectedSourceId
        if (state.onboardingCompleted && selectedSourceId != null) {
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
