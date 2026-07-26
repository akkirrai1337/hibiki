package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GogoAnimeWebViewExtractorTest {
    @Test
    fun `supports current GogoAnime embed hosts`() {
        assertTrue(isGogoAnimePlayerLink(link("https://gogoanime.me.uk/newplayer.php?mal_id=1&ep=1&category=sub")))
        assertTrue(isGogoAnimePlayerLink(link("https://vidmoly.biz/embed-example.html")))
        assertTrue(isGogoAnimePlayerLink(link("https://bysesayeveum.com/e/example")))
        assertFalse(isGogoAnimePlayerLink(link("https://example.com/embed/example")))
        assertFalse(isGogoAnimePlayerLink(link("https://vidmoly.biz/video.m3u8", PlayerType.DIRECT_HLS)))
    }

    private fun link(url: String, type: PlayerType = PlayerType.EMBED) = PlayerLink(
        url = url,
        type = type,
        quality = null,
    )
}
