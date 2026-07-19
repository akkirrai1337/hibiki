package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePaheWebViewExtractorTest {
    @Test
    fun `supports current AnimePahe embed hosts`() {
        assertTrue(isAnimePahePlayerLink(link("https://megaplay.buzz/stream/fixture/dub", PlayerType.EMBED)))
        assertTrue(isAnimePahePlayerLink(link("https://vidwish.live/stream/fixture/dub", PlayerType.EMBED)))
        assertFalse(isAnimePahePlayerLink(link("https://kwik.cx/e/session", PlayerType.EMBED)))
        assertFalse(isAnimePahePlayerLink(link("https://megaplay.buzz/video.m3u8", PlayerType.DIRECT_HLS)))
    }

    private fun link(url: String, type: PlayerType) = PlayerLink(url = url, type = type, quality = null)
}
