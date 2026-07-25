package org.akkirrai.hibiki.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("Studio", result.studio)
    }
}
