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
import kotlin.test.assertTrue

/**
 * Proves the Rhino-scripted Sibnet resolver (`sibnet.js`) against the same fixture the compiled-in
 * [org.akkirrai.beakokit.playback.extractor.SibnetExtractor] was proven against - see
 * [ScriptedKodikResolverTest]'s doc for the migration sequence this is part of.
 *
 * The resolver payload (`sibnet.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedSibnetResolverTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedResolverFixtures.isAvailable("sibnet"), "sibnet.js fixture is not present locally")
    }

    private fun installSibnetResolver(host: SourceFixtureHost) =
        PlayerResolverExtensionRepository(java.nio.file.Files.createTempDirectory("sibnet-resolver").toFile())
            .apply { install(ScriptedResolverFixtures.loadJson("sibnet"), originRepositoryUrl = "repo") }
            .loadAll(host.context)
            .single()

    @Test
    fun `extracts direct mp4 source from sibnet embed`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(path = "/shell.php", body = PAGE_HTML, contentType = ContentType.Text.Html, query = mapOf("videoid" to "5560903")),
            ),
        ).use { host ->
            val resolver = installSibnetResolver(host)
            val stream = resolver.extract(
                PlayerLink(
                    url = "https://video.sibnet.ru/shell.php?videoid=5560903",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals("https://video.sibnet.ru/v/50adb8b798cbf55d7c999be4e4326c9b/5560903.mp4", stream.url)
            assertEquals(StreamType.MP4, stream.type)
            assertEquals("https://video.sibnet.ru/video5560903-_Kondrateandr_RU_SUB__Noch_mechtyi/", stream.headers["Referer"])
            assertEquals("https://video.sibnet.ru", stream.headers["Origin"])
            assertTrue(stream.headers["User-Agent"]?.contains("Mozilla/5.0") == true)
        }
    }

    private companion object {
        val PAGE_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
            <link rel="canonical" href="https://video.sibnet.ru/video5560903-_Kondrateandr_RU_SUB__Noch_mechtyi/"/>
            </head>
            <body>
            <script type="text/javascript">
            player.src([
                {src: "/v/50adb8b798cbf55d7c999be4e4326c9b/5560903.mp4", type: "video/mp4"},
            ]);
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
