package org.akkirrai.hibiki.feature.player

import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodesScreenLogicTest {
    @Test
    fun `scrolls to an unfinished episode`() {
        assertEquals(
            2,
            resolveEpisodeAutoScrollIndex(
                episodes = episodes(5),
                progressItems = listOf(progress(episode = 3, positionMs = 30_000, durationMs = 60_000)),
            ),
        )
    }

    @Test
    fun `scrolls to the episode after the highest completed episode`() {
        assertEquals(
            900,
            resolveEpisodeAutoScrollIndex(
                episodes = episodes(1_001),
                progressItems = listOf(
                    progress(episode = 1, positionMs = 60_000, durationMs = 60_000, updatedAt = 20),
                    progress(episode = 900, positionMs = 60_000, durationMs = 60_000, updatedAt = 10),
                ),
            ),
        )
    }

    @Test
    fun `keeps the final episode visible when it is completed`() {
        assertEquals(
            4,
            resolveEpisodeAutoScrollIndex(
                episodes = episodes(5),
                progressItems = listOf(progress(episode = 5, positionMs = 60_000, durationMs = 60_000)),
            ),
        )
    }

    private fun episodes(count: Int) = (1..count).map { number ->
        WatchEpisode(id = "episode-$number", number = number.toDouble(), title = null)
    }

    private fun progress(
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long = 1,
    ) = EpisodeWatchProgress(
        titleId = "title",
        episodeId = "episode-$episode",
        episodeNumber = episode.toDouble(),
        sourceId = "source",
        voiceoverId = "source",
        sourceTitle = "Source",
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )
}
