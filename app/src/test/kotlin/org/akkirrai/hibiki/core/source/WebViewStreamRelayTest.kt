package org.akkirrai.hibiki.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewStreamRelayTest {
    @Test
    fun `stream relay starts on headers, survives a locked body, and omits credentials`() {
        val script = WebViewStreamRelay.buildStreamingFetchScript(
            reqId = "request",
            url = "https://cdn.example/master.m3u8",
            headers = emptyMap(),
            range = null,
        )

        // Headers are reported before the body is touched, so a slow segment is not mistaken for a
        // transport that cannot answer at all.
        val startCall = script.indexOf("onStreamStart")
        val bodyRead = script.indexOf("getReader()")
        assertTrue(startCall in 1..<bodyRead)
        // A Service Worker can hand back a body already locked to another reader; that throws
        // rather than failing the request, so the whole-response path has to remain reachable.
        assertTrue(script.contains("catch(e) { reader = null; }"))
        assertTrue(script.contains("response.arrayBuffer()"))
        // Signed HLS CDNs answer with a wildcard CORS header, which a credentialed request cannot use.
        assertTrue(script.contains("credentials:'omit'"))
    }

    @Test
    fun `playlist rewrite covers variants segments keys audio and subtitles`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="keys/key.bin"
            #EXT-X-MEDIA:TYPE=AUDIO,URI="https://audio.example/eng.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,URI="subs/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2000000
            video/720.m3u8
            #EXTINF:6,
            segment-1.ts
        """.trimIndent()
        val proxied = mutableListOf<String>()

        val rewritten = WebViewStreamRelay.rewritePlaylist(
            playlist = playlist,
            baseUrl = "https://cdn.example/path/master.m3u8",
        ) { resolved ->
            proxied += resolved
            "relay://${proxied.lastIndex}"
        }

        assertEquals(
            listOf(
                "https://cdn.example/path/keys/key.bin",
                "https://audio.example/eng.m3u8",
                "https://cdn.example/path/subs/en.m3u8",
                "https://cdn.example/path/video/720.m3u8",
                "https://cdn.example/path/segment-1.ts",
            ),
            proxied,
        )
        assertTrue(rewritten.contains("URI=\"relay://0\""))
        assertTrue(rewritten.contains("relay://3"))
        assertTrue(rewritten.contains("relay://4"))
    }
}
