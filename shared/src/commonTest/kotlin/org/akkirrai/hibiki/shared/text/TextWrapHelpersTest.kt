package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextWrapHelpersTest {
    @Test
    fun keepsLastWordAttached() {
        assertEquals("Player\u00A0Settings", "Player Settings".preventTrailingOrphanWrap())
        assertEquals("Необъятный океан\u00A02", "Необъятный океан 2".preventTrailingOrphanWrap())
        assertEquals("Single", "Single".preventTrailingOrphanWrap())
    }

    @Test
    fun attachesSingleCharacterWordToTheFollowingWord() {
        assertEquals(
            "Унесенные призраками и\u00A0ведьма",
            "Унесенные призраками и ведьма".preventTrailingOrphanWrap(),
        )
    }

    @Test
    fun preventsBreakingInsideWords() {
        assertEquals(
            "У\u2060н\u2060е\u2060с\u2060е\u2060н\u2060н\u2060ы\u2060е п\u2060р\u2060и\u2060з\u2060р\u2060а\u2060к\u2060а\u2060м\u2060и",
            "Унесенные призраками".preventWordBreaks(),
        )
    }
}
