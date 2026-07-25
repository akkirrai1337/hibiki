package org.akkirrai.hibiki.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

class AnimeCatalogRepositoryTest {
    @Test
    fun searchMatchesTitlesSubtitlesAndGenres() = runTest {
        val repository = PrototypeAnimeCatalogRepository

        assertEquals(6, repository.initialItems.size)
        assertEquals("The Apothecary Diaries", repository.search("apothecary").single().title)
        assertEquals("Frieren: Beyond Journey's End", repository.search("Fantasy").first().title)
        assertEquals(
            listOf("The Apothecary Diaries", "Violet Evergarden"),
            repository.search(
                AnimeCatalogQuery(
                    pageSize = 2,
                    filters = AnimeSearchFilters(includedGenreAliases = setOf("Drama")),
                ),
            ).items.map { it.title },
        )
        assertEquals(
            listOf("Adventure", "Comedy", "Drama", "Fantasy", "Mystery", "Romance", "Slice of life", "Thriller"),
            repository.filterCatalog().genreOptions.map { it.id },
        )
    }
}
