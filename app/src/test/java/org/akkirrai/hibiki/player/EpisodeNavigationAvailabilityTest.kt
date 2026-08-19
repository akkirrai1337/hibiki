package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.player.model.WatchEpisode

class EpisodeNavigationAvailabilityTest {
    @Test
    fun fallsBackToEpisodeNumberWhenThePlaybackIdDiffersFromTheListedEpisodeId() {
        val availability = resolveEpisodeNavigationAvailability(
            episodes = listOf(
                WatchEpisode("episode-1", 1.0, "Episode 1"),
                WatchEpisode("episode-2", 2.0, "Episode 2"),
                WatchEpisode("episode-3", 3.0, "Episode 3"),
            ),
            currentEpisodeId = "ios-playback-id",
            currentEpisodeNumber = 2.0,
        )

        assertEquals(EpisodeNavigationAvailability(hasPrevious = true, hasNext = true), availability)
    }
}
