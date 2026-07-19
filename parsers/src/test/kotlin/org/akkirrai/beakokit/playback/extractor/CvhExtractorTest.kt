package org.akkirrai.beakokit.playback.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CvhExtractorTest {
    @Test
    fun `extracts cvh stream via playlist and video api`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/player/sv/playlist" -> {
                    assertEquals("745", request.url.parameters["pub"])
                    assertEquals("40748", request.url.parameters["id"])
                    assertEquals("mali", request.url.parameters["aggr"])
                    assertNull(request.url.parameters["vstu"])
                    respond(
                        content = """
                            {
                              "items": [
                                {
                                  "episode": 1,
                                  "vkId": 101,
                                  "voiceStudio": "AnilibriaTV",
                                  "voiceType": "sub"
                                },
                                {
                                  "episode": 1,
                                  "vkId": 202,
                                  "voiceStudio": "OtherStudio",
                                  "voiceType": "dub"
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                "/api/v1/player/sv/video/101" -> respond(
                    content = """
                        {
                          "failoverHost": "video.cvh.example.com",
                          "sources": {
                            "hlsUrl": "https://203.0.113.10/master.m3u8",
                            "dashUrl": "https://203.0.113.10/manifest.mpd",
                            "mpegLowestUrl": "https://203.0.113.10/240.mp4",
                            "mpegLowUrl": "https://203.0.113.10/360.mp4",
                            "mpegMediumUrl": "https://203.0.113.10/480.mp4",
                            "mpegHighUrl": "https://203.0.113.10/720.mp4",
                            "mpegFullHdUrl": "https://203.0.113.10/1080.mp4"
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val streams = CvhExtractor(client).extractVariants(
            PlayerLink(
                url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AnilibriaTV&anime_id=40748&episode=1&dubbing=%D0%9E%D0%B7%D0%B2%D1%83%D1%87%D0%BA%D0%B0+AniLibria",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals(7, streams.size)
        assertEquals("https://video.cvh.example.com/manifest.mpd", streams[0].url)
        assertEquals(StreamType.DASH, streams[0].type)
        assertEquals("https://ru.yummyani.me/", streams[0].headers[HttpHeaders.Referrer])
        assertEquals("https://video.cvh.example.com/master.m3u8", streams[1].url)
        assertEquals(StreamType.HLS, streams[1].type)
        assertEquals("https://video.cvh.example.com/1080.mp4", streams[2].url)
        assertEquals(StreamType.MP4, streams[2].type)
        assertEquals("1080p", streams[2].quality)
        assertEquals("https://video.cvh.example.com/240.mp4", streams[6].url)
        assertEquals("240p", streams[6].quality)
        client.close()
    }
}
