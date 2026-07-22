package org.akkirrai.hibiki.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStoreTest {
    @Test
    fun inMemoryStoreRoundTripsSettings() {
        val store = InMemoryAppSettingsStore()
        val expected = AppSettingsState(LanguageMode.RUSSIAN, darkTheme = true)

        store.save(expected)

        assertEquals(expected, store.load())
    }
}
