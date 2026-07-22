package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.WatchSource

class WatchSourcesStateResolverTest {
    @Test
    fun mergesSourcesByIdWhileKeepingLatestEntry() {
        val primary = listOf(source("one", "Primary"), source("two", "Two"))
        val secondary = listOf(source("one", "Offline"), source("three", "Three"))

        assertEquals(
            listOf("one", "two", "three"),
            mergeWatchSources(primary, secondary).map(WatchSource::sourceId),
        )
        assertEquals("Offline", mergeWatchSources(primary, secondary).first().title)
    }

    @Test
    fun limitsInitialSourcesAndReportsMoreItems() {
        val sources = (1..7).map { source("source-$it", it.toString()) }
        val visible = visibleWatchSources(sources, showAllItems = false)

        assertEquals(6, visible.size)
        assertTrue(hasMoreWatchSources(sources, visible, showAllItems = false))
        assertEquals(sources, visibleWatchSources(sources, showAllItems = true))
        assertFalse(hasMoreWatchSources(sources, sources, showAllItems = true))
    }

    private fun source(id: String, title: String) = WatchSource(
        sourceId = id,
        title = title,
        episodeCount = null,
    )
}
