package org.akkirrai.beakokit.playback.extractor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectExtractorsTest {
    @Test
    fun `direct extractors preserve source-owned audio and subtitles`() = runBlocking {
        val subtitle = SubtitleTrack(
            url = "https://media.test/subtitles/en.vtt",
            label = "English",
            language = "en",
            headers = mapOf("Referer" to "https://media.test/"),
        )
        val link = PlayerLink(
            url = "https://media.test/video.m3u8",
            type = PlayerType.DIRECT_HLS,
            quality = "1080p",
            headers = mapOf("Authorization" to "video-token"),
            audioUrl = "https://media.test/audio/en.m3u8",
            audioHeaders = mapOf("Authorization" to "audio-token"),
            subtitles = listOf(subtitle),
        )

        val stream = DirectHlsExtractor().extract(link)

        assertEquals(link.audioUrl, stream.audioUrl)
        assertEquals(link.audioHeaders, stream.audioHeaders)
        assertEquals(listOf(subtitle), stream.subtitles)
    }

    @Test
    fun `older player link json keeps empty auxiliary tracks`() {
        val link = Json.decodeFromString<PlayerLink>(
            """{"url":"https://media.test/video.mp4","type":"DIRECT_MP4","quality":null}""",
        )

        assertNull(link.audioUrl)
        assertTrue(link.audioHeaders.isEmpty())
        assertTrue(link.subtitles.isEmpty())
    }
}
