package org.akkirrai.hibiki.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLanguageResolverTest {
    @Test
    fun explicitLanguageSelectionOverridesSystemLanguage() {
        assertEquals("ru", resolveAppLanguageTag(LanguageMode.RUSSIAN, "en"))
        assertEquals("en", resolveAppLanguageTag(LanguageMode.ENGLISH, "ru"))
        assertFalse(isEnglishAppLanguage(LanguageMode.RUSSIAN, "en"))
        assertTrue(isEnglishAppLanguage(LanguageMode.ENGLISH, "ru"))
    }

    @Test
    fun systemLanguageIsUsedOnlyForSystemMode() {
        assertEquals("ru", resolveAppLanguageTag(LanguageMode.SYSTEM, "ru-RU"))
        assertEquals("en", resolveAppLanguageTag(LanguageMode.SYSTEM, "en-US"))
    }
}
