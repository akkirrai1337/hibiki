package org.akkirrai.beakokit.source.aniliberty.internal

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
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniLibertyCatalogClientTest {
    @Test
    fun `parses latest releases and weekly schedule`() = runBlocking {
        var latestLimit: String? = null
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "primary.test") {
                return@MockEngine respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
            val payload = when (request.url.encodedPath) {
                "/api/v1/anime/releases/latest" -> {
                    latestLimit = request.url.parameters["limit"]
                    "[$RELEASE]"
                }
                "/api/v1/anime/schedule/week" -> "[{\"release\":$RELEASE}]"
                else -> error("Unexpected URL: ${request.url}")
            }
            respond(payload, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyCatalogClient(
            client,
            listOf("https://primary.test/api/v1", "https://mirror.test/api/v1"),
        )

        val latest = source.latest(limit = 100).single()
        assertEquals("Example", latest.displayName)
        assertEquals("50", latestLimit)
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
        val source = AniLibertyCatalogClient(client, "https://ani.test/api/v1")

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
        val source = AniLibertyCatalogClient(client, "https://ani.test/api/v1")

        source.search(
            AnimeSearchRequest(
                limit = 75,
                sort = AnimeSearchSort.TITLE,
                excludedGenreAliases = listOf("14"),
            )
        )

        val parameters = checkNotNull(capturedParameters)
        assertEquals("50", parameters["limit"])
        assertEquals("FRESH_AT_DESC", parameters["f[sorting]"])
        assertNull(parameters["f[genres]"])
        client.close()
    }

    @Test
    fun `uses loaded episodes when ongoing total is unknown`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                RELEASE_WITH_EPISODES,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyCatalogClient(client, "https://ani.test/api/v1")

        val title = source.getById("10277")

        assertEquals(2, title.episodeCount)
        assertEquals(2, title.availableEpisodeCount)
        assertEquals("ongoing", title.status)
        client.close()
    }

    @Test
    fun `keeps planned total separate from available episodes`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                DANDELION_RELEASE,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyCatalogClient(client, "https://ani.test/api/v1")

        val title = source.getById("10213")

        assertEquals(7, title.episodeCount)
        assertEquals(5, title.availableEpisodeCount)
        client.close()
    }

    private companion object {
        const val RELEASE = """{"id":7,"type":{"value":"TV"},"year":2026,"name":{"main":"Example","english":"Example","alternative":null},"poster":{"src":"/poster.jpg","optimized":{"src":"/optimized.webp"}},"is_ongoing":true,"publish_day":{"value":3},"episodes_total":12}"""
        const val RELEASE_WITH_EPISODES = """{"id":10277,"type":{"value":"TV"},"year":2026,"name":{"main":"Вперёд, отряд мистики!","english":"Ghost Concert: Missing Songs","alternative":null},"is_ongoing":true,"episodes_total":null,"episodes":[{"id":1,"ordinal":1},{"id":2,"ordinal":2}]}"""
        const val DANDELION_RELEASE = """{"id":10213,"type":{"value":"WEB"},"year":2026,"name":{"main":"Одуванчик","english":"Dandelion","alternative":null},"is_ongoing":true,"episodes_total":7,"episodes":[{"id":1,"ordinal":1},{"id":2,"ordinal":2},{"id":3,"ordinal":3},{"id":4,"ordinal":4},{"id":5,"ordinal":5}]}"""
    }
}
