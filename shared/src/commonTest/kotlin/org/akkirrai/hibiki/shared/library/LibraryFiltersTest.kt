package org.akkirrai.hibiki.shared.library

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.Anime

class LibraryFiltersTest {
    @Test
    fun filtersMatchCategoryEntryMetadata() {
        val entry = LibraryEntry(
            anime = Anime(
                id = "1",
                title = "Demo",
                subtitle = "TV | 2024",
                episodesLabel = "12",
                status = "Ongoing",
                genres = listOf("Action", "Drama"),
            ),
            category = LibraryCategory.Watching,
        )

        assertTrue(LibrarySearchFilters(type = "TV", status = "ongoing", includedGenres = setOf("Action")).matches(entry))
        assertFalse(LibrarySearchFilters(type = "Movie").matches(entry))
        assertTrue(entry.anime.extractLibraryType() == "TV")
    }
}
