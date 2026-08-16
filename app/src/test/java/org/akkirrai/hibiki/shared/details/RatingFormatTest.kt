package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals

class RatingFormatTest {
    @Test
    fun formatsTwoDecimalPlaces() {
        assertEquals("8.50", formatRating(8.5))
        assertEquals("7.13", formatRating(7.126))
        assertEquals("0.00", formatRating(0.0))
    }
}
