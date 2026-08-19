package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.player.model.EpisodeWatchProgress

class WatchProgressResolverTest {
    @Test
    fun resolvesLatestUnfinishedProgress() {
        val progress = listOf(progress("1", 1.0, 500, 10000), progress("2", 2.0, 300, 10000))
        assertEquals("2", resolveResumeWatchState(progress)?.episodeId)
    }

    private fun progress(id: String, number: Double, position: Long, duration: Long, sourceId: String = "source") = EpisodeWatchProgress(
        titleId = "title", episodeId = id, episodeNumber = number, sourceId = sourceId, voiceoverId = "voice",
        sourceTitle = "Source", positionMs = position, durationMs = duration, updatedAt = number.toLong(),
    )
}
