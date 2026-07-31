package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeDownloadRepositoryTest {
    @Test
    fun `android download states map to shared row actions`() {
        assertEquals(
            EpisodeDownloadActionState.Downloading(0.42f),
            EpisodeDownloadState.Downloading(0.42f).toEpisodeDownloadActionState(),
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
