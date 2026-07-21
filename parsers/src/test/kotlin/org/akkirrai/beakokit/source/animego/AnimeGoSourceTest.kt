package org.akkirrai.beakokit.source.animego

import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
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
                htmlRoute("/search/all", "empty-search.html", query = mapOf("q" to "Missing")),
                htmlRoute("/anime/test-anime-710", "details.html"),
                htmlRoute("/anime", "search.html"),
                FixtureRoute.fromResource(
                    path = "/anime/2",
                    resource = "beakokit/animego/catalog-page.json",
                ),
                FixtureRoute.fromResource(
                    path = "/anime/filter/year-from-2020-to-2024/genres-is-action-or-!comedy/type-is-tv/status-is-ongoing/apply",
                    resource = "beakokit/animego/catalog-page.json",
                ),
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
            SourceTestKit.assertTitleMetadataContract(
                catalog.details,
                requireDescription = true,
                requirePoster = true,
            )
            val latest = SourceTestKit.assertLatestContract(source, limit = 5)
            val playback = SourceTestKit.assertPlaybackContract(source, catalog.details)
            val filters = SourceTestKit.assertFilterCatalogContract(source)
            val paged = source.search(
                AnimeSearchRequest(offset = 20, limit = 1, sort = AnimeSearchSort.RATING),
            )
            val pagination = SourceTestKit.assertPaginationContract(
                source,
                AnimeSearchRequest(limit = 1),
            )
            val filtered = SourceTestKit.assertFilteredSearchContract(
                source,
                AnimeSearchRequest(
                    limit = 1,
                    sort = AnimeSearchSort.YEAR,
                    typeAliases = listOf("tv"),
                    statusAliases = listOf("ongoing"),
                    includedGenreAliases = listOf("action"),
                    excludedGenreAliases = listOf("comedy"),
                    yearFrom = 2020,
                    yearTo = 2024,
                ),
            )
            SourceTestKit.assertEmptySearchContract(
                source,
                AnimeSearchRequest(query = "Missing", limit = 1),
            )

            assertEquals("test-anime-710", catalog.details.id)
            assertEquals("Test Anime", catalog.details.originalName)
            assertEquals("Подробное описание тестового аниме.", catalog.searchResults.single().description)
            assertTrue(catalog.details.posterUrl?.startsWith("https://images.weserv.nl/") == true)
            assertTrue(catalog.details.posterFallbackUrl?.startsWith("https://img.cdngos.com/") == true)
            assertEquals(3, catalog.details.availableEpisodeCount)
            assertEquals(listOf("test-anime-710"), latest.map(AnimeTitle::id))
            assertEquals("AniBoom", playback.firstEpisodeLinks.first().playerName)
            assertEquals(listOf("tv"), filters.typeOptions.map { it.id })
            assertEquals(listOf("ongoing"), filters.statusOptions.map { it.id })
            assertEquals(listOf("action", "comedy"), filters.genreOptions.map { it.id })
            assertEquals(listOf("paged-anime-711"), paged.map(AnimeTitle::id))
            assertEquals("Paged catalog description.", paged.single().description)
            assertEquals(listOf("test-anime-710"), pagination.firstPage.map(AnimeTitle::id))
            assertEquals(listOf("paged-anime-711"), pagination.secondPage.map(AnimeTitle::id))
            assertEquals(listOf("paged-anime-711"), filtered.map(AnimeTitle::id))
            assertEquals(
                setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
                source.info.capabilities,
            )
            assertTrue(
                host.requests.filter { it.url.encodedPath.startsWith("/player") }
                    .all { it.headers["X-Requested-With"] == "XMLHttpRequest" },
            )
            assertTrue(
                host.requests.filter {
                    it.url.encodedPath == "/anime/2" || it.url.encodedPath.startsWith("/anime/filter/")
                }.all { request ->
                    request.headers["X-Requested-With"] == "XMLHttpRequest" &&
                        request.url.parameters["entities"] == "true"
                },
            )
            assertTrue(
                host.requests.any { request ->
                    request.url.encodedPath == "/anime/2" &&
                        request.url.parameters["sort"] == "rating" &&
                        request.url.parameters["direction"] == "desc"
                },
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
