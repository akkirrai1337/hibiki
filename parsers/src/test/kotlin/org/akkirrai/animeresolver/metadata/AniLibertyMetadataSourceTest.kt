package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniLibertyMetadataSourceTest {
    @Test
    fun `parses latest releases and weekly schedule`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "primary.test") {
                return@MockEngine respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
            val payload = when (request.url.encodedPath) {
                "/api/v1/anime/releases/latest" -> "[$RELEASE]"
                "/api/v1/anime/schedule/week" -> "[{\"release\":$RELEASE}]"
                else -> error("Unexpected URL: ${request.url}")
            }
            respond(payload, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyMetadataSource(
            client,
            listOf("https://primary.test/api/v1", "https://mirror.test/api/v1"),
        )

        val latest = source.latest().single()
        assertEquals("Example", latest.displayName)
        assertTrue(latest.synonyms.isEmpty())
        assertEquals("https://anilibria.top/optimized.webp", latest.posterUrl)
        assertEquals(3, source.weeklySchedule().single().dayOfWeek)
        client.close()
    }

    @Test
    fun `applies supported catalog filters`() = runBlocking {
        var capturedParameters: io.ktor.http.Parameters? = null
        val client = HttpClient(MockEngine { request ->
            capturedParameters = request.url.parameters
            respond(
                "{\"data\":[$RELEASE]}",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyMetadataSource(client, "https://ani.test/api/v1")

        source.search(
            AnimeSearchRequest(
                query = " Example ",
                limit = 25,
                offset = 50,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOf("tv"),
                statusAliases = listOf("is_ongoing"),
                includedGenreAliases = listOf("15", "8"),
                yearFrom = 2020,
                yearTo = 2026,
            )
        )

        val parameters = checkNotNull(capturedParameters)
        assertEquals("3", parameters["page"])
        assertEquals("25", parameters["limit"])
        assertEquals("Example", parameters["f[search]"])
        assertEquals("TV", parameters["f[types]"])
        assertEquals("IS_ONGOING", parameters["f[publish_statuses]"])
        assertEquals("15,8", parameters["f[genres]"])
        assertEquals("2020", parameters["f[years][from_year]"])
        assertEquals("2026", parameters["f[years][to_year]"])
        assertEquals("RATING_DESC", parameters["f[sorting]"])
        client.close()
    }

    @Test
    fun `silently adapts unsupported filters and sorting`() = runBlocking {
        var capturedParameters: io.ktor.http.Parameters? = null
        val client = HttpClient(MockEngine { request ->
            capturedParameters = request.url.parameters
            respond(
                "{\"data\":[$RELEASE]}",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyMetadataSource(client, "https://ani.test/api/v1")

        source.search(
            AnimeSearchRequest(
                sort = AnimeSearchSort.TITLE,
                excludedGenreAliases = listOf("14"),
            )
        )

        val parameters = checkNotNull(capturedParameters)
        assertEquals("FRESH_AT_DESC", parameters["f[sorting]"])
        assertNull(parameters["f[genres]"])
        client.close()
    }

    private companion object {
        const val RELEASE = """{"id":7,"type":{"value":"TV"},"year":2026,"name":{"main":"Example","english":"Example","alternative":null},"poster":{"src":"/poster.jpg","optimized":{"src":"/optimized.webp"}},"is_ongoing":true,"publish_day":{"value":3},"episodes_total":12}"""
    }
}
