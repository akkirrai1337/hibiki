package org.akkirrai.beakokit.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JsonAccessorsTest {
    private val payload = Json.parseToJsonElement(
        """
        {
            "title": "  Attack on Titan  ",
            "blank": "   ",
            "year": 2013,
            "rating": 8.7,
            "ongoing": false,
            "genres": ["Action", " Drama ", "", "  "],
            "poster": {"hq": "poster.webp"},
            "not_an_object": "oops"
        }
        """.trimIndent(),
    ).asObject() as JsonObject

    @Test
    fun `string trims and treats blank as absent`() {
        assertEquals("Attack on Titan", payload.string("title"))
        assertNull(payload.string("blank"))
        assertNull(payload.string("missing"))
    }

    @Test
    fun `int and double read primitives`() {
        assertEquals(2013, payload.int("year"))
        assertEquals(8.7, payload.double("rating"))
        assertEquals(false, payload.bool("ongoing"))
    }

    @Test
    fun `strings filters blanks from an array`() {
        assertEquals(listOf("Action", "Drama"), payload.strings("genres"))
        assertEquals(emptyList(), payload.strings("missing"))
    }

    @Test
    fun `obj reads a nested object and null for the wrong shape`() {
        assertEquals("poster.webp", payload.obj("poster")?.string("hq"))
        assertNull(payload.obj("title"))
        assertNull(payload.obj("missing"))
    }

    @Test
    fun `asObject and asArray are lenient about the wrong shape`() {
        assertNull(Json.parseToJsonElement("[1,2]").asObject())
        assertEquals(emptyList(), Json.parseToJsonElement("{}").asArray())
    }

    @Test
    fun `string on an object valued key still throws like jsonPrimitive does`() {
        assertFailsWith<IllegalArgumentException> { payload.string("poster") }
    }
}
