package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType

class PlaybackLinkSelectionTest {
    @Test
    fun filtersUnsupportedLinksAndPrefersRequestedQuality() {
        val links = listOf(
            PlayerLink("https://unsupported", PlayerType.EMBED, "1080p", playerName = "embed"),
            PlayerLink("https://direct-720", PlayerType.DIRECT_HLS, "720p", playerName = "Kodik"),
            PlayerLink("https://direct-1080", PlayerType.DIRECT_HLS, "1080p", playerName = "Kodik"),
        )

        val selected = selectPlaybackLinks(
            links = links,
            supports = { it.type != PlayerType.EMBED },
            preferredQuality = "1080p",
        )

        assertEquals(listOf("https://direct-1080", "https://direct-720"), selected.map(PlayerLink::url))
    }
}
