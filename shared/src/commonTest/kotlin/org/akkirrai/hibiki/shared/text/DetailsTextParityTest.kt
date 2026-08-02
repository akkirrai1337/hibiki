package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class DetailsTextParityTest {
    @Test
    fun releasedEpisodesLabelMatchesAndroidReference() {
        assertEquals(
            "Episodes released",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.EpisodesReleased),
        )
        assertEquals(
            "Серий вышло",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.EpisodesReleased),
        )
    }
}
