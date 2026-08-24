package org.akkirrai.hibiki.core.anilist

import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.hibiki.catalog.model.AniListMatchHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListMatcherTest {
    private val matcher = TitleMatcher()

    private fun hints(
        russianName: String? = null,
        englishName: String? = null,
        originalName: String? = null,
        year: Int? = null,
        type: String? = null,
        episodeCount: Int? = null,
    ) = AniListMatchHints(
        russianName = russianName,
        englishName = englishName,
        originalName = originalName,
        japaneseName = null,
        synonyms = emptyList(),
        year = year,
        type = type,
        episodeCount = episodeCount,
    )

    @Test
    fun `picks the candidate with the highest confidence above the threshold`() {
        val candidates = listOf(
            AniListSearchMedia(
                id = 1,
                seasonYear = 2018,
                format = "TV",
                episodes = 13,
                title = AniListTitle(
                    romaji = "Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai",
                    english = "Rascal Does Not Dream of Bunny Girl Senpai",
                ),
            ),
            AniListSearchMedia(
                id = 2,
                seasonYear = 2019,
                format = "TV",
                episodes = 12,
                title = AniListTitle(romaji = "Some Unrelated Anime"),
            ),
        )

        val match = AniListMatcher.bestMatch(
            matcher = matcher,
            hints = hints(
                russianName = "Этот глупый свин не понимает мечту девочки-зайки",
                englishName = "Rascal Does Not Dream of Bunny Girl Senpai",
                originalName = "Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai",
                year = 2018,
                type = "tv",
                episodeCount = 13,
            ),
            candidates = candidates,
        )

        assertEquals(1, match?.id)
    }

    @Test
    fun `returns null when nothing clears the confidence threshold`() {
        val candidates = listOf(
            AniListSearchMedia(
                id = 1,
                seasonYear = 2005,
                format = "MOVIE",
                episodes = 1,
                title = AniListTitle(romaji = "Completely Unrelated Title"),
            ),
        )

        val match = AniListMatcher.bestMatch(
            matcher = matcher,
            hints = hints(originalName = "Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai", year = 2018, episodeCount = 13),
            candidates = candidates,
        )

        assertNull(match)
    }

    @Test
    fun `returns null for an empty candidate list`() {
        val match = AniListMatcher.bestMatch(
            matcher = matcher,
            hints = hints(originalName = "Anything"),
            candidates = emptyList(),
        )

        assertNull(match)
    }
}
