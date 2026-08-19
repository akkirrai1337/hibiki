package org.akkirrai.hibiki.text

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

}
