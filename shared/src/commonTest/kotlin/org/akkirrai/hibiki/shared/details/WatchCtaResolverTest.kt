package org.akkirrai.hibiki.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.model.TitleWatchState
import org.akkirrai.hibiki.shared.model.WatchSource

class WatchCtaResolverTest {
    @Test
    fun missingProgressOpensEpisodes() {
        val result = resolveWatchCtaData(null, emptyList(), WatchSource("source", "Source", 12))
        assertEquals(WatchCtaAction.OpenEpisodes, result.action)
    }

    @Test
    fun inProgressEpisodeOpensPlayerWithRemainingTime() {
        val progress = TitleWatchState("title", "episode", 2.0, "source", "voice", "Source", null, 1000, 1000, 1)
        val item = EpisodeWatchProgress("title", "episode", 2.0, "source", "voice", "Source", null, 5000, 120000, 2)
        val result = resolveWatchCtaData(progress, listOf(item), WatchSource("source", "Source", 12))
        assertEquals(WatchCtaAction.OpenPlayer, result.action)
        assertEquals(2.0, result.episodeNumber)
        assertEquals(1L, result.remainingMinutes)
    }
}
