package org.akkirrai.animeresolver.core

import org.akkirrai.animeresolver.model.AnimeTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitleMatcherTest {
    private val matcher = TitleMatcher()

    @Test
    fun `normalization removes punctuation case and season words`() {
        assertEquals(
            "bunny girl senpai",
            matcher.normalize("  Bunny Girl Senpai: Season 2!! "),
        )
    }

    @Test
    fun `matching alternative english title with metadata is confident`() {
        val title = AnimeTitle(
            id = "37450",
            russianName = "Этот глупый свин не понимает мечту девочки-зайки",
            englishName = "Rascal Does Not Dream of Bunny Girl Senpai",
            originalName = "Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai",
            japaneseName = null,
            synonyms = emptyList(),
            year = 2018,
            type = "tv",
            episodeCount = 13,
            posterUrl = null,
            status = null,
            description = null,
        )

        val confidence = matcher.confidence(
            title = title,
            candidateNames = listOf("Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai"),
            candidateYear = 2018,
            candidateType = "TV",
            candidateEpisodes = 13,
        )

        assertTrue(confidence >= 0.99)
    }
}
