package org.akkirrai.beakokit.source.aniliberty.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.VideoSegmentType
import kotlin.test.Test
import kotlin.test.assertEquals

class AniLibertyPlaybackClientTest {
    @Test
    fun `uses mirror and exposes every available hls quality`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "primary.test") {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else when (request.url.encodedPath) {
                "/api/v1/app/search/releases" -> respond(SEARCH, headers = headersOf("Content-Type", "application/json"))
                "/api/v1/anime/releases/42" -> respond(RELEASE, headers = headersOf("Content-Type", "application/json"))
                else -> error("Unexpected URL: ${request.url}")
            }
        }) { install(ContentNegotiation) { json() } }
        val provider = AniLibertyPlaybackClient(
            client = client,
            matcher = TitleMatcher(),
            baseUrls = listOf("https://primary.test/api/v1", "https://mirror.test/api/v1"),
        )
        val title = AnimeTitle("title", "Example", null, "Example", null, emptyList(), 2024, "tv", 1, null, null, null)

        val match = provider.search(title).single()
        val episode = provider.getEpisodes(match).single()
        val links = provider.getPlayerLinks(match, episode)

        assertEquals("42", match.mediaId)
        assertEquals(listOf("1080p", "720p", "360p"), links.map { it.quality })
        assertEquals("https://cdn.test/master.m3u8", links.first().url)
        assertEquals("AniLiberty", links.first().playerName)
        assertEquals("AniLiberty", links.first().translation)
        assertEquals(
            listOf(VideoSegmentType.OPENING, VideoSegmentType.ENDING),
            links.first().segments.map { it.type },
        )
        assertEquals(90_000L, links.first().segments.first().startMs)
        assertEquals(180_000L, links.first().segments.first().endMs)
        assertEquals(1_400_000L, links.first().segments.last().endMs)
        client.close()
    }

    private companion object {
        const val SEARCH = """[{"id":42,"name":{"main":"Example"},"year":2024,"episodes_total":1}]"""
        const val RELEASE = """{"id":42,"episodes":[{"id":"episode-1","ordinal":1,"name":"Episode 1","duration":1400,"opening":{"start":90,"stop":180},"ending":{"start":1300,"stop":9000},"hls_1080":"https://cdn.test/master.m3u8","hls_720":"//cdn.test/720.m3u8","hls_360":"cdn.test/360.m3u8"}]}"""
    }
}
