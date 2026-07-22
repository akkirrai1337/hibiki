package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.WatchEpisode

class CurrentEpisodeResolverTest {
    private val episodes = listOf(
        WatchEpisode("one", 1.0, "One"),
        WatchEpisode("two", 2.0, "Two"),
    )

    @Test
    fun prefersRequestedIdThenRequestedNumber() {
        assertEquals("one", resolveCurrentEpisode("one", 2.0, episodes, emptyList())?.id)
        assertEquals("two", resolveCurrentEpisode("missing", 2.0, episodes, emptyList())?.id)
    }

    @Test
    fun usesPreviousEpisodeNumberAndSavedNumberAsFallbacks() {
        val previous = listOf(WatchEpisode("missing", 1.0, "Previous"))
        assertEquals("one", resolveCurrentEpisode("missing", null, episodes, previous)?.id)
        assertEquals("two", resolveCurrentEpisode("missing", null, episodes, emptyList(), 2.0)?.id)
    }
}
