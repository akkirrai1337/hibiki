package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeDownloadRepositoryTest {
    @Test
    fun `android download states map to shared row actions`() {
        assertEquals(
            EpisodeDownloadActionState.NotDownloaded,
            EpisodeDownloadState.NotDownloaded.toEpisodeDownloadActionState(),
        )
        assertEquals(
            EpisodeDownloadActionState.Queued,
            EpisodeDownloadState.Queued.toEpisodeDownloadActionState(),
        )
        assertEquals(
            EpisodeDownloadActionState.Downloading(0.42f),
            EpisodeDownloadState.Downloading(0.42f).toEpisodeDownloadActionState(),
        )
        assertEquals(
            EpisodeDownloadActionState.Paused,
            EpisodeDownloadState.Paused.toEpisodeDownloadActionState(),
        )
        assertEquals(
            EpisodeDownloadActionState.Completed,
            EpisodeDownloadState.Completed.toEpisodeDownloadActionState(),
        )
        assertEquals(
            EpisodeDownloadActionState.Failed,
            EpisodeDownloadState.Failed.toEpisodeDownloadActionState(),
        )
    }
}
