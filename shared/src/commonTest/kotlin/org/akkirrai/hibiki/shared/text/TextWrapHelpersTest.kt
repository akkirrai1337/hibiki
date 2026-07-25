package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextWrapHelpersTest {
    @Test
    fun keepsLastWordAttached() {
        assertEquals("Player\u00A0Settings", "Player Settings".preventTrailingOrphanWrap())
        assertEquals("Single", "Single".preventTrailingOrphanWrap())
    }
}
