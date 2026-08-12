package org.akkirrai.hibiki.shared.app.shell.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.saveHibikiAppSettings
import org.akkirrai.hibiki.shared.app.shell.settings.HibikiSettingsActions
import org.akkirrai.hibiki.shared.app.shell.settings.createHibikiSettingsActions

internal class HibikiAppSettingsState(
    initial: AppSettingsState,
    selectedSourceId: String?,
    private val settingsStore: AppSettingsStore,
) {
    var languageMode by mutableStateOf(initial.languageMode)
    var darkTheme by mutableStateOf(initial.darkTheme)
    var themeMode by mutableStateOf(initial.themeMode)
    var useSystemColorScheme by mutableStateOf(initial.useSystemColorScheme)
    var useAmoledTheme by mutableStateOf(initial.useAmoledTheme)
    var autoSkipSegments by mutableStateOf(initial.autoSkipSegments)
    var autoPlayNextEpisode by mutableStateOf(initial.autoPlayNextEpisode)
    var notificationPermissionState by mutableStateOf(initial.notificationPermissionState)
    var onboardingCompleted by mutableStateOf(initial.onboardingCompleted)
    var onboardingSourceId by mutableStateOf(initial.selectedSourceId ?: selectedSourceId)
    var currentSelectedSourceId by mutableStateOf(selectedSourceId ?: initial.selectedSourceId)

    fun applyOnboardingPermission(state: NotificationPermissionState) {
        if (state != NotificationPermissionState.NOT_ASKED ||
            notificationPermissionState == NotificationPermissionState.NOT_ASKED
        ) {
            notificationPermissionState = state
        }
    }

    fun saveTo() {
        saveHibikiAppSettings(
            settingsStore = settingsStore,
            languageMode = languageMode,
            darkTheme = darkTheme,
            themeMode = themeMode,
            useSystemColorScheme = useSystemColorScheme,
            useAmoledTheme = useAmoledTheme,
            autoSkipSegments = autoSkipSegments,
            autoPlayNextEpisode = autoPlayNextEpisode,
            onboardingCompleted = onboardingCompleted,
            selectedSourceId = onboardingSourceId,
        )
    }

    fun completeOnboarding(sourceId: String) {
        onboardingSourceId = sourceId
        onboardingCompleted = true
        settingsStore.save(
            settingsStore.load().copy(
                onboardingCompleted = true,
                selectedSourceId = sourceId,
            ),
        )
    }

    fun createActions(): HibikiSettingsActions =
        createHibikiSettingsActions(
            saveSettings = ::saveTo,
            setLanguageMode = { languageMode = it },
            setDarkTheme = { darkTheme = it },
            setThemeMode = { themeMode = it },
            setUseSystemColorScheme = { useSystemColorScheme = it },
            setUseAmoledTheme = { useAmoledTheme = it },
            setAutoSkipSegments = { autoSkipSegments = it },
        )
}

@Composable
internal fun rememberHibikiAppSettingsState(
    settingsStore: AppSettingsStore,
    selectedSourceId: String?,
    onboardingNotificationPermissionState: NotificationPermissionState,
): HibikiAppSettingsState {
    val initial = remember(settingsStore) { settingsStore.load() }
    val state = remember(settingsStore, selectedSourceId, initial) {
        HibikiAppSettingsState(initial, selectedSourceId, settingsStore)
    }
    LaunchedEffect(onboardingNotificationPermissionState) {
        if (
            onboardingNotificationPermissionState != NotificationPermissionState.NOT_ASKED ||
            state.notificationPermissionState == NotificationPermissionState.NOT_ASKED
        ) {
            state.applyOnboardingPermission(onboardingNotificationPermissionState)
        }
    }
    return state
}
