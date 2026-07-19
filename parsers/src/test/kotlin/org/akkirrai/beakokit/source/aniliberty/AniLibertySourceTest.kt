package org.akkirrai.beakokit.source.aniliberty

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.testkit.JsonFixtureRoute
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
    fun `playback stays behind source contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                JsonFixtureRoute.fromResource(
                    path = "/api/v1/app/search/releases",
                    resource = "beakokit/aniliberty/search-releases.json",
                ),
                JsonFixtureRoute.fromResource(
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
