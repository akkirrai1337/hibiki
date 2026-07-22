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
}
