package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackStreamType

class PlaybackSessionKeyTest {
    @Test
    fun sameTransportProducesSameSessionKey() {
        val first = stream(url = "https://cdn.example/episode.m3u8", headers = mapOf("Referer" to "https://example"))
        val second = first.copy(animeTitle = "Updated title", qualityLabel = "1080p")

        assertEquals(first.sessionKey(), second.sessionKey())
    }

    @Test
    fun changedUrlOrHeadersProducesNewSessionKey() {
        val first = stream(url = "https://cdn.example/episode.m3u8", headers = mapOf("Token" to "one"))

        assertNotEquals(first.sessionKey(), first.copy(streamUrl = "https://cdn.example/episode-2.m3u8").sessionKey())
        assertNotEquals(first.sessionKey(), first.copy(headers = mapOf("Token" to "two")).sessionKey())
    }

    private fun stream(url: String, headers: Map<String, String>) = PlaybackStream(
        animeTitle = "Anime",
        sourceTitle = "Source",
        episodeTitle = "Episode 1",
        streamUrl = url,
        streamType = PlaybackStreamType.HLS,
        headers = headers,
    )
}
