package org.akkirrai.beakokit.extension

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.ScriptedExtensionFixtures
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the Rhino-scripted YummyAnime extension against the same fixtures the old compiled-in
 * YummyAnimeSource used. Like AniLiberty, this port skips the original's cross-provider
 * TitleMatcher re-search for playback since `mediaId` was always just `title.id` unchanged.
 * The application_token is baked into the script (it's a non-secret API-access constant, not a
 * per-user credential), so unlike the old ConfigurableSource-backed test there is nothing to
 * inject via host config here.
 *
 * The extension payload (`yummy-anime.js`) is a gitignored local-only fixture - these tests skip
 * themselves when it's absent instead of failing.
 */
class ScriptedYummyAnimeSourceTest {
    @BeforeTest
    fun requireLocalFixture() {
        assumeTrue(ScriptedExtensionFixtures.isAvailable("yummy-anime"), "yummy-anime.js fixture is not present locally")
    }

    @Test
    fun `source owns identity and capabilities`() {
        SourceFixtureHost(preferredLanguages = listOf(SourceLanguage.ENGLISH)).use { host ->
            val source = ScriptedAnimeSource(host.context, ScriptedExtensionFixtures.load("yummy-anime"))

            SourceTestKit.assertSourceContract(source, SourceId("yummy-anime"))
            assertEquals(
                setOf(
                    SourceCapability.LATEST_RELEASES,
                    SourceCapability.PLAYBACK,
                    SourceCapability.RELATED_TITLES,
                    SourceCapability.SIMILAR_TITLES,
                ),
                source.info.capabilities,
            )
        }
    }

    @Test
    fun `catalog operations satisfy the shared contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(
                    path = "/anime",
                    resource = "beakokit/yummy/catalog-search.json",
                    query = mapOf("limit" to "1", "offset" to "0"),
                ),
                FixtureRoute.fromResource(
                    path = "/anime",
                    resource = "beakokit/yummy/catalog-page-2.json",
                    query = mapOf("limit" to "1", "offset" to "1"),
                ),
                FixtureRoute.fromResource(path = "/anime", resource = "beakokit/yummy/catalog-search.json"),
                FixtureRoute.fromResource(path = "/anime/987654", resource = "beakokit/yummy/catalog-details.json"),
                FixtureRoute.fromResource(path = "/anime/987654/trailers", resource = "beakokit/yummy/empty-response.json"),
                FixtureRoute.fromResource(path = "/anime/987654/recommendations", resource = "beakokit/yummy/empty-response.json"),
                FixtureRoute.fromResource(path = "/anime/schedule", resource = "beakokit/yummy/catalog-latest.json"),
                FixtureRoute.fromResource(path = "/swagger.json", resource = "beakokit/yummy/swagger.json"),
                FixtureRoute.fromResource(path = "/anime/genres", resource = "beakokit/yummy/genres.json"),
            ),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        ).use { host ->
            val source = ScriptedAnimeSource(host.context, ScriptedExtensionFixtures.load("yummy-anime"))

            val catalog = SourceTestKit.assertCatalogContract(source, AnimeSearchRequest(query = "Test", limit = 5))
            val latest = SourceTestKit.assertLatestContract(source, limit = 5)
            val filters = SourceTestKit.assertFilterCatalogContract(source)
            val pagination = SourceTestKit.assertPaginationContract(source, AnimeSearchRequest(limit = 1))
            val filtered = SourceTestKit.assertFilteredSearchContract(
                source,
                AnimeSearchRequest(
                    limit = 1,
                    sort = AnimeSearchSort.RATING,
                    typeAliases = listOf("tv"),
                    statusAliases = listOf("ongoing"),
                    includedGenreAliases = listOf("drama"),
                    excludedGenreAliases = listOf("senen"),
                    yearFrom = 2020,
                    yearTo = 2026,
                ),
            )

            assertEquals("987654", catalog.details.id)
            assertEquals(listOf("987654"), latest.map(AnimeTitle::id))
            assertEquals(listOf("tv"), filters.typeOptions.take(1).map { it.id })
            assertEquals(listOf("987654"), pagination.firstPage.map(AnimeTitle::id))
            assertEquals(listOf("987655"), pagination.secondPage.map(AnimeTitle::id))
            assertEquals(listOf("987654"), filtered.map(AnimeTitle::id))
        }
    }

    @Test
    fun `playback stays behind source contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(path = "/anime/987654/videos", resource = "beakokit/yummy/playback-videos.json"),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
        ).use { host ->
            val source = ScriptedAnimeSource(host.context, ScriptedExtensionFixtures.load("yummy-anime"))
            val title = AnimeTitle(
                id = "987654",
                russianName = "Test",
                englishName = null,
                originalName = "Test",
                japaneseName = null,
                synonyms = emptyList(),
                year = 2026,
                type = "tv",
                episodeCount = 1,
                posterUrl = null,
                status = null,
                description = null,
            )

            val snapshot = SourceTestKit.assertPlaybackContract(source, title)
            val group = snapshot.groups.single()
            val links = snapshot.firstEpisodeLinks

            assertEquals("Voice", group.title)
            assertEquals(listOf("Kodik"), links.map { it.playerName })
            assertTrue(host.requests.all { it.headers["X-Application"] == "wawegr8j13it4rdw" })
        }
    }
}
