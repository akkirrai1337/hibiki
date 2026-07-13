package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AniLibertyMetadataSourceTest {
    @Test
    fun `parses latest releases and weekly schedule`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val payload = when (request.url.encodedPath) {
                "/api/v1/anime/releases/latest" -> "[$RELEASE]"
                "/api/v1/anime/schedule/week" -> "[{\"release\":$RELEASE}]"
                else -> error("Unexpected URL: ${request.url}")
            }
            respond(payload, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val source = AniLibertyMetadataSource(client, "https://ani.test/api/v1")

        assertEquals("Example", source.latest().single().displayName)
        assertEquals(3, source.weeklySchedule().single().dayOfWeek)
        client.close()
    }

    private companion object {
        const val RELEASE = """{"id":7,"type":{"value":"TV"},"year":2026,"name":{"main":"Example","english":"Example"},"poster":{"src":"/poster.jpg"},"is_ongoing":true,"publish_day":{"value":3},"episodes_total":12}"""
    }
}
