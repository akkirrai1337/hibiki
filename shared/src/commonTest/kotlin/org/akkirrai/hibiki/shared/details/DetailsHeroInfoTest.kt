package org.akkirrai.hibiki.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.Anime

class DetailsHeroInfoTest {
    @Test
    fun resolvesTypeYearEpisodesAndStudio() {
        val result = resolveDetailsHeroInfo(
            Anime("1", "Title", "TV | 2024", "12 episodes", "Finished", studios = listOf("Studio")),
            localizedEpisodeWord = "episodes",
        )
        assertEquals("TV", result.type)
        assertEquals("2024", result.releaseDate)
        assertEquals("12 episodes", result.episodes)
        assertEquals(13, result.nextEpisodeNumber)
        assertEquals("Studio", result.studio)
    }

    @Test
    fun localizesEpisodeLabelWithoutChangingEpisodeNumber() {
        val result = resolveDetailsHeroInfo(
            Anime("1", "Title", "TV | 2024", "12 episodes", "Ongoing"),
            localizedEpisodeWord = "\u0441\u0435\u0440\u0438\u0439",
        )

        assertEquals("12 \u0441\u0435\u0440\u0438\u0439", result.episodes)
        assertEquals(13, result.nextEpisodeNumber)
        assertEquals("Ongoing", result.status)
    }

    @Test
    fun normalizesSourceTypeKeysForDetails() {
        val result = resolveDetailsHeroInfo(
            Anime("1", "Title", "TV_SHORT | 2024", "", ""),
            localizedEpisodeWord = "episodes",
        )

        assertEquals("TV SHORT", result.type)
    }

    @Test
    fun recognizesAnnouncementAndOngoingStatuses() {
        assertTrue(isAnnouncementStatus("announcement"))
        assertTrue(isAnnouncementStatus("", "announcement"))
        assertTrue(isOngoingStatus("ongoing"))
    }
}
