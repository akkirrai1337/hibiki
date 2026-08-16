package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactCountFormatTest {
    @Test
    fun formatsThousandsAndMillions() {
        assertEquals("999", formatCompactCount(999L))
        assertEquals("1.0K", formatCompactCount(1_000L))
        assertEquals("1.5K", formatCompactCount(1_500L))
        assertEquals("1.0M", formatCompactCount(1_000_000L))
    }
}
