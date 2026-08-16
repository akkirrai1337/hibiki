package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.playback.ResolvedPlaybackStream
import org.akkirrai.hibiki.shared.player.model.PlaybackSegmentType
import org.akkirrai.hibiki.shared.player.model.PlaybackStreamType

class ResolvedPlaybackMapperTest {
    @Test
    fun preservesValidatedUrlHeadersQualityAndApiSegments() {
        val resolved = ResolvedPlaybackStream(
            link = PlayerLink(
                url = "https://provider.example/embed/1",
                type = PlayerType.EMBED,
                quality = "720p",
                headers = mapOf("Referer" to "https://provider.example"),
                videoId = 42L,
                segments = listOf(VideoSegment(VideoSegmentType.OPENING, 1_000L, 2_000L)),
            ),
            stream = VideoStream(
                url = "https://cdn.example/video.m3u8",
                type = StreamType.HLS,
                quality = "1080p",
                headers = emptyMap(),
            ),
            validation = StreamValidationResult(
                success = true,
                streamType = StreamType.HLS,
                quality = "1080p",
                finalUrl = "https://cdn.example/media.m3u8",
                statusCode = 200,
                message = "ok",
            ),
            availableQualityLabels = listOf("1080p", "720p"),
        )

        val stream = resolved.toPlaybackStream("Anime", "Source", "Episode 1")

        assertEquals("https://cdn.example/media.m3u8", stream.streamUrl)
        assertEquals(PlaybackStreamType.HLS, stream.streamType)
        assertEquals("1080p", stream.qualityLabel)
        assertEquals(mapOf("Referer" to "https://provider.example"), stream.headers)
        assertEquals(listOf("1080p", "720p"), stream.availableQualityLabels)
        assertEquals(42L, stream.videoId)
        assertEquals(1_000L, stream.segments.single().startMs)
        assertTrue(stream.segments.single().type == PlaybackSegmentType.Opening)
    }
}
