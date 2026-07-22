package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeFormattingTest {
    @Test
    fun formatsWholeAndFractionalEpisodeNumbers() {
        assertEquals("3", formatEpisodeNumber(3.0))
        assertEquals("3.5", formatEpisodeNumber(3.5))
    }

    @Test
    fun formatsEpisodeDurationAsMinutesAndSeconds() {
        assertEquals("00:00", formatEpisodeDuration(0))
        assertEquals("01:05", formatEpisodeDuration(65_000))
        assertEquals("12:34", formatEpisodeDuration(754_000))
    }
}
