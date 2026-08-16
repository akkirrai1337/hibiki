package org.akkirrai.hibiki.shared.home
import org.akkirrai.hibiki.shared.home.data.*
import org.akkirrai.hibiki.shared.home.model.*
import org.akkirrai.hibiki.shared.home.presentation.*
import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.*
import org.akkirrai.hibiki.shared.home.ui.*

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
