package org.akkirrai.hibiki.shared.catalog
import org.akkirrai.hibiki.shared.catalog.presentation.*
import org.akkirrai.hibiki.shared.catalog.sort.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogCapabilities

class CatalogSortTest {
    @Test
    fun resolvesKnownAliases() {
        assertEquals(CatalogSort.Alphabetical, catalogSortFromAlias("title"))
        assertEquals(CatalogSort.Updated, catalogSortFromAlias("latest_releases"))
        assertEquals(CatalogSort.Popular, catalogSortFromAlias("unknown"))
    }

    @Test
    fun listsSupportedSortsUsingSourceAliases() {
        assertEquals(
            listOf(CatalogSort.Alphabetical, CatalogSort.Popular, CatalogSort.Updated),
            availableCatalogSorts(
                AnimeCatalogCapabilities(
                    supportedSorts = setOf("title", "rating", "latest_releases"),
                )
            ),
        )
    }
}
