package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRegistryTest {
    @Test
    fun `registry reports unknown source as not found`() {
        val registry = CatalogSourceRegistry(SourceCatalog(emptyList()))

        val error = assertFailsWith<SourceNotRegisteredException> {
            registry.requireInfo(SourceId("missing"))
        }

        assertEquals(SourceErrorCode.NOT_FOUND, error.code)
    }
}
