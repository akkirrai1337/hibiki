package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.WatchEpisode

class AdjacentEpisodeResolverTest {
    private val episodes = listOf(
        WatchEpisode("one", 1.0, "One"),
        WatchEpisode("two", 2.0, "Two"),
        WatchEpisode("three", 3.0, "Three"),
    )

    @Test
    fun resolvesPreviousAndNextByCurrentId() {
        assertEquals("one", resolveAdjacentEpisode(episodes, "two", null, -1)?.id)
        assertEquals("three", resolveAdjacentEpisode(episodes, "two", null, 1)?.id)
    }

    @Test
    fun fallsBackToEpisodeNumberWhenIdIsMissing() {
        assertEquals("one", resolveAdjacentEpisode(episodes, "missing", 2.0, -1)?.id)
        assertEquals("three", resolveAdjacentEpisode(episodes, "missing", 2.0, 1)?.id)
    }
}
