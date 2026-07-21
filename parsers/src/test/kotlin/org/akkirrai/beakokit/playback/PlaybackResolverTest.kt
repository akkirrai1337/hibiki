package org.akkirrai.beakokit.playback

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceUnavailableException
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.StreamValidator
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackResolverTest {
    @Test
    fun `falls back when the first extractor fails`() = runBlocking {
        val resolver = PlaybackResolver(
            extractors = listOf(
                extractor("broken") { error("embed changed") },
                extractor("working") { stream("https://video.test/working.m3u8") },
            ),
            validator = successfulValidator,
        )

        val resolved = resolver.resolve(listOf(link("broken"), link("working")))

        assertEquals("working", resolved.link.playerName)
        assertEquals("https://video.test/working.m3u8", resolved.validation.finalUrl)
    }

    @Test
    fun `reports a blocked URL when every supported player is forbidden`() = runBlocking {
        val resolver = PlaybackResolver(
            extractors = listOf(extractor("blocked") { stream("https://video.test/blocked.m3u8") }),
            validator = object : StreamValidator {
                override suspend fun validate(stream: VideoStream) =
                    StreamValidationResult(false, stream.type, stream.quality, stream.url, 403, "Forbidden")
            },
        )

        val error = assertFailsWith<BlockedPlaybackUrlException> {
            resolver.resolve(listOf(link("blocked")))
        }

        assertEquals(403, error.statusCode)
    }

    @Test
    fun `preserves source unavailability when every player source is unavailable`() = runBlocking {
        val resolver = PlaybackResolver(
            extractors = listOf(extractor("offline") { throw SourceUnavailableException("mirror offline") }),
            validator = successfulValidator,
        )

        assertFailsWith<SourceUnavailableException> {
            resolver.resolve(listOf(link("offline")))
        }
    }

    @Test
    fun `reports missing player links separately from extractor failures`() = runBlocking {
        val resolver = PlaybackResolver(emptyList(), successfulValidator)

        assertFailsWith<NoPlayerLinksException> { resolver.resolve(emptyList()) }
        assertFailsWith<NoSupportedExtractorException> { resolver.resolve(listOf(link("unsupported"))) }
    }

    private fun extractor(playerName: String, block: suspend (PlayerLink) -> VideoStream) = object : StreamExtractor {
        override fun supports(link: PlayerLink): Boolean = link.playerName == playerName
        override suspend fun extract(link: PlayerLink): VideoStream = block(link)
    }

    private fun link(playerName: String) = PlayerLink(
        url = "https://player.test/$playerName",
        type = PlayerType.EMBED,
        quality = "720p",
        playerName = playerName,
    )

    private fun stream(url: String) = VideoStream(url, StreamType.HLS, "720p")

    private val successfulValidator = object : StreamValidator {
        override suspend fun validate(stream: VideoStream) =
            StreamValidationResult(true, stream.type, stream.quality, stream.url, 200, "OK")
    }
}
