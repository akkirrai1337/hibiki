package org.akkirrai.beakokit.source.aniliberty

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceFeature
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AniLibertySourceTest {
    @Test
    fun `source owns its stable identity and capabilities`() {
        val client = HttpClient(MockEngine { error("Network must not be called") })

        try {
            val source = AniLibertySource(
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                ),
            )

            SourceTestKit.assertSourceContract(source, SourceId("ani-liberty"))
            assertEquals(SourceId("ani-liberty"), source.info.id)
            assertEquals("AniLiberty", source.name)
            assertEquals(setOf(SourceLanguage.RUSSIAN), source.info.languages)
            assertEquals("https://anilibria.top", source.info.website)
            assertEquals(
                setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
                source.info.capabilities,
            )
            assertTrue(MetadataSourceFeature.LATEST_RELEASES in source.capabilities.features)
            assertTrue(MetadataSourceFeature.SCHEDULE in source.capabilities.features)
        } finally {
            client.close()
        }
    }

    @Test
    fun `playback stays behind source contract`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/app/search/releases" -> respond(
                    """[{"id":987654,"name":{"main":"Test"},"year":2026,"episodes_total":1}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/anime/releases/987654" -> respond(
                    """{"id":987654,"episodes":[{"id":"episode-1","ordinal":1,"hls_720":"https://cdn.test/720.m3u8"}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected URL: ${request.url}")
            }
        }) {
            install(ContentNegotiation) { json() }
        }

        try {
            val source = AniLibertySource(
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                    config = MapSourceConfig(
                        values = mapOf(
                            AniLibertySource.BASE_URLS_KEY to "https://aniliberty.test/api/v1",
                        ),
                    ),
                ),
            )
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
        } finally {
            client.close()
        }
    }
}
