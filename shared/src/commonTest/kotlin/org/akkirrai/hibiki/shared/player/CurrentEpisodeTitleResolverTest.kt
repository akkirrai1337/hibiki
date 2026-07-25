package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.WatchEpisode

class CurrentEpisodeTitleResolverTest {
    @Test
    fun prefersNonBlankPlaybackTitle() {
        assertEquals(
            "Custom title",
            resolveCurrentEpisodeTitle(" Custom title ", "2", listOf(WatchEpisode("2", 2.0, null))),
        )
    }

    @Test
    fun fallsBackToCurrentEpisodeNumber() {
        assertEquals(
            "Episode 2.5",
            resolveCurrentEpisodeTitle(null, "2", listOf(WatchEpisode("2", 2.5, null))),
        )
        assertEquals("", resolveCurrentEpisodeTitle(" ", "missing", emptyList()))
    }
}
