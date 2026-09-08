package org.akkirrai.hibiki.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressRulesTest {

    @Test
    fun `an episode counts as watched once the threshold share is reached`() {
        val durationMs = 24 * 60 * 1_000L
        assertFalse(isEpisodeWatched(positionMs = 0L, durationMs = durationMs, thresholdPercent = 90))
        assertFalse(isEpisodeWatched(positionMs = durationMs * 89 / 100, durationMs = durationMs, thresholdPercent = 90))
        assertTrue(isEpisodeWatched(positionMs = durationMs * 90 / 100, durationMs = durationMs, thresholdPercent = 90))
        // Stopping during the credits still finishes the episode - the point of the threshold.
        assertTrue(isEpisodeWatched(positionMs = durationMs - 60_000L, durationMs = durationMs, thresholdPercent = 90))
    }

    @Test
    fun `a lower threshold marks the same position watched earlier`() {
        val durationMs = 1_000_000L
        val positionMs = 600_000L
        assertFalse(isEpisodeWatched(positionMs, durationMs, thresholdPercent = 90))
        assertTrue(isEpisodeWatched(positionMs, durationMs, thresholdPercent = 50))
    }

    @Test
    fun `an unknown duration is never watched`() {
        assertFalse(isEpisodeWatched(positionMs = 5_000L, durationMs = 0L, thresholdPercent = 50))
        assertFalse(isEpisodeWatched(positionMs = 5_000L, durationMs = -1L, thresholdPercent = 50))
    }

    @Test
    fun `playback time widens to hours only when there are hours to show`() {
        assertEquals("00:00", formatPlaybackTime(0L))
        assertEquals("00:07", formatPlaybackTime(7_400L))
        assertEquals("24:00", formatPlaybackTime(24 * 60 * 1_000L))
        // The episode list used to print this as "84:12" with its own minutes-only formatter.
        assertEquals("1:24:12", formatPlaybackTime((84 * 60 + 12) * 1_000L))
    }

    @Test
    fun `a negative position formats as the start rather than a negative time`() {
        assertEquals("00:00", formatPlaybackTime(-5_000L))
    }

    @Test
    fun `whole episode numbers lose their decimal part and half episodes keep it`() {
        assertEquals("12", formatEpisodeNumber(12.0))
        assertEquals("12.5", formatEpisodeNumber(12.5))
    }
}
