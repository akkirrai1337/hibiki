package org.akkirrai.hibiki.details
import org.akkirrai.hibiki.details.data.*
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.screen.*
import org.akkirrai.hibiki.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeLabelResolverTest {
    @Test
    fun extractsNextNumberFromReleasedEpisodeLabel() {
        assertEquals(13, extractNextEpisodeNumber("12 episodes"))
        assertEquals(1, extractNextEpisodeNumber("0 episodes"))
        assertNull(extractNextEpisodeNumber("Unknown"))
    }
}
