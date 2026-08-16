package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchNavigationLockPolicyTest {
    @Test
    fun lockKeyChangesForSourcesEpisodesAndPlayerRoutes() {
        assertEquals("sources:anime-1", watchNavigationLockKey("anime-1", null, false))
        assertEquals("episodes:anime-1:source-1", watchNavigationLockKey("anime-1", "source-1", false))
        assertEquals("player:anime-1:source-1", watchNavigationLockKey("anime-1", "source-1", true))
        assertNull(watchNavigationLockKey(null, null, false))
    }
}
