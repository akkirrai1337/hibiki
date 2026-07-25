package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.model.WatchSourceSelection

class WatchProgressResolverTest {
    @Test
    fun resolvesSelectedSourceWithFallback() {
        val sources = listOf(WatchSource("a", "A", 3), WatchSource("b", "B", 3))
        assertEquals("b", resolveWatchSource(sources, WatchSourceSelection("t", "b", "B", autoSelect = false))?.sourceId)
        assertEquals("a", resolveWatchSource(sources, WatchSourceSelection("t", "missing", null, autoSelect = false))?.sourceId)
    }

    @Test
    fun resolvesNextEpisodeAfterWatchedAndSavedEpisodes() {
        val progress = listOf(progress("1", 1.0, 10000, 10000), progress("3", 3.0, 500, 10000))
        assertEquals(3.0, resolveNextEpisodeNumber(progress, 5))
        assertEquals(1.0, resolveNextEpisodeNumber(emptyList(), 5))
        assertNull(resolveNextEpisodeNumber(listOf(progress("5", 5.0, 10000, 10000)), 5))
    }

    @Test
    fun resolvesLatestUnfinishedProgress() {
        val progress = listOf(progress("1", 1.0, 500, 10000), progress("2", 2.0, 300, 10000))
        assertEquals("2", resolveResumeWatchState(progress)?.episodeId)
    }

    @Test
    fun filtersProgressBySelectedSourceAndKeepsFallback() {
        val progress = listOf(
            progress("a", 1.0, 500, 10000, sourceId = "a"),
            progress("b", 2.0, 500, 10000, sourceId = "b"),
        )
        assertEquals(listOf("b"), filterProgressForSource(progress, WatchSource("b", "B", 2)).map { it.episodeId })
        val fallback = resolveSourceProgress(null, emptyList())
        assertEquals(null, fallback)
    }

    private fun progress(id: String, number: Double, position: Long, duration: Long, sourceId: String = "source") = EpisodeWatchProgress(
        titleId = "title", episodeId = id, episodeNumber = number, sourceId = sourceId, voiceoverId = "voice",
        sourceTitle = "Source", positionMs = position, durationMs = duration, updatedAt = number.toLong(),
    )
}
