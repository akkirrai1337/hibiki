package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AksorExtractorTest {
    @Test
    fun `selects highest available dash quality`() = runBlocking {
        var refererHeader: String? = null
        val engine = MockEngine {
            refererHeader = it.headers[HttpHeaders.Referrer]
            respond(
                content = """
                    {
                      "qualities": {
                        "q480": "https://cdn.example/480.mpd",
                        "q1080": "https://cdn.example/1080.mpd",
                        "q4k": null
                      }
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val stream = AksorExtractor(client).extract(
            PlayerLink(
                url = "https://player.aksor.tv/video/test-id",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals("https://cdn.example/1080.mpd", stream.url)
        assertEquals(StreamType.DASH, stream.type)
        assertEquals("1080p", stream.quality)
        assertEquals("https://player.aksor.tv/video/test-id", refererHeader)
        client.close()
    }

    @Test
    fun `returns every available quality from aksor api`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "qualities": {
                        "q360": "https://cdn.example/360.m3u8",
                        "q1080": "https://cdn.example/1080.mpd",
                        "q4k": "https://cdn.example/2160.mpd"
                      }
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val streams = AksorExtractor(client).extractVariants(
            PlayerLink(
                url = "https://player.aksor.tv/video/test-id",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals(listOf("2160p", "1080p", "360p"), streams.map { it.quality })
        assertEquals(listOf(StreamType.DASH, StreamType.DASH, StreamType.HLS), streams.map { it.type })
        client.close()
    }

    @Test
    fun `falls back to meta video url when api returns no qualities`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/video/test-id" -> respond(
                    content = """{ "qualities": {} }""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                "/video/test-id" -> respond(
                    content = """
                        <html>
                          <head>
                            <meta name="video_url" content="https://cdn.example/fallback-720.m3u8">
                          </head>
                        </html>
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val stream = AksorExtractor(client).extract(
            PlayerLink(
                url = "https://player.aksor.tv/video/test-id",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals("https://cdn.example/fallback-720.m3u8", stream.url)
        assertEquals(StreamType.HLS, stream.type)
        assertNotNull(stream.headers[HttpHeaders.UserAgent])
        client.close()
    }
}
