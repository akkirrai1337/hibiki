package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchSource

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
