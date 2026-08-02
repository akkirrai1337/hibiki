package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.akkirrai.hibiki.shared.model.WatchSource

class WatchFlowDestinationTest {
    @Test
    fun `back from episodes returns to sources`() {
        val source = WatchSource(sourceId = "source", title = "Dub", episodeCount = 1)

        assertEquals(
            WatchFlowDestination.Sources,
            WatchFlowDestination.Episodes(source).backDestination(),
        )
    }

    @Test
    fun `back from sources closes watch flow`() {
        assertNull(WatchFlowDestination.Sources.backDestination())
    }
}
