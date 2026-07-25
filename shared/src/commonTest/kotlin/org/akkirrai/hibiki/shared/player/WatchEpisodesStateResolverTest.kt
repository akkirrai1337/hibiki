package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.WatchEpisode

class WatchEpisodesStateResolverTest {
    @Test
    fun mergesEpisodesByIdAndSortsByNumber() {
        val primary = listOf(
            WatchEpisode(id = "two", number = 2.0, title = "Two"),
            WatchEpisode(id = "one", number = 1.0, title = "One"),
        )
        val secondary = listOf(
            WatchEpisode(id = "two", number = 2.0, title = "Offline two"),
            WatchEpisode(id = "three", number = 3.0, title = "Three"),
        )

        val merged = mergeWatchEpisodes(primary, secondary)
        assertEquals(listOf("one", "two", "three"), merged.map(WatchEpisode::id))
        assertEquals("Offline two", merged[1].title)
    }
}
