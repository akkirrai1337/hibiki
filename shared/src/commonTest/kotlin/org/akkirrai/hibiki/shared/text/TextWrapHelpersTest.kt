package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextWrapHelpersTest {
    @Test
    fun keepsLastWordAttached() {
        assertEquals("Player\u00A0Settings", "Player Settings".preventTrailingOrphanWrap())
        assertEquals("Необъятный океан\u00A02", "Необъятный океан 2".preventTrailingOrphanWrap())
        assertEquals("Single", "Single".preventTrailingOrphanWrap())
        assertEquals(
            "Унесенные призраками\u00A0и\u00A0ведьма",
            "Унесенные призраками и ведьма".preventTrailingOrphanWrap(),
        )
    }
}
