package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceIdTest {
    @Test
    fun `source ids are safe for package storage paths`() {
        listOf("../outside", "nested/source", "nested\\source", "Uppercase")
            .forEach { value ->
                assertFailsWith<IllegalArgumentException> { SourceId(value) }
            }
    }

    @Test
    fun `legacy enum names are normalized when reading persisted values`() {
        assertEquals(SourceId("ani-liberty"), SourceId.parseStored("ANI_LIBERTY"))
    }
}
