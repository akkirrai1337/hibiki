package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class AppTextResolverTest {
    @Test
    fun englishTextIsResolvedForEnglishMode() {
        assertEquals(
            "Shared UI is ready",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.SharedUiReady),
        )
    }

    @Test
    fun russianTextIsResolvedForRussianMode() {
        assertEquals(
            "Общий UI готов",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.SharedUiReady),
        )
    }
    @Test
    fun systemModeUsesRussianHostLanguage() {
        assertEquals(
            "Общий UI готов",
            DefaultAppTextResolver(LanguageMode.SYSTEM, systemLanguage = "ru-RU")
                .resolve(AppTextKey.SharedUiReady),
        )
    }

    @Test
    fun systemModeFallsBackToEnglishForOtherHostLanguages() {
        assertEquals(
            "Shared UI is ready",
            DefaultAppTextResolver(LanguageMode.SYSTEM, systemLanguage = "uk-UA")
                .resolve(AppTextKey.SharedUiReady),
        )
    }
}
