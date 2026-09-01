package org.akkirrai.beakokit.extension

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.ScriptedResolverFixtures
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the Rhino-scripted Kodik resolver (`kodik.js`) against the same fixtures the compiled-in
 * [org.akkirrai.beakokit.playback.extractor.KodikExtractor] was proven against - the first step of
 * migrating player resolvers out of the APK and into hibiki-sources (see project plan: port the
 * existing fixture tests first, then flip the resolver on, then retire the Kotlin fallback once
 * parity holds).
 *
 * The resolver payload (`kodik.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedKodikResolverTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedResolverFixtures.isAvailable("kodik"), "kodik.js fixture is not present locally")
    }

    private fun installKodikResolver(host: SourceFixtureHost) =
        PlayerResolverExtensionRepository(java.nio.file.Files.createTempDirectory("kodik-resolver").toFile())
            .apply { install(ScriptedResolverFixtures.loadJson("kodik"), originRepositoryUrl = "repo") }
            .loadAll(host.context)
            .single()

    @Test
    fun `extracts highest quality from ftor response`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p", PAGE_HTML),
                jsonRoute("/ftor", FTOR_JSON, method = HttpMethod.Post),
            ),
        ).use { host ->
            val resolver = installKodikResolver(host)
            val stream = resolver.extract(
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

            val requestBody = host.requests.single { it.url.encodedPath == "/ftor" }.body
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
        }
    }

    @Test
    fun `returns every available quality from ftor response`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p", PAGE_HTML),
                jsonRoute("/ftor", FTOR_JSON, method = HttpMethod.Post),
            ),
        ).use { host ->
            val resolver = installKodikResolver(host)
            val streams = resolver.extractVariants(
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
        }
    }

    @Test
    fun `fails fast when mandatory url params are missing`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p", BROKEN_PAGE_HTML),
            ),
        ).use { host ->
            val resolver = installKodikResolver(host)
            val error = assertFailsWith<SourceException> {
                resolver.extract(
                    PlayerLink(
                        url = "https://kodikplayer.com/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p",
                        type = PlayerType.EMBED,
                        quality = null,
                    ),
                )
            }
            assertContains(error.message.orEmpty(), "did not expose required urlParams")
        }
    }

    @Test
    fun `decodes encoded ref from kodik page before posting to ftor`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/season/112161/f0c38c4699af50e6e236e70617c429bf/720p", ENCODED_REF_PAGE_HTML),
                jsonRoute("/ftor", FTOR_JSON, method = HttpMethod.Post),
            ),
        ).use { host ->
            val resolver = installKodikResolver(host)
            resolver.extract(
                PlayerLink(
                    url = "https://kodikplayer.com/season/112161/f0c38c4699af50e6e236e70617c429bf/720p",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            val requestBody = host.requests.single { it.url.encodedPath == "/ftor" }.body
            assertContains(requestBody, "ref=https%3A%2F%2Fru.yummyani.me%2F")
            assertEquals(false, requestBody.contains("ref=https%253A%252F%252Fru.yummyani.me%252F"))
            assertContains(requestBody, "hash=34cd58be942b7f659aaf88d8d2b04f9c")
            assertContains(requestBody, "id=1483325")
        }
    }

    @Test
    fun `uses player script endpoint and forwards cookies from page load`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(
                    path = "/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p",
                    body = SCRIPT_ENDPOINT_PAGE_HTML,
                    contentType = ContentType.Text.Html,
                    extraHeaders = mapOf("Set-Cookie" to "session=abc123; Path=/"),
                ),
                FixtureRoute(
                    path = "/assets/js/app.player_single.min.js",
                    // Plain base64, not kodik.js's shifted "src" encoding - Kodik's own player
                    // script literally hands this to the browser's native atob() as-is.
                    body = """const endpoint = atob("${java.util.Base64.getEncoder().encodeToString("/xfor".toByteArray())}");""",
                    contentType = ContentType.Text.JavaScript,
                ),
                FixtureRoute(
                    path = "/xfor",
                    body = FTOR_JSON,
                    method = HttpMethod.Post,
                ),
            ),
        ).use { host ->
            val resolver = installKodikResolver(host)
            val stream = resolver.extract(
                PlayerLink(
                    url = "https://kodikplayer.com/seria/337505/db59e08c1dfd13e437ad6ef5a701c450/720p",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals("https://cdn.example/720.m3u8", stream.url)
            val cookieHeader = host.requests.single { it.url.encodedPath == "/xfor" }.headers["Cookie"]
            assertEquals("session=abc123", cookieHeader)
        }
    }

    private fun htmlRoute(path: String, body: String) = FixtureRoute(path = path, body = body, contentType = ContentType.Text.Html)

    private fun jsonRoute(path: String, body: String, method: HttpMethod = HttpMethod.Get) =
        FixtureRoute(path = path, body = body, method = method, contentType = ContentType.Application.Json)

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

        val SCRIPT_ENDPOINT_PAGE_HTML = """
            <html>
              <script src="/assets/js/app.player_single.min.js"></script>
              <script>
                videoInfo.type = 'seria';
                videoInfo.id = '337505';
                var urlParams = '{"d":"kodik.cc","d_sign":"sig1","pd":"kodikplayer.com","pd_sign":"sig2","ref":"https://yummy.test/watch","ref_sign":"sig3"}';
                videoInfo.hash = 'db59e08c1dfd13e437ad6ef5a701c450';
              </script>
            </html>
        """.trimIndent()

        val FTOR_JSON = """
            {
              "default": 360,
              "links": {
                "360": [
                  { "src": "${encodeBase64("https://cdn.example/360.m3u8")}", "type": "application/x-mpegURL" }
                ],
                "720": [
                  { "src": "${encodeBase64("https://cdn.example/720.m3u8")}", "type": "application/x-mpegURL" }
                ]
              }
            }
        """.trimIndent()

        /** Mirrors kodik.js's decodeShiftedBase64 in reverse: base64-encode, then shift every
         * letter back 18 places within its case - matches how Kodik's own "src" fields are shaped. */
        fun encodeBase64(value: String): String {
            val base64 = java.util.Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
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
