package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.SearchFilterOption

class AnimeSearchFilterCatalogSanitizationTest {
    @Test
    fun hidesUnsupportedAndUnlabeledFilters() {
        val catalog = AnimeSearchFilterCatalog(
            sortOptions = listOf(
                SearchFilterOption("rating_counters", ""),
                SearchFilterOption("", "Ignored"),
            ),
            typeOptions = listOf(SearchFilterOption("tv", "TV")),
            statusOptions = listOf(
                SearchFilterOption("ongoing", "ongoing"),
                SearchFilterOption("released", "released"),
            ),
            genreOptions = listOf(SearchFilterOption("123", "123")),
            capabilities = CatalogCapabilities(
                supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
                supportedFilters = setOf(AnimeSearchFilter.STATUS),
            ),
        )

        val sanitized = catalog.sanitizedForApp(
            preferEnglish = false,
            sourceLanguage = SourceLanguage.RUSSIAN,
        )

        assertEquals(listOf(SearchFilterOption("rating_counters", "Rating Counters")), sanitized.sortOptions)
        assertEquals(emptyList(), sanitized.typeOptions)
        assertEquals(
            listOf(
                SearchFilterOption("ongoing", "\u041e\u043d\u0433\u043e\u0438\u043d\u0433"),
                SearchFilterOption("released", "\u0412\u044b\u0448\u043b\u043e"),
            ),
            sanitized.statusOptions,
        )
        assertEquals(emptyList(), sanitized.genreOptions)
    }
}
