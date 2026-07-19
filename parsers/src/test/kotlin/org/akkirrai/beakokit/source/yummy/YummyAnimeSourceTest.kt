package org.akkirrai.beakokit.source.yummy

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyAnimeSourceTest {
    @Test
    fun `source owns identity and consumes host language and secret`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/anime", request.url.encodedPath)
            assertEquals("en", request.headers["Lang"])
            assertEquals("application-secret", request.headers["X-Application"])
            respond(
                content = """{"response":[]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json() }
        }

        try {
            val source = YummyAnimeSource(
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                    config = MapSourceConfig(
                        values = mapOf(YummyAnimeSource.BASE_URL_KEY to "https://yummy.test"),
                        secrets = mapOf(
                            YummyAnimeSource.APPLICATION_TOKEN_KEY to "application-secret",
                        ),
                    ),
                ),
            )

            assertEquals(SourceId("yummy-anime"), source.info.id)
            assertEquals("YummyAnime", source.name)
            assertEquals(SourceCapability.entries.toSet(), source.info.capabilities)
            assertEquals(emptyList(), source.search("frieren"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `playback stays behind source contract`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("application-secret", request.headers["X-Application"])
            when (request.url.encodedPath) {
                "/anime/987654/videos" -> respond(
                    content = """
                        {"response":[{
                          "video_id":1,
                          "iframe_url":"https://player.test/episode-1",
                          "data":{"dubbing":"Voice","player":"Плеер Kodik","player_id":3},
                          "number":"1"
                        }]}
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected URL: ${request.url}")
            }
        }) {
            install(ContentNegotiation) { json() }
        }

        try {
            val source = YummyAnimeSource(
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                    config = MapSourceConfig(
                        values = mapOf(YummyAnimeSource.BASE_URL_KEY to "https://yummy.test"),
                        secrets = mapOf(YummyAnimeSource.APPLICATION_TOKEN_KEY to "application-secret"),
                    ),
                ),
            )
            val title = title("987654")

            val group = source.getPlaybackGroups(title).single()
            val links = source.getPlayerLinks(title, group, group.episodes.single())

            assertEquals("Voice", group.title)
            assertEquals(listOf("Kodik"), links.map { it.playerName })
        } finally {
            client.close()
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
