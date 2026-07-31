package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class DetailsAccessibilityTextParityTest {
    @Test
    fun favoriteActionLabelMatchesAndroidReference() {
        assertEquals(
            "Add to favorites",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.DetailsFavorite),
        )
        assertEquals(
            "В избранное",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.DetailsFavorite),
        )
    }
}
