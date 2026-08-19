package org.akkirrai.hibiki.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PosterUrlResolverTest {
    @Test
    fun acceptsOnlyHttpUrls() {
        assertEquals("https://example.com/poster.jpg", normalizePosterUrl(" https://example.com/poster.jpg "))
        assertNull(normalizePosterUrl("file:///poster.jpg"))
        assertNull(normalizePosterUrl(null))
    }
}
