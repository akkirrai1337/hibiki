package org.akkirrai.beakokit.http

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlSupportTest {
    @Test
    fun `normalizes protocol relative URLs`() {
        assertEquals("https://cdn.example/video.m3u8", normalizeUrl("//cdn.example/video.m3u8"))
    }

    @Test
    fun `resolves root relative and sibling URLs`() {
        val base = "https://example.org/anime/catalog/page.html"

        assertEquals("https://example.org/posters/42.jpg", resolveUrl(base, "/posters/42.jpg"))
        assertEquals("https://example.org/anime/catalog/42.json", resolveUrl(base, "42.json"))
    }
}
