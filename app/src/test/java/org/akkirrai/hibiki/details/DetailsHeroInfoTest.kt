package org.akkirrai.hibiki.details
import org.akkirrai.hibiki.details.data.*
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.screen.*
import org.akkirrai.hibiki.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.akkirrai.hibiki.catalog.model.Anime

class DetailsHeroInfoTest {
    @Test
    fun resolvesTypeYearEpisodesAndStudio() {
        val result = resolveDetailsHeroInfo(
            Anime("1", "Title", "TV | 2024", "12 episodes", "Finished", studios = listOf("Studio")),
        )
        assertEquals("TV", result.type)
        assertEquals("2024", result.releaseDate)
        assertEquals("12 episodes", result.episodes)
        assertEquals(13, result.nextEpisodeNumber)
        assertEquals("Studio", result.studio)
    }

    @Test
    fun normalizesSourceTypeKeysForDetails() {
        val result = resolveDetailsHeroInfo(
            Anime("1", "Title", "TV_SHORT | 2024", "", ""),
        )

        assertEquals("TV SHORT", result.type)
    }

    @Test
    fun recognizesAnnouncementStatus() {
        assertTrue(isAnnouncementStatus("announcement"))
        assertTrue(isAnnouncementStatus("", "announcement"))
    }
}
