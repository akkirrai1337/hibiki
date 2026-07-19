package org.akkirrai.beakokit.source.aniliberty

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AniLibertySourceTest {
    @Test
    fun `source owns its stable identity and capabilities`() {
        SourceFixtureHost(preferredLanguages = listOf(SourceLanguage.RUSSIAN)).use { host ->
            val source = AniLibertySource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("ani-liberty"))
            assertEquals(SourceId("ani-liberty"), source.info.id)
            assertEquals("AniLiberty", source.name)
            assertEquals(setOf(SourceLanguage.RUSSIAN), source.info.languages)
            assertEquals("https://anilibria.top", source.info.website)
            assertEquals(
                setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
                source.info.capabilities,
            )
            assertTrue(CatalogFeature.LATEST_RELEASES in source.catalogCapabilities.features)
            assertTrue(CatalogFeature.SCHEDULE in source.catalogCapabilities.features)
        }
    }

    @Test
    fun `catalog operations satisfy the shared contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(
                    path = "/api/v1/anime/catalog/releases",
                    resource = "beakokit/aniliberty/catalog-search.json",
                ),
                FixtureRoute.fromResource(
                    path = "/api/v1/anime/releases/987654",
                    resource = "beakokit/aniliberty/catalog-details.json",
                ),
                FixtureRoute.fromResource(
                    path = "/api/v1/anime/releases/latest",
                    resource = "beakokit/aniliberty/catalog-search.json",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(AniLibertySource.BASE_URLS_KEY to "https://aniliberty.test/api/v1"),
        ).use { host ->
            val source = AniLibertySource(host.context)

            val catalog = SourceTestKit.assertCatalogContract(
                source,
                AnimeSearchRequest(query = "Test", limit = 5),
            )
            val latest = SourceTestKit.assertLatestContract(source, limit = 5)

            assertEquals("987654", catalog.details.id)
            assertEquals(listOf("987654"), latest.map(AnimeTitle::id))
        }
    }

    @Test
    fun `playback stays behind source contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(
                    path = "/api/v1/app/search/releases",
                    resource = "beakokit/aniliberty/search-releases.json",
                ),
                FixtureRoute.fromResource(
                    path = "/api/v1/anime/releases/987654",
                    resource = "beakokit/aniliberty/release-playback.json",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(AniLibertySource.BASE_URLS_KEY to "https://aniliberty.test/api/v1"),
        ).use { host ->
            val source = AniLibertySource(host.context)
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

            assertEquals("AniLiberty", group.title)
            assertEquals("HLS", group.qualityLabel)
            assertEquals(listOf("720p"), links.map { it.quality })
        }
    }
}
