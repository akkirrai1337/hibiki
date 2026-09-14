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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves the Rhino-scripted Aksor resolver (`aksor.js`) against the same fixtures the compiled-in
 * [org.akkirrai.beakokit.playback.extractor.AksorExtractor] was proven against - see
 * [ScriptedKodikResolverTest]'s doc for the migration sequence this is part of.
 *
 * The resolver payload (`aksor.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedAksorResolverTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedResolverFixtures.isAvailable("aksor"), "aksor.js fixture is not present locally")
    }

    private fun installAksorResolver(host: SourceFixtureHost) =
        PlayerResolverExtensionRepository(java.nio.file.Files.createTempDirectory("aksor-resolver").toFile())
            .apply { install(ScriptedResolverFixtures.loadJson("aksor"), originRepositoryUrl = "repo") }
            .loadAll(host.context)
            .single()

    @Test
    fun `selects highest available dash quality`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(
                    path = "/api/video/test-id",
                    body = """
                        {
                          "qualities": {
                            "q480": "https://cdn.example/480.mpd",
                            "q1080": "https://cdn.example/1080.mpd",
                            "q4k": null
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        ).use { host ->
            val resolver = installAksorResolver(host)
            val stream = resolver.extract(
                PlayerLink(
                    url = "https://player.aksor.tv/video/test-id",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals("https://cdn.example/1080.mpd", stream.url)
            assertEquals(StreamType.DASH, stream.type)
            assertEquals("1080p", stream.quality)
            assertEquals("https://player.aksor.tv/video/test-id", host.requests.single().headers["Referer"])
        }
    }

    @Test
    fun `returns every available quality from aksor api`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(
                    path = "/api/video/test-id",
                    body = """
                        {
                          "qualities": {
                            "q360": "https://cdn.example/360.m3u8",
                            "q1080": "https://cdn.example/1080.mpd",
                            "q4k": "https://cdn.example/2160.mpd"
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        ).use { host ->
            val resolver = installAksorResolver(host)
            val streams = resolver.extractVariants(
                PlayerLink(
                    url = "https://player.aksor.tv/video/test-id",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals(listOf("2160p", "1080p", "360p"), streams.map { it.quality })
            assertEquals(listOf(StreamType.DASH, StreamType.DASH, StreamType.HLS), streams.map { it.type })
        }
    }

    @Test
    fun `falls back to meta video url when api returns no qualities`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(path = "/api/video/test-id", body = """{ "qualities": {} }"""),
                FixtureRoute(
                    path = "/video/test-id",
                    body = """
                        <html>
                          <head>
                            <meta name="video_url" content="https://cdn.example/fallback-720.m3u8">
                          </head>
                        </html>
                    """.trimIndent(),
                    contentType = ContentType.Text.Html,
                ),
            ),
        ).use { host ->
            val resolver = installAksorResolver(host)
            val stream = resolver.extract(
                PlayerLink(
                    url = "https://player.aksor.tv/video/test-id",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals("https://cdn.example/fallback-720.m3u8", stream.url)
            assertEquals(StreamType.HLS, stream.type)
            assertNotNull(stream.headers["User-Agent"])
        }
    }
}
