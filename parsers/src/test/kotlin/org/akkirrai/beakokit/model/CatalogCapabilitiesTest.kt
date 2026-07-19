package org.akkirrai.beakokit.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogCapabilitiesTest {
    @Test
    fun `unsupported request options are silently removed`() {
        val capabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE, AnimeSearchSort.RATING),
            supportedFilters = setOf(
                AnimeSearchFilter.STATUS,
                AnimeSearchFilter.INCLUDED_GENRES,
            ),
        )

        val adapted = capabilities.adapt(
            AnimeSearchRequest(
                sort = AnimeSearchSort.TITLE,
                typeAliases = listOf("tv"),
                statusAliases = listOf("ongoing"),
                includedGenreAliases = listOf("action"),
                excludedGenreAliases = listOf("horror"),
                yearFrom = 2020,
                yearTo = 2025,
            ),
        )

        assertEquals(AnimeSearchSort.RELEVANCE, adapted.sort)
        assertTrue(adapted.typeAliases.isEmpty())
        assertEquals(listOf("ongoing"), adapted.statusAliases)
        assertEquals(listOf("action"), adapted.includedGenreAliases)
        assertTrue(adapted.excludedGenreAliases.isEmpty())
        assertEquals(null, adapted.yearFrom)
        assertEquals(null, adapted.yearTo)
    }
}
