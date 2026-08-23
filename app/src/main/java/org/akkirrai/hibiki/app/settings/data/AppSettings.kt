package org.akkirrai.hibiki.app.settings

import org.akkirrai.hibiki.player.VideoScaleMode

/** User-selected application language, independent from the host platform locale. */
enum class LanguageMode(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    ENGLISH("en"),
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class NotificationPermissionState {
    NOT_ASKED,
    GRANTED,
    DENIED,
}

data class AppSettingsState(
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val darkTheme: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useSystemColorScheme: Boolean = true,
    val useAmoledTheme: Boolean = false,
    val autoSkipSegments: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val playbackSpeed: Float = 1f,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val onboardingCompleted: Boolean = false,
    val selectedSourceId: String? = null,
    val notificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
)

interface AppSettingsStore {
    fun load(): AppSettingsState
    fun save(state: AppSettingsState)
}

/** Safe fallback for hosts that have not connected persistent storage yet. */
class InMemoryAppSettingsStore(
    initialState: AppSettingsState = AppSettingsState(),
) : AppSettingsStore {
    private var state = initialState

    override fun load(): AppSettingsState = state

    override fun save(state: AppSettingsState) {
        this.state = state
    }
}
