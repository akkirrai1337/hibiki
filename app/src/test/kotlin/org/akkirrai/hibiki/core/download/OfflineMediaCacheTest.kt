package org.akkirrai.hibiki.core.download

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineMediaCacheTest {
    @Test
    fun `subtitle request uses its own headers without leaking video authorization`() {
        val subtitleUrl = "https://subtitles.test/en.vtt"

        val headers = OfflineMediaCache.playbackRequestHeadersForUrl(
            defaultHeaders = mapOf("Authorization" to "video-token", "Referer" to "https://video.test/"),
            resourceHeadersByUrl = mapOf(
                subtitleUrl to mapOf("Authorization" to "subtitle-token", "Referer" to "https://subtitles.test/"),
            ),
            url = subtitleUrl,
        )

        assertEquals("subtitle-token", headers["Authorization"])
        assertEquals("https://subtitles.test/", headers["Referer"])
    }

    @Test
    fun `subtitle with empty headers inherits video headers`() {
        val headers = OfflineMediaCache.playbackRequestHeadersForUrl(
            defaultHeaders = mapOf("Authorization" to "video-token"),
            resourceHeadersByUrl = mapOf("https://subtitles.test/en.vtt" to emptyMap()),
            url = "https://subtitles.test/en.vtt",
        )

        assertEquals("video-token", headers["Authorization"])
    }
}
