package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class DetailsRelatedTextParityTest {
    @Test
    fun relatedTitleMatchesAndroidReference() {
        assertEquals(
            "Related titles",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.DetailsRelatedTitle),
        )
        assertEquals(
            "Связанное",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.DetailsRelatedTitle),
        )
    }
}
