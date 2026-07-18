package org.akkirrai.beakokit.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SourceIdentityTest {
    @Test
    fun `source id accepts canonical slug`() {
        assertEquals("ani-liberty", SourceId("ani-liberty").value)
    }

    @Test
    fun `stored source id migrates legacy enum name`() {
        assertEquals(SourceId("yummy-anime"), SourceId.parseStored("YUMMY_ANIME"))
        assertEquals(SourceId("ani-liberty"), SourceId.parseStored("ANI_LIBERTY"))
    }

    @Test
    fun `invalid source id is rejected`() {
        assertFailsWith<IllegalArgumentException> { SourceId("Ani Liberty") }
        assertNull(SourceId.parseStored("ani_liberty"))
    }

    @Test
    fun `anime key preserves opaque native id`() {
        val key = AnimeKey(SourceId("ani-liberty"), "release:42/episode")

        assertEquals("source:ani-liberty:release:42/episode", key.value)
        assertEquals(key, AnimeKey.parse(key.value))
    }

    @Test
    fun `anime key reads legacy scoped id and writes canonical id`() {
        val key = AnimeKey.parse("source:ANI_LIBERTY:10213")

        assertEquals(AnimeKey(SourceId("ani-liberty"), "10213"), key)
        assertEquals("source:ani-liberty:10213", key?.value)
    }

    @Test
    fun `anime key has stable string serialization`() {
        val key = AnimeKey(SourceId("yummy-anime"), "42")
        val encoded = Json.encodeToString(AnimeKeySerializer, key)

        assertEquals("\"source:yummy-anime:42\"", encoded)
        assertEquals(key, Json.decodeFromString(AnimeKeySerializer, encoded))
    }
}
