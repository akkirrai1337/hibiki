package org.akkirrai.hibiki.feature.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YummyProfileTimestampTest {
    @Test
    fun `profile spent time is converted from seconds`() {
        assertEquals(3_600_000L, yummySpentTimeToMillis(3_600L))
        assertEquals(0L, yummySpentTimeToMillis(-1L))
    }

    @Test
    fun `unix seconds and milliseconds format as the same date`() {
        val seconds = 1_700_000_000L

        val fromSeconds = formatEpochDateCompact(seconds)
        val fromMilliseconds = formatEpochDateCompact(seconds * 1_000L)

        assertEquals(fromMilliseconds, fromSeconds)
        assertFalse(fromSeconds.endsWith("70"))
    }
}
