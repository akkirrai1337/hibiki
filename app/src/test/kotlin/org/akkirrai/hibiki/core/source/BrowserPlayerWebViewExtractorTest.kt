package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.extension.BrowserScriptResolver
import org.akkirrai.beakokit.extension.BrowserPlaybackMode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPlayerWebViewExtractorTest {
    @Test
    fun `routing is generic and delegates host knowledge to resolver`() {
        val resolver = object : BrowserScriptResolver {
            override fun supportsBrowser(link: PlayerLink) = link.url.contains("player.example")
            override suspend fun browserScript(link: PlayerLink) = ""
        }

        assertTrue(BrowserResolverRouting.supports(embed("https://player.example/watch/1"), listOf(resolver)))
        assertFalse(BrowserResolverRouting.supports(embed("https://unknown.example/watch/1"), listOf(resolver)))
        assertFalse(BrowserResolverRouting.supports(embed("https://player.example/video.m3u8", PlayerType.DIRECT_HLS), listOf(resolver)))
    }

    @Test
    fun `video element stream wins over an earlier audio manifest`() {
        val audio = BrowserCapturedStream("https://cdn.example/audio/eng.m3u8", emptyMap(), BrowserCaptureOrigin.NETWORK)
        val video = BrowserCapturedStream("https://cdn.example/video/720.m3u8", emptyMap(), BrowserCaptureOrigin.VIDEO_ELEMENT)

        assertEquals(video, BrowserStreamSelector.select(listOf(audio, video)))
    }

    @Test
    fun `network fallback rejects audio rendition when no video element URL is available`() {
        val video = BrowserCapturedStream("https://cdn.example/video/720.m3u8", emptyMap(), BrowserCaptureOrigin.NETWORK)
        val audio = BrowserCapturedStream("https://cdn.example/audio/eng.m3u8", emptyMap(), BrowserCaptureOrigin.NETWORK)

        assertEquals(video, BrowserStreamSelector.select(listOf(video, audio)))
    }

    @Test
    fun `source-declared video and audio streams take priority over URL guesses`() {
        val video = BrowserCapturedStream("https://cdn.example/manifest.m3u8", emptyMap(), BrowserCaptureOrigin.SOURCE_VIDEO)
        val audio = BrowserCapturedStream("https://cdn.example/track.m3u8", emptyMap(), BrowserCaptureOrigin.SOURCE_AUDIO)

        assertEquals(video, BrowserStreamSelector.select(listOf(audio, video)))
        assertEquals(audio, BrowserStreamSelector.findAudio(listOf(video, audio)))
    }

    @Test
    fun `source-declared master playlist wins over its video rendition`() {
        val rendition = BrowserCapturedStream("https://cdn.example/video/720.m3u8", emptyMap(), BrowserCaptureOrigin.SOURCE_VIDEO)
        val master = BrowserCapturedStream("https://cdn.example/master.m3u8", emptyMap(), BrowserCaptureOrigin.SOURCE_MASTER)

        assertEquals(master, BrowserStreamSelector.select(listOf(rendition, master)))
    }

    @Test
    fun `direct relay fallback accepts transport blocks and rejects missing media`() {
        fun failure(status: Int?) = StreamValidationResult(
            success = false,
            streamType = StreamType.HLS,
            quality = null,
            finalUrl = "https://cdn.example/master.m3u8",
            statusCode = status,
            message = "test",
        )

        assertTrue(DirectRelayFallbackPolicy.shouldRelay(listOf(failure(403))))
        assertTrue(DirectRelayFallbackPolicy.shouldRelay(listOf(failure(429))))
        assertTrue(DirectRelayFallbackPolicy.shouldRelay(listOf(failure(200))))
        assertTrue(DirectRelayFallbackPolicy.shouldRelay(listOf(failure(null))))
        assertFalse(DirectRelayFallbackPolicy.shouldRelay(listOf(failure(404))))
    }

    private fun embed(url: String, type: PlayerType = PlayerType.EMBED) = PlayerLink(url, type, null)
}
