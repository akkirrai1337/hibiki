package org.akkirrai.beakokit.extension

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

/**
 * Proves the Rhino-scripted CVH resolver (`cvh.js`) against the same fixture the compiled-in
 * [org.akkirrai.beakokit.playback.extractor.CvhExtractor] was proven against - see
 * [ScriptedKodikResolverTest]'s doc for the migration sequence this is part of.
 *
 * The resolver payload (`cvh.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedCvhResolverTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedResolverFixtures.isAvailable("cvh"), "cvh.js fixture is not present locally")
    }

    private fun installCvhResolver(host: SourceFixtureHost) =
        PlayerResolverExtensionRepository(java.nio.file.Files.createTempDirectory("cvh-resolver").toFile())
            .apply { install(ScriptedResolverFixtures.loadJson("cvh"), originRepositoryUrl = "repo") }
            .loadAll(host.context)
            .single()

    @Test
    fun `extracts cvh stream via playlist and video api`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(
                    path = "/api/v1/player/sv/playlist",
                    query = mapOf("pub" to "745", "id" to "40748", "aggr" to "mali"),
                    body = """
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
                ),
                FixtureRoute(
                    path = "/api/v1/player/sv/video/101",
                    body = """
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
                ),
            ),
        ).use { host ->
            val resolver = installCvhResolver(host)
            val streams = resolver.extractVariants(
                PlayerLink(
                    url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AnilibriaTV&anime_id=40748&episode=1&dubbing=%D0%9E%D0%B7%D0%B2%D1%83%D1%87%D0%BA%D0%B0+AniLibria",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals(7, streams.size)
            assertEquals("https://video.cvh.example.com/manifest.mpd", streams[0].url)
            assertEquals(StreamType.DASH, streams[0].type)
            assertEquals("https://ru.yummyani.me/", streams[0].headers["Referer"])
            assertEquals("https://video.cvh.example.com/master.m3u8", streams[1].url)
            assertEquals(StreamType.HLS, streams[1].type)
            assertEquals("https://video.cvh.example.com/1080.mp4", streams[2].url)
            assertEquals(StreamType.MP4, streams[2].type)
            assertEquals("1080p", streams[2].quality)
            assertEquals("https://video.cvh.example.com/240.mp4", streams[6].url)
            assertEquals("240p", streams[6].quality)
        }
    }
}
