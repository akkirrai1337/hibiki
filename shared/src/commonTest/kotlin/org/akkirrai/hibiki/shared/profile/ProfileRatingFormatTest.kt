package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileRatingFormatTest {
    @Test
    fun omitsDecimalsForWholeRatings() {
        assertEquals("8", formatProfileRating(8.0))
        assertEquals("8.25", formatProfileRating(8.25))
    }
}
