package org.akkirrai.beakokit.extension

import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.ScriptedResolverFixtures
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the Rhino-scripted AniBoom resolver (`aniboom.js`) against the same fixture the
 * compiled-in [org.akkirrai.beakokit.playback.extractor.AniBoomExtractor] was proven against - see
 * [ScriptedKodikResolverTest]'s doc for the migration sequence this is part of.
 *
 * The resolver payload (`aniboom.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedAniBoomResolverTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedResolverFixtures.isAvailable("aniboom"), "aniboom.js fixture is not present locally")
    }

    private fun installAniBoomResolver(host: SourceFixtureHost) =
        PlayerResolverExtensionRepository(java.nio.file.Files.createTempDirectory("aniboom-resolver").toFile())
            .apply { install(ScriptedResolverFixtures.loadJson("aniboom"), originRepositoryUrl = "repo") }
            .loadAll(host.context)
            .single()

    @Test
    fun `extracts escaped hls url and quality from embed`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(path = "/embed/example", body = PAGE_HTML, contentType = ContentType.Text.Html),
            ),
        ).use { host ->
            val resolver = installAniBoomResolver(host)
            val stream = resolver.extract(
                PlayerLink(
                    url = "https://aniboom.one/embed/example",
                    type = PlayerType.EMBED,
                    quality = null,
                ),
            )

            assertEquals("https://video.example/anime/master.m3u8", stream.url)
            assertEquals("1080p", stream.quality)
        }
    }

    private companion object {
        val PAGE_HTML = """
            <div data-config="{&quot;hls&quot;:&quot;{\&quot;src\&quot;:
            \&quot;https:\\\/\\\/video.example\\\/anime\\\/master.m3u8\&quot;}&quot;,
            &quot;qualityVideo&quot;:1080}"></div>
        """.trimIndent()
    }
}
