package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `only active or completed downloads keep title saved`() {
        assertFalse(EpisodeDownloadActionState.NotDownloaded.keepsTitleSaved())
        assertTrue(EpisodeDownloadActionState.Queued.keepsTitleSaved())
        assertTrue(EpisodeDownloadActionState.Downloading(0.5f).keepsTitleSaved())
        assertTrue(EpisodeDownloadActionState.Paused.keepsTitleSaved())
        assertTrue(EpisodeDownloadActionState.Completed.keepsTitleSaved())
        assertFalse(EpisodeDownloadActionState.Failed.keepsTitleSaved())
    }
}
