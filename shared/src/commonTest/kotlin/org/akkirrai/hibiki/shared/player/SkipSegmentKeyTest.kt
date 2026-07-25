package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.PlaybackSegment
import org.akkirrai.hibiki.shared.model.PlaybackSegmentType

class SkipSegmentKeyTest {
    @Test
    fun includesEpisodeAndSegmentBounds() {
        val segment = PlaybackSegment(PlaybackSegmentType.Opening, 1_000L, 20_000L)
        assertEquals("episode:Opening:1000:20000", buildSkipSegmentKey("episode", segment))
    }
}
