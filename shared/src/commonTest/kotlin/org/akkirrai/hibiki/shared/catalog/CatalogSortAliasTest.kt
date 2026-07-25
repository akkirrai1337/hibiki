package org.akkirrai.hibiki.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSortAliasTest {
    @Test
    fun createsCanonicalAliases() {
        assertEquals("alphabetical", CatalogSort.Alphabetical.toAlias())
        assertEquals("popular", CatalogSort.Popular.toAlias())
        assertEquals("updated", CatalogSort.Updated.toAlias())
    }
}
