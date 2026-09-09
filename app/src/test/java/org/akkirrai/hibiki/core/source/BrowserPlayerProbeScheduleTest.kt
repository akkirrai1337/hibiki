package org.akkirrai.hibiki.core.source

import org.junit.Assert.assertEquals
import org.junit.Test

/** The resolver script is re-run on a schedule; how soon the first run happens is what a healthy
 * embed feels, and where the schedule settles is what a slow one costs. */
class BrowserPlayerProbeScheduleTest {
    @Test
    fun `probing starts quickly`() {
        assertEquals(125L, nextProbeDelayMs(0))
        assertEquals(250L, nextProbeDelayMs(1))
    }

    @Test
    fun `probing settles at the steady interval`() {
        assertEquals(500L, nextProbeDelayMs(2))
        assertEquals(500L, nextProbeDelayMs(3))
        assertEquals(500L, nextProbeDelayMs(24))
    }
}
