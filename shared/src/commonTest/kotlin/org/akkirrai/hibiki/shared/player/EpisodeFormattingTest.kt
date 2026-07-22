package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress

class EpisodeFormattingTest {
    @Test
    fun formatsWholeAndFractionalEpisodeNumbers() {
        assertEquals("3", formatEpisodeNumber(3.0))
        assertEquals("3.5", formatEpisodeNumber(3.5))
    }

    @Test
    fun formatsEpisodeDurationAsMinutesAndSeconds() {
        assertEquals("00:00", formatEpisodeDuration(0))
        assertEquals("01:05", formatEpisodeDuration(65_000))
        assertEquals("12:34", formatEpisodeDuration(754_000))
    }

    @Test
    fun resolvesEpisodeProgressStatusAcrossSharedTargets() {
        assertEquals(EpisodeProgressStatus.NotStarted, resolveEpisodeProgressStatus(null))
        assertEquals(
            EpisodeProgressStatus.InProgress,
            resolveEpisodeProgressStatus(progress(positionMs = 500, durationMs = 10_000)),
        )
        assertEquals(
            EpisodeProgressStatus.Watched,
            resolveEpisodeProgressStatus(progress(positionMs = 9_500, durationMs = 10_000)),
        )
    }

    private fun progress(positionMs: Long, durationMs: Long) = EpisodeWatchProgress(
        titleId = "title",
        episodeId = "episode",
        episodeNumber = 1.0,
        sourceId = "source",
        voiceoverId = "voiceover",
        sourceTitle = "Source",
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 1L,
    )
}
