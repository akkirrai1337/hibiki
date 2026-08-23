package org.akkirrai.hibiki.details

import org.akkirrai.hibiki.details.screen.DetailsSessionCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailsSessionCacheTest {
    @Test
    fun storesAndReturnsValuesByKey() {
        val cache = DetailsSessionCache<String>()
        cache["a"] = "value-a"
        assertEquals("value-a", cache["a"])
        assertNull(cache["missing"])
    }

    @Test
    fun evictsLeastRecentlyUsedEntryOncePastLimit() {
        val cache = DetailsSessionCache<Int>()
        repeat(21) { index -> cache["id-$index"] = index }
        // The cache caps at 20 entries, evicting the least-recently-used one -- the very first
        // insert should be gone, but everything inserted after it should still be there.
        assertNull(cache["id-0"])
        assertEquals(1, cache["id-1"])
        assertEquals(20, cache["id-20"])
    }
}
