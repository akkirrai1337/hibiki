package org.akkirrai.hibiki.shared.library

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

class LibraryFilterMappingsTest {
    @Test
    fun sharedCatalogFiltersRoundTripThroughLibraryFilters() {
        val catalog = LibraryFilterCatalog(
            typeOptions = listOf("TV", "Movie"),
            statusOptions = listOf("Ongoing", "Released"),
            genreOptions = listOf("Action", "Drama"),
        )
        val filters = AnimeSearchFilters(
            typeAlias = "tv",
            statusAlias = "ongoing",
            includedGenreAliases = setOf("Action"),
            excludedGenreAliases = setOf("Drama"),
            yearFrom = 2020,
            yearTo = 2026,
        )

        assertEquals(
            LibrarySearchFilters(
                type = "TV",
                status = "Ongoing",
                includedGenres = setOf("Action"),
                excludedGenres = setOf("Drama"),
                yearFrom = 2020,
                yearTo = 2026,
            ),
            filters.toLibrarySearchFilters(catalog),
        )
        assertEquals(filters, filters.toLibrarySearchFilters(catalog).toAnimeSearchFilters())
    }
}
