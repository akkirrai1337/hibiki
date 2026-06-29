package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals

class VkExtractorTest {
    @Test
    fun `extracts hls dash and mp4 variants from vk embed`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/video_ext.php", request.url.encodedPath)
            assertEquals("-225898691", request.url.parameters["oid"])
            assertEquals("456239021", request.url.parameters["id"])
            assertEquals("1", request.url.parameters["js_api"])
            assertEquals("viqeo", request.url.parameters["partner_name"])
            respond(
                content = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <script>
                    window.cur.apiPrefetchCache = [{
                      "method": "video.getEmbed",
                      "response": {
                        "video": {
                          "files": {
                            "hls_fmp4": "https://cdn.example.com/master-fmp4.m3u8",
                            "hls": "https://cdn.example.com/master.m3u8",
                            "dash_sep": "https://cdn.example.com/manifest.mpd",
                            "mp4_360": "https://cdn.example.com/360.mp4",
                            "mp4_1080": "https://cdn.example.com/1080.mp4"
                          }
                        }
                      }
                    }];
                    window.apiPrefetchReadyResolve();
                    </script>
                    </body>
                    </html>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine)

        val streams = VkExtractor(client).extractVariants(
            PlayerLink(
                url = "https://ru.yummyani.me/iframeVK.html?id=-225898691_456239021",
                type = PlayerType.EMBED,
                quality = null,
                headers = mapOf(HttpHeaders.Referrer to "https://ru.yummyani.me/"),
            ),
        )

        assertEquals(5, streams.size)
        assertEquals("https://cdn.example.com/master-fmp4.m3u8", streams[0].url)
        assertEquals(StreamType.HLS, streams[0].type)
        assertEquals("https://ru.yummyani.me/", streams[0].headers[HttpHeaders.Referrer])
        assertEquals("https://cdn.example.com/master.m3u8", streams[1].url)
        assertEquals(StreamType.HLS, streams[1].type)
        assertEquals("https://cdn.example.com/manifest.mpd", streams[2].url)
        assertEquals(StreamType.DASH, streams[2].type)
        assertEquals("https://cdn.example.com/1080.mp4", streams[3].url)
        assertEquals(StreamType.MP4, streams[3].type)
        assertEquals("1080p", streams[3].quality)
        assertEquals("https://cdn.example.com/360.mp4", streams[4].url)
        assertEquals("360p", streams[4].quality)
        client.close()
    }

    @Test
    fun `falls back to html parsing when api prefetch cache is absent`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/video_ext.php", request.url.encodedPath)
            respond(
                content = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <script>
                    var playerParams = {
                      "hls": "https:\/\/cdn.example.com\/master.m3u8",
                      "mp4_360": "https:\/\/cdn.example.com\/360.mp4",
                      "mp4_720": "https:\/\/cdn.example.com\/720.mp4"
                    };
                    </script>
                    </body>
                    </html>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine)

        val streams = VkExtractor(client).extractVariants(
            PlayerLink(
                url = "https://ru.yummyani.me/iframeVK.html?id=-225898691_456239021",
                type = PlayerType.EMBED,
                quality = null,
                headers = mapOf(HttpHeaders.Referrer to "https://ru.yummyani.me/"),
            ),
        )

        assertEquals(3, streams.size)
        assertEquals("https://cdn.example.com/master.m3u8", streams[0].url)
        assertEquals(StreamType.HLS, streams[0].type)
        assertEquals("https://cdn.example.com/720.mp4", streams[1].url)
        assertEquals("720p", streams[1].quality)
        assertEquals("https://cdn.example.com/360.mp4", streams[2].url)
        client.close()
    }

    @Test
    fun `parses files block and data source variants from html`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/video_ext.php", request.url.encodedPath)
            respond(
                content = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <script>
                    var payload = {
                      "files": {
                        "hls": "https:\/\/cdn.example.com\/master.m3u8",
                        "mp4_360": "https:\/\/cdn.example.com\/360.mp4",
                        "mp4_1080": "https:\/\/cdn.example.com\/1080.mp4"
                      },
                      "trailer": null
                    };
                    </script>
                    <div data-video-src="https://cdn.example.com/480.mp4"></div>
                    </body>
                    </html>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine)

        val streams = VkExtractor(client).extractVariants(
            PlayerLink(
                url = "https://ru.yummyani.me/iframeVK.html?id=-225898691_456239021",
                type = PlayerType.EMBED,
                quality = null,
                headers = mapOf(HttpHeaders.Referrer to "https://ru.yummyani.me/"),
            ),
        )

        assertEquals(listOf(StreamType.HLS, StreamType.MP4, StreamType.MP4, StreamType.MP4), streams.map { it.type })
        assertEquals(listOf(null, "1080p", "480p", "360p"), streams.map { it.quality })
        assertEquals("https://cdn.example.com/480.mp4", streams[2].url)
        client.close()
    }
}
