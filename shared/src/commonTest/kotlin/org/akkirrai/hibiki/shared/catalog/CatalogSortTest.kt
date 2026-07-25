package org.akkirrai.hibiki.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSortTest {
    @Test
    fun resolvesKnownAliases() {
        assertEquals(CatalogSort.Alphabetical, catalogSortFromAlias("title"))
        assertEquals(CatalogSort.Updated, catalogSortFromAlias("latest_releases"))
        assertEquals(CatalogSort.Popular, catalogSortFromAlias("unknown"))
    }
}
