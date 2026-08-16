package org.akkirrai.hibiki.shared.catalog
import org.akkirrai.hibiki.shared.catalog.presentation.*
import org.akkirrai.hibiki.shared.catalog.sort.*

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
