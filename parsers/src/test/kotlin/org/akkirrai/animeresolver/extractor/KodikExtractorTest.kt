package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.StreamType
import org.akkirrai.animeresolver.model.VideoSegmentType
import org.akkirrai.animeresolver.core.SourceException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KodikExtractorTest {
    @Test
    fun `extracts highest quality from ftor response`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p" -> respond(
                    content = PAGE_HTML,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                "/ftor" -> respond(
                    content = FTOR_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                ).also {
                    requestBody = request.body.toByteArray().decodeToString()
                }

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val stream = KodikExtractor(client).extract(
            PlayerLink(
                url = "https://kodikplayer.com/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals("https://cdn.example/720.m3u8", stream.url)
        assertEquals(StreamType.HLS, stream.type)
        assertEquals("720p", stream.quality)
        assertEquals(1, stream.segments.size)
        assertEquals(VideoSegmentType.OPENING, stream.segments.single().type)
        assertEquals(52_000L, stream.segments.single().startMs)
        assertEquals(142_000L, stream.segments.single().endMs)
        assertContains(requestBody, "d=kodik.cc")
        assertContains(requestBody, "pd=kodikplayer.com")
        assertContains(requestBody, "ref=https%3A%2F%2Fyummy.test%2Fwatch")
        assertContains(requestBody, "bad_user=false")
        assertContains(requestBody, "cdn_is_working=false")
        assertContains(requestBody, "type=seria")
        assertContains(requestBody, "hash=db59e08c1dfd13e437ad6ef5a701c450")
        assertContains(requestBody, "id=337505")
        assertContains(requestBody, "info=%7B%7D")
        assertEquals(false, requestBody.contains("translations="))
        assertEquals(false, requestBody.contains("advert_debug="))
        assertEquals(false, requestBody.contains("first_url="))
        client.close()
    }

    @Test
    fun `returns every available quality from ftor response`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p" -> respond(
                    content = PAGE_HTML,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                "/ftor" -> respond(
                    content = FTOR_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val streams = KodikExtractor(client).extractVariants(
            PlayerLink(
                url = "https://kodikplayer.com/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals(listOf("720p", "360p"), streams.map { it.quality })
        assertEquals(
            listOf("https://cdn.example/720.m3u8", "https://cdn.example/360.m3u8"),
            streams.map { it.url },
        )
        client.close()
    }

    @Test
    fun `fails fast when mandatory url params are missing`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p" -> respond(
                    content = BROKEN_PAGE_HTML,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val error = assertFailsWith<SourceException> {
            KodikExtractor(client).extract(
                PlayerLink(
                    url = "https://kodikplayer.com/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )
        }

        assertContains(error.message.orEmpty(), "obyazatel'nye parametry")
        client.close()
    }

    @Test
    fun `decodes encoded ref from kodik page before posting to ftor`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/season/112161/f0c38c4699af50e6e236e70617c429bf/720p" -> respond(
                    content = ENCODED_REF_PAGE_HTML,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                "/ftor" -> respond(
                    content = FTOR_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                ).also {
                    requestBody = request.body.toByteArray().decodeToString()
                }

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        KodikExtractor(client).extract(
            PlayerLink(
                url = "https://kodikplayer.com/season/112161/f0c38c4699af50e6e236e70617c429bf/720p?translations=false&only_episode=true&only_season=true&episode=1",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertContains(requestBody, "ref=https%3A%2F%2Fru.yummyani.me%2F")
        assertEquals(false, requestBody.contains("ref=https%253A%252F%252Fru.yummyani.me%252F"))
        assertContains(requestBody, "hash=34cd58be942b7f659aaf88d8d2b04f9c")
        assertContains(requestBody, "id=1483325")
        client.close()
    }

    private companion object {
        val PAGE_HTML = """
            <html>
              <script>
                var type = "seria";
                var videoId = "337505";
                var urlParams = '{"d":"kodik.cc","d_sign":"sig1","pd":"kodikplayer.com","pd_sign":"sig2","ref":"https://yummy.test/watch","ref_sign":"sig3","translations":false,"advert_debug":true,"first_url":false}';
                playerSettings.skipButton = parseSkipButton("00:52-02:22", "anime");
                vInfo.hash = 'db59e08c1dfd13e437ad6ef5a701c450';
              </script>
            </html>
        """.trimIndent()

        val BROKEN_PAGE_HTML = """
            <html>
              <script>
                var type = "seria";
                var videoId = "337505";
                var urlParams = '{"d":"kodik.cc","pd":"kodikplayer.com","pd_sign":"sig2","ref":"https://yummy.test/watch","ref_sign":"sig3"}';
                vInfo.hash = 'db59e08c1dfd13e437ad6ef5a701c450';
              </script>
            </html>
        """.trimIndent()

        val ENCODED_REF_PAGE_HTML = """
            <html>
              <script>
                var type = "seria";
                var videoId = "1483325";
                var urlParams = '{"d":"ru.yummyani.me","d_sign":"sig1","pd":"kodikplayer.com","pd_sign":"sig2","ref":"https%3A%2F%2Fru.yummyani.me%2F","ref_sign":"sig3","translations":false,"advert_debug":true,"first_url":false}';
                vInfo.hash = '34cd58be942b7f659aaf88d8d2b04f9c';
              </script>
            </html>
        """.trimIndent()

        val FTOR_JSON = """
            {
              "default": 360,
              "links": {
                "360": [
                  {
                    "src": "${encode("https://cdn.example/360.m3u8")}",
                    "type": "application/x-mpegURL"
                  }
                ],
                "720": [
                  {
                    "src": "${encode("https://cdn.example/720.m3u8")}",
                    "type": "application/x-mpegURL"
                  }
                ]
              }
            }
        """.trimIndent()

        fun encode(url: String): String {
            val base64 = java.util.Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
                .replace("=", "")
            return base64.map { ch ->
                if (ch.isLetter()) {
                    val base = if (ch.isUpperCase()) 'A' else 'a'
                    (((ch.code - base.code - 18 + 26) % 26) + base.code).toChar()
                } else {
                    ch
                }
            }.joinToString("")
        }
    }
}
