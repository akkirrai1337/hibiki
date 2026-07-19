package org.akkirrai.beakokit.source.yummy

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.testkit.FixtureRoute
import org.akkirrai.beakokit.testkit.SourceFixtureHost
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyAnimeSourceTest {
    @Test
    fun `source owns identity and consumes host language and secret`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(
                    path = "/anime",
                    resource = "beakokit/yummy/empty-search.json",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            values = mapOf(YummyAnimeConfig.BASE_URL to "https://yummy.test"),
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to "application-secret"),
        ).use { host ->
            val source = YummyAnimeSource(host.context)

            SourceTestKit.assertSourceContract(source, SourceId("yummy-anime"))
            assertEquals(SourceId("yummy-anime"), source.info.id)
            assertEquals("YummyAnime", source.name)
            assertEquals(SourceCapability.entries.toSet(), source.info.capabilities)
            assertEquals(emptyList(), source.search("frieren"))
            assertEquals("en", host.requests.single().headers["Lang"])
            assertEquals("application-secret", host.requests.single().headers["X-Application"])
        }
    }

    @Test
    fun `catalog operations satisfy the shared contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(
                    path = "/anime",
                    resource = "beakokit/yummy/catalog-search.json",
                ),
                FixtureRoute.fromResource(
                    path = "/anime/987654",
                    resource = "beakokit/yummy/catalog-details.json",
                ),
                FixtureRoute.fromResource(
                    path = "/anime/987654/trailers",
                    resource = "beakokit/yummy/empty-response.json",
                ),
                FixtureRoute.fromResource(
                    path = "/anime/987654/recommendations",
                    resource = "beakokit/yummy/empty-response.json",
                ),
                FixtureRoute.fromResource(
                    path = "/anime/schedule",
                    resource = "beakokit/yummy/catalog-latest.json",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            values = mapOf(YummyAnimeConfig.BASE_URL to "https://yummy.test"),
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to "application-secret"),
        ).use { host ->
            val source = YummyAnimeSource(host.context)

            val catalog = SourceTestKit.assertCatalogContract(
                source,
                AnimeSearchRequest(query = "Test", limit = 5),
            )
            val latest = SourceTestKit.assertLatestContract(source, limit = 5)

            assertEquals("987654", catalog.details.id)
            assertEquals(listOf("987654"), latest.map(AnimeTitle::id))
            assertEquals(5, host.requests.size)
        }
    }

    @Test
    fun `playback stays behind source contract`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute.fromResource(
                    path = "/anime/987654/videos",
                    resource = "beakokit/yummy/playback-videos.json",
                ),
            ),
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
            values = mapOf(YummyAnimeConfig.BASE_URL to "https://yummy.test"),
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to "application-secret"),
        ).use { host ->
            val source = YummyAnimeSource(host.context)
            val title = title("987654")

            val snapshot = SourceTestKit.assertPlaybackContract(source, title)
            val group = snapshot.groups.single()
            val links = snapshot.firstEpisodeLinks

            assertEquals("Voice", group.title)
            assertEquals(listOf("Kodik"), links.map { it.playerName })
            assertEquals("application-secret", host.requests.single().headers["X-Application"])
        }
    }

    private fun title(id: String) = AnimeTitle(
        id = id,
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
}
