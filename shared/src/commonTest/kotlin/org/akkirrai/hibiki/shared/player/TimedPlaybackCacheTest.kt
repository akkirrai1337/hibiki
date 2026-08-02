package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimedPlaybackCacheTest {
    @Test
    fun returnsValuesUntilTtlAndThenExpiresThem() {
        var now = 1_000L
        val cache = TimedPlaybackCache<String, String>(
            ttlMillis = 5_000L,
            nowMillis = { now },
        )

        cache.put("episode", "stream")
        assertEquals("stream", cache.get("episode"))

        now += 4_999L
        assertEquals("stream", cache.get("episode"))

        now += 1L
        assertNull(cache.get("episode"))
    }
}
