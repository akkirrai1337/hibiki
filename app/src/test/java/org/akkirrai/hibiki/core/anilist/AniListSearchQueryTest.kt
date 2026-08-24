package org.akkirrai.hibiki.core.anilist

import org.akkirrai.hibiki.catalog.model.AniListMatchHints
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListSearchQueryTest {
    private fun hints(
        russianName: String? = null,
        englishName: String? = null,
        originalName: String? = null,
        synonyms: List<String> = emptyList(),
    ) = AniListMatchHints(
        russianName = russianName,
        englishName = englishName,
        originalName = originalName,
        japaneseName = null,
        synonyms = synonyms,
        year = null,
        type = null,
        episodeCount = null,
    )

    @Test
    fun `prefers englishName when present`() {
        val query = hints(englishName = "Spirited Away", russianName = "Унесенные призраками")
            .searchQuery(fallback = "fallback")
        assertEquals("Spirited Away", query)
    }

    @Test
    fun `falls back to a romaji synonym when the source only gives a Cyrillic name`() {
        // Mirrors YummyAnime: englishName/originalName null, real titles stuffed into synonyms.
        val query = hints(
            russianName = "Унесенные призраками",
            originalName = "Унесенные призраками",
            synonyms = listOf("Sen to Chihiro no Kamikakushi", "Spirited Away"),
        ).searchQuery(fallback = "fallback")
        assertEquals("Sen to Chihiro no Kamikakushi", query)
    }

    @Test
    fun `falls back to the Cyrillic name when nothing else is available`() {
        val query = hints(russianName = "Унесенные призраками", originalName = "Унесенные призраками")
            .searchQuery(fallback = "fallback")
        assertEquals("Унесенные призраками", query)
    }

    @Test
    fun `falls back to the display title when hints are entirely empty`() {
        val query = hints().searchQuery(fallback = "Display Title")
        assertEquals("Display Title", query)
    }
}
