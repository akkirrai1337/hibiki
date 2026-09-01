package org.akkirrai.beakokit.playback

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.FallbackStreamExtractor
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
import kotlin.test.assertTrue

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
    fun `tries a fallback extractor for the same link after direct validation fails`() = runBlocking {
        val calls = mutableListOf<String>()
        val direct = object : StreamExtractor {
            override fun supports(link: PlayerLink) = true
            override suspend fun extract(link: PlayerLink): VideoStream {
                calls += "direct"
                return stream("https://video.test/direct.m3u8")
            }
        }
        val fallback = object : FallbackStreamExtractor {
            override fun supports(link: PlayerLink) = true
            override fun shouldAttempt(link: PlayerLink, validationFailures: List<StreamValidationResult>): Boolean {
                assertEquals(403, validationFailures.single().statusCode)
                return true
            }
            override suspend fun extract(link: PlayerLink): VideoStream {
                calls += "fallback"
                return stream("http://127.0.0.1/relay.m3u8")
            }
        }
        val validator = object : StreamValidator {
            override suspend fun validate(stream: VideoStream) = StreamValidationResult(
                success = stream.url.startsWith("http://127.0.0.1"),
                streamType = stream.type,
                quality = stream.quality,
                finalUrl = stream.url,
                statusCode = if (stream.url.startsWith("http://127.0.0.1")) 200 else 403,
                message = "test",
            )
        }

        val resolved = PlaybackResolver(listOf(direct, fallback), validator).resolve(listOf(link("direct")))

        assertEquals(listOf("direct", "fallback"), calls)
        assertTrue(resolved.validation.success)
    }

    @Test
    fun `does not start a declined fallback extractor`() = runBlocking {
        var fallbackStarted = false
        val direct = object : StreamExtractor {
            override fun supports(link: PlayerLink) = true
            override suspend fun extract(link: PlayerLink) = stream("https://video.test/missing.m3u8")
        }
        val fallback = object : FallbackStreamExtractor {
            override fun supports(link: PlayerLink) = true
            override fun shouldAttempt(link: PlayerLink, validationFailures: List<StreamValidationResult>) = false
            override suspend fun extract(link: PlayerLink): VideoStream {
                fallbackStarted = true
                return stream("http://127.0.0.1/relay.m3u8")
            }
        }
        val validator = object : StreamValidator {
            override suspend fun validate(stream: VideoStream) =
                StreamValidationResult(false, stream.type, stream.quality, stream.url, 404, "Not found")
        }

        assertFailsWith<ExtractorFailedException> {
            PlaybackResolver(listOf(direct, fallback), validator).resolve(listOf(link("direct")))
        }
        assertEquals(false, fallbackStarted)
    }

    @Test
    fun `selects relayed variant when direct auxiliary audio fails validation`() = runBlocking {
        val extractor = object : StreamExtractor {
            override fun supports(link: PlayerLink) = true
            override suspend fun extract(link: PlayerLink) = error("Variants are used")
            override suspend fun extractVariants(link: PlayerLink) = listOf(
                stream("https://video.test/direct.m3u8").copy(
                    audioUrl = "https://audio.test/direct.m3u8",
                ),
                stream("http://127.0.0.1/relay/video.m3u8").copy(
                    audioUrl = "http://127.0.0.1/relay/audio.m3u8",
                ),
            )
        }
        val validator = object : StreamValidator {
            override suspend fun validate(stream: VideoStream) = StreamValidationResult(
                success = stream.audioUrl?.startsWith("http://127.0.0.1") == true,
                streamType = stream.type,
                quality = stream.quality,
                finalUrl = stream.url,
                statusCode = if (stream.audioUrl?.startsWith("http://127.0.0.1") == true) 200 else 403,
                message = "audio validation",
            )
        }

        val resolved = PlaybackResolver(listOf(extractor), validator).resolve(listOf(link("browser")))

        assertEquals("http://127.0.0.1/relay/audio.m3u8", resolved.stream.audioUrl)
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
