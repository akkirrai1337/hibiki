package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonNull
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort

class ExternalSourceRuntimePayloadsTest {
    @Test
    fun searchPayloadPreservesAllRequestFields() {
        val payload = ExternalSourceRuntimePayloads.search(
            AnimeSearchRequest(
                query = "frieren",
                limit = 10,
                offset = 20,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOf("tv"),
                statusAliases = listOf("ongoing"),
                includedGenreAliases = listOf("fantasy"),
                excludedGenreAliases = listOf("horror"),
                yearFrom = 2020,
                yearTo = 2024,
            ),
        )

        assertEquals("frieren", payload["query"]?.toString()?.trim('"'))
        assertEquals(10, payload["limit"]?.toString()?.toInt())
        assertEquals(20, payload["offset"]?.toString()?.toInt())
        assertEquals("RATING", payload["sort"]?.toString()?.trim('"'))
        assertEquals("[\"tv\"]", payload["typeAliases"].toString())
        assertEquals("[\"ongoing\"]", payload["statusAliases"].toString())
        assertEquals("[\"fantasy\"]", payload["includedGenreAliases"].toString())
        assertEquals("[\"horror\"]", payload["excludedGenreAliases"].toString())
        assertEquals(2020, payload["yearFrom"]?.toString()?.toInt())
        assertEquals(2024, payload["yearTo"]?.toString()?.toInt())
    }

    @Test
    fun searchPayloadKeepsOptionalYearsAsNull() {
        val payload = ExternalSourceRuntimePayloads.search(AnimeSearchRequest())

        assertEquals(JsonNull, payload["yearFrom"])
        assertEquals(JsonNull, payload["yearTo"])
    }

    @Test
    fun detailsPayloadContainsStableIdField() {
        assertEquals("title-1", ExternalSourceRuntimePayloads.details("title-1")["id"]?.toString()?.trim('"'))
    }
}
