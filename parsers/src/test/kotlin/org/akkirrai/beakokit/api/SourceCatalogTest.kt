package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceCatalogTest {
    @Test
    fun `catalog resolves source by stable id`() {
        val info = sourceInfo("ani-liberty")
        val catalog = SourceCatalog(listOf(info))

        assertEquals(info, catalog.require(SourceId("ani-liberty")))
    }

    @Test
    fun `catalog rejects duplicate source ids`() {
        assertFailsWith<IllegalArgumentException> {
            SourceCatalog(listOf(sourceInfo("ani-liberty"), sourceInfo("ani-liberty")))
        }
    }

    private fun sourceInfo(id: String) = SourceInfo(
        id = SourceId(id),
        name = "Test",
        languages = setOf(SourceLanguage.RUSSIAN),
        website = "https://example.com",
    )
}
