package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class DetailsTrailerTextParityTest {
    @Test
    fun trailerActionLabelMatchesAndroidReference() {
        assertEquals(
            "Play trailer",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.DetailsTrailer),
        )
        assertEquals(
            "Воспроизвести трейлер",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.DetailsTrailer),
        )
    }
}
