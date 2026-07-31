package org.akkirrai.hibiki.desktop

import java.util.UUID
import java.util.prefs.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.LanguageMode

class DesktopSettingsStoreTest {
    private lateinit var preferences: Preferences

    @Before
    fun setUp() {
        preferences = Preferences.userRoot().node("hibiki-settings-tests/${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        preferences.removeNode()
    }

    @Test
    fun languageModePersistsAcrossStoreInstances() {
        DesktopSettingsStore(preferences).save(AppSettingsState(languageMode = LanguageMode.RUSSIAN))

        assertEquals(LanguageMode.RUSSIAN, DesktopSettingsStore(preferences).load().languageMode)

        DesktopSettingsStore(preferences).save(AppSettingsState(languageMode = LanguageMode.ENGLISH))
        assertEquals(LanguageMode.ENGLISH, DesktopSettingsStore(preferences).load().languageMode)
    }
}
