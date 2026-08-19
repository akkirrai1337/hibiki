package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.player.model.EpisodeWatchProgress

class WatchProgressStatusTest {
    @Test
    fun appliesOneSecondCompletionTolerance() {
        val base = EpisodeWatchProgress("title", "episode", 1.0, "source", "voice", "Source", null, 0L, 10_000L, 0L)
        assertTrue(base.copy(positionMs = 9_000L).isWatchedToEnd())
        assertFalse(base.copy(positionMs = 8_999L).isWatchedToEnd())
        assertFalse(base.copy(durationMs = 0L, positionMs = 0L).isWatchedToEnd())
    }
}
