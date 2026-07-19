package org.akkirrai.beakokit.playback.validation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpStreamValidatorTest {
    @Test
    fun `validation propagates coroutine cancellation`() = runBlocking {
        val client = HttpClient(MockEngine { throw CancellationException("player changed") })

        assertFailsWith<CancellationException> {
            HttpStreamValidator(client).validate(
                VideoStream(
                    url = "https://video.example/media.m3u8",
                    type = StreamType.HLS,
                    quality = null,
                ),
            )
        }
        client.close()
    }

    @Test
    fun `master playlist selects highest bandwidth without fetching segment`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            when (request.url.encodedPath) {
                "/master.m3u8" -> respond(
                    content = """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=800000
                        low/index.m3u8
                        #EXT-X-STREAM-INF:BANDWIDTH=2400000
                        high/index.m3u8
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                )

                "/high/index.m3u8" -> respond(
                    content = """
                        #EXTM3U
                        #EXTINF:5,
                        segment-1.ts
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine)

        val result = HttpStreamValidator(client).validate(
            VideoStream(
                url = "https://video.example/master.m3u8",
                type = StreamType.HLS,
                quality = "auto",
            ),
        )

        assertTrue(result.success, result.message)
        assertEquals(
            listOf(
                "https://video.example/master.m3u8",
                "https://video.example/high/index.m3u8",
            ),
            requestedUrls,
        )
        client.close()
    }

    @Test
    fun `direct media playlist succeeds when it contains at least one segment`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            when (request.url.encodedPath) {
                "/media.m3u8" -> respond(
                    content = """
                        #EXTM3U
                        #EXTINF:5,
                        segment-1.ts
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                )

                "/segment-1.ts" -> error("Validator should not fetch HLS segments")

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine)

        val result = HttpStreamValidator(client).validate(
            VideoStream(
                url = "https://video.example/media.m3u8",
                type = StreamType.HLS,
                quality = "720p",
            ),
        )

        assertTrue(result.success, result.message)
        assertEquals(listOf("https://video.example/media.m3u8"), requestedUrls)
        client.close()
    }

    @Test
    fun `dash manifest validates init and first media segment`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            when (request.url.encodedPath) {
                "/video/1080.mpd" -> respond(
                    content = """
                        <MPD>
                          <Period>
                            <AdaptationSet contentType="video">
                              <Representation id="0" bandwidth="1000">
                                <SegmentTemplate
                                  initialization="init-stream${'$'}RepresentationID${'$'}.m4s"
                                  media="chunk-stream${'$'}RepresentationID${'$'}-${'$'}Number%05d${'$'}.m4s"
                                  startNumber="1" />
                              </Representation>
                            </AdaptationSet>
                          </Period>
                        </MPD>
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/dash+xml"),
                )

                "/video/init-stream0.m4s",
                "/video/chunk-stream0-00001.m4s",
                -> respond(
                    content = byteArrayOf(0, 1, 2, 3),
                    status = HttpStatusCode.PartialContent,
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine)

        val result = HttpStreamValidator(client).validate(
            VideoStream(
                url = "https://cdn.example/video/1080.mpd",
                type = StreamType.DASH,
                quality = "1080p",
            ),
        )

        assertTrue(result.success, result.message)
        assertEquals(
            listOf(
                "https://cdn.example/video/1080.mpd",
                "https://cdn.example/video/init-stream0.m4s",
                "https://cdn.example/video/chunk-stream0-00001.m4s",
            ),
            requestedUrls,
        )
        client.close()
    }
}
