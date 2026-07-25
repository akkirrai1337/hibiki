package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeTitleResolverTest {
    @Test
    fun extractsEpisodeNumberFromDefaultEnglishTitle() {
        assertEquals("12.5", fallbackEpisodeNumberFromTitle("Episode 12.5"))
        assertEquals("7", fallbackEpisodeNumberFromTitle(" episode 7 "))
    }

    @Test
    fun leavesCustomTitlesUntouched() {
        assertNull(fallbackEpisodeNumberFromTitle("Special"))
        assertNull(fallbackEpisodeNumberFromTitle("Episode"))
    }
}
