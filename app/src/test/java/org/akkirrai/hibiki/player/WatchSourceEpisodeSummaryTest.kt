package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchSourceEpisodeSummaryTest {
    @Test
    fun `formats compact English episode summary`() {
        assertEquals("· 61 ep.", formatWatchSourceEpisodeSummary(61, "ep."))
    }

    @Test
    fun `formats compact Russian episode summary`() {
        assertEquals("· 61 сер.", formatWatchSourceEpisodeSummary(61, "сер."))
    }
}
