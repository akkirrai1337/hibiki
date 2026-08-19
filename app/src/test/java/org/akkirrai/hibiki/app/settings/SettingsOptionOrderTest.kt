package org.akkirrai.hibiki.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsOptionOrderTest {
    @Test
    fun preservesSettingsOptionOrder() {
        assertEquals(listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM), themeModeOptions)
        assertEquals(listOf(LanguageMode.RUSSIAN, LanguageMode.ENGLISH, LanguageMode.SYSTEM), languageModeOptions)
    }
}
