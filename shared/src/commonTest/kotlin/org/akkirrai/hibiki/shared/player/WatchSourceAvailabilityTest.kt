package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.model.WatchSourceSelection

class WatchSourceAvailabilityTest {
    @Test
    fun acceptsResolvedSourceOrStoredSourceTitle() {
        val selection = WatchSourceSelection("title", sourceId = null, sourceTitle = null, autoSelect = false)
        assertTrue(hasWatchSource(WatchSource("id", "Title", null, null), selection))
        assertTrue(hasWatchSource(null, selection.copy(sourceTitle = "Stored source")))
        assertFalse(hasWatchSource(null, selection))
    }
}
