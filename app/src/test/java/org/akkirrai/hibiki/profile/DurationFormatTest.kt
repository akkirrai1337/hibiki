package org.akkirrai.hibiki.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class DurationFormatTest {
    @Test
    fun formatsHoursToOneDecimal() {
        assertEquals("0", formatDurationHours(0L))
        assertEquals("1.5", formatDurationHours(5_400_000L))
    }
}
