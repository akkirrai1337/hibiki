package org.akkirrai.hibiki.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewStreamRelayTest {
    @Test
    fun `stream relay uses XHR for playlists and keeps fetch streaming for media`() {
        val script = WebViewStreamRelay.buildStreamingFetchScript(
            reqId = "request",
            url = "https://cdn.example/master.m3u8",
            headers = emptyMap(),
            range = null,
            preferXhr = true,
        )

        // Headers are reported before the body is touched, so a slow segment is not mistaken for a
        // transport that cannot answer at all.
        val startCall = script.indexOf("onStreamStart")
        val bodyRead = script.indexOf("getReader()")
        assertTrue(startCall in 1..<bodyRead)
        assertTrue(script.contains("if (true)"))
        assertTrue(script.contains("var sendXhr = function()"))
        assertTrue(script.contains("xhr.responseType = 'arraybuffer'"))
        // A Service Worker can hand back a body already owned by another reader. Detect that
        // before attempting arrayBuffer(), so native code can move to the next relay transport.
        assertTrue(script.contains("response.bodyUsed || bodyLocked"))
        assertTrue(script.contains("Response body is unavailable:"))
        assertTrue(script.contains("onStreamDiagnostic"))
        assertTrue(script.contains("response.arrayBuffer()"))
        // The first cross-origin attempt remains anonymous for wildcard-CORS CDNs, but the
        // same-origin fallback carries the WebView's session cookies.
        assertTrue(script.contains("credentials:'same-origin'"))
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
