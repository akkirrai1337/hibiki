package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackCompletionResolverTest {
    @Test
    fun acceptsTheAndroidEndTolerance() {
        assertTrue(isPlaybackComplete(positionMs = 99_000L, durationMs = 100_000L))
        assertTrue(isPlaybackComplete(positionMs = 100_000L, durationMs = 100_000L))
    }

    @Test
    fun rejectsAnEpisodeThatHasNotEnded() {
        assertFalse(isPlaybackComplete(positionMs = 98_999L, durationMs = 100_000L))
        assertFalse(isPlaybackComplete(positionMs = 0L, durationMs = 0L))
    }

    @Test
    fun doesNotTreatNegativeToleranceAsCompletion() {
        assertFalse(isPlaybackComplete(positionMs = 100_000L, durationMs = 100_000L, toleranceMs = -1L))
    }
}
