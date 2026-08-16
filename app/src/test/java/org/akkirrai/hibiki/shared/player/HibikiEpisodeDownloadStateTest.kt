package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.player.EpisodeDownloadState

class HibikiEpisodeDownloadStateTest {
    @Test
    fun markEpisodeQueuedUpdatesOnlyRequestedEpisode() {
        val initial = mapOf("one" to EpisodeDownloadState.NotDownloaded)

        assertEquals(
            mapOf(
                "one" to EpisodeDownloadState.NotDownloaded,
                "two" to EpisodeDownloadState.Queued,
            ),
            initial.markEpisodeQueued("two"),
        )
    }

    @Test
    fun markEpisodeRemovedResetsRequestedEpisode() {
        val initial = mapOf("one" to EpisodeDownloadState.Completed)

        assertEquals(
            mapOf("one" to EpisodeDownloadState.NotDownloaded),
            initial.markEpisodeRemoved("one"),
        )
    }
}
