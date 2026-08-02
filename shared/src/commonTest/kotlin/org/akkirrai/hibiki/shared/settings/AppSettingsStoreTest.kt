package org.akkirrai.hibiki.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStoreTest {
    @Test
    fun inMemoryStoreRoundTripsSettings() {
        val store = InMemoryAppSettingsStore()
        val expected = AppSettingsState(
            languageMode = LanguageMode.RUSSIAN,
            darkTheme = true,
            themeMode = ThemeMode.DARK,
            onboardingCompleted = true,
            selectedSourceId = "ani-liberty",
            notificationPermissionState = NotificationPermissionState.GRANTED,
        )

        store.save(expected)

        assertEquals(expected, store.load())
    }
}
