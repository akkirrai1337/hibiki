package org.akkirrai.hibiki.shared.settings

data class AppSettingsState(
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val darkTheme: Boolean = false,
    val useSystemColorScheme: Boolean = true,
    val useAmoledTheme: Boolean = false,
    val autoSkipSegments: Boolean = false,
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
