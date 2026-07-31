package org.akkirrai.hibiki.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodesCountLabelTest {
    @Test
    fun formatsEnglishEpisodeCount() {
        assertEquals("13 episodes", formatEpisodesCountLabel(13, preferEnglish = true))
    }

    @Test
    fun formatsRussianEpisodeCount() {
        assertEquals("13 серий", formatEpisodesCountLabel(13, preferEnglish = false))
    }

    @Test
    fun preservesZeroCount() {
        assertEquals("0 episodes", formatEpisodesCountLabel(0, preferEnglish = true))
        assertEquals("0 серий", formatEpisodesCountLabel(0, preferEnglish = false))
    }
}
