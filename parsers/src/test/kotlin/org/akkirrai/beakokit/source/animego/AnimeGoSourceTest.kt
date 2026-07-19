package org.akkirrai.beakokit.source.animego

import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimeGoSourceTest {
    @Test
    fun `scraped catalog and playback satisfy shared contracts`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                htmlRoute("/search/all", "search.html", query = mapOf("q" to "Test")),
                htmlRoute("/anime/test-anime-710", "details.html"),
                htmlRoute("/anime", "search.html"),
                FixtureRoute.fromResource(
                    path = "/player/710",
                    resource = "beakokit/animego/episodes.json",
                ),
                FixtureRoute.fromResource(
                    path = "/player/videos/10934",
                    resource = "beakokit/animego/players.json",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(AnimeGoConfig.BASE_URL to "https://animego.test"),
        ).use { host ->
            val source = AnimeGoSource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("animego"))
            val catalog = SourceTestKit.assertCatalogContract(
                source,
                AnimeSearchRequest(query = "Test", limit = 5),
            )
            val latest = SourceTestKit.assertLatestContract(source, limit = 5)
            val playback = SourceTestKit.assertPlaybackContract(source, catalog.details)

            assertEquals("test-anime-710", catalog.details.id)
            assertEquals("Test Anime", catalog.details.originalName)
            assertEquals(3, catalog.details.availableEpisodeCount)
            assertEquals(listOf("test-anime-710"), latest.map(AnimeTitle::id))
            assertEquals("AniBoom", playback.firstEpisodeLinks.first().playerName)
            assertEquals(
                setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
                source.info.capabilities,
            )
            assertTrue(
                host.requests.filter { it.url.encodedPath.startsWith("/player") }
                    .all { it.headers["X-Requested-With"] == "XMLHttpRequest" },
            )
        }
    }

    private fun htmlRoute(
        path: String,
        resource: String,
        query: Map<String, String> = emptyMap(),
    ) = FixtureRoute.fromResource(
        path = path,
        resource = "beakokit/animego/$resource",
        query = query,
        contentType = ContentType.Text.Html,
    )
}
