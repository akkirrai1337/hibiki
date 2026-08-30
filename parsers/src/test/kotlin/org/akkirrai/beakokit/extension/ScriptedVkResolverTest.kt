package org.akkirrai.beakokit.extension

import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.ScriptedResolverFixtures
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the Rhino-scripted VK resolver (`vk.js`) against the same fixtures the compiled-in
 * [org.akkirrai.beakokit.playback.extractor.VkExtractor] was proven against - step 2 of the player
 * resolver migration plan (Kodik was step 1, see ScriptedKodikResolverTest).
 *
 * The resolver payload (`vk.js`) is a gitignored local-only fixture - these tests skip themselves
 * when it's absent instead of failing.
 */
class ScriptedVkResolverTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedResolverFixtures.isAvailable("vk"), "vk.js fixture is not present locally")
    }

    private fun installVkResolver(host: SourceFixtureHost) =
        PlayerResolverExtensionRepository(Files.createTempDirectory("vk-resolver").toFile())
            .apply { install(ScriptedResolverFixtures.loadJson("vk")) }
            .loadAll(host.context)
            .single()

    @Test
    fun `extracts hls dash and mp4 variants from vk embed`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute(
                    "/video_ext.php",
                    """
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
                    query = mapOf("oid" to "-225898691", "id" to "456239021", "js_api" to "1", "partner_name" to "viqeo"),
                ),
            ),
        ).use { host ->
            val resolver = installVkResolver(host)
            val streams = resolver.extractVariants(
                PlayerLink(
                    url = "https://ru.yummyani.me/iframeVK.html?id=-225898691_456239021",
                    type = PlayerType.EMBED,
                    quality = null,
                    headers = mapOf("Referer" to "https://ru.yummyani.me/"),
                ),
            )

            // The compiled-in extractor still handles this yummyani.me/iframeVK.html case (see
            // vk.js's header comment: the manifest schema only matches by host, so this JS
            // resolver isn't wired up for it yet) - this test drives the resolver directly to
            // prove resolveEmbedUrl's own logic is faithful, independent of routing.
            assertEquals(5, streams.size)
            assertEquals("https://cdn.example.com/master-fmp4.m3u8", streams[0].url)
            assertEquals(StreamType.HLS, streams[0].type)
            assertEquals("https://ru.yummyani.me/", streams[0].headers["Referer"])
            assertEquals("https://cdn.example.com/master.m3u8", streams[1].url)
            assertEquals(StreamType.HLS, streams[1].type)
            assertEquals("https://cdn.example.com/manifest.mpd", streams[2].url)
            assertEquals(StreamType.DASH, streams[2].type)
            assertEquals("https://cdn.example.com/1080.mp4", streams[3].url)
            assertEquals("1080p", streams[3].quality)
            assertEquals("https://cdn.example.com/360.mp4", streams[4].url)
            assertEquals("360p", streams[4].quality)
        }
    }

    @Test
    fun `falls back to html parsing when api prefetch cache is absent`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute(
                    "/video_ext.php",
                    """
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
                ),
            ),
        ).use { host ->
            val resolver = installVkResolver(host)
            val streams = resolver.extractVariants(
                PlayerLink(
                    url = "https://ru.yummyani.me/iframeVK.html?id=-225898691_456239021",
                    type = PlayerType.EMBED,
                    quality = null,
                    headers = mapOf("Referer" to "https://ru.yummyani.me/"),
                ),
            )

            assertEquals(3, streams.size)
            assertEquals("https://cdn.example.com/master.m3u8", streams[0].url)
            assertEquals(StreamType.HLS, streams[0].type)
            assertEquals("https://cdn.example.com/720.mp4", streams[1].url)
            assertEquals("720p", streams[1].quality)
            assertEquals("https://cdn.example.com/360.mp4", streams[2].url)
        }
    }

    @Test
    fun `parses files block and data source variants from html`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute(
                    "/video_ext.php",
                    """
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
                ),
            ),
        ).use { host ->
            val resolver = installVkResolver(host)
            val streams = resolver.extractVariants(
                PlayerLink(
                    url = "https://ru.yummyani.me/iframeVK.html?id=-225898691_456239021",
                    type = PlayerType.EMBED,
                    quality = null,
                    headers = mapOf("Referer" to "https://ru.yummyani.me/"),
                ),
            )

            assertEquals(listOf(StreamType.HLS, StreamType.MP4, StreamType.MP4, StreamType.MP4), streams.map { it.type })
            assertEquals(listOf(null, "1080p", "480p", "360p"), streams.map { it.quality })
            assertEquals("https://cdn.example.com/480.mp4", streams[2].url)
        }
    }

    private fun htmlRoute(path: String, body: String, query: Map<String, String> = emptyMap()) =
        FixtureRoute(path = path, body = body, query = query, contentType = ContentType.Text.Html)
}
