package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.player.model.PlaybackLinkOption

class PlayerOptionsResolverTest {
    @Test
    fun returnsDistinctPlayerNamesInSourceOrder() {
        assertEquals(
            listOf("A", "B"),
            uniquePlayerNames(
                listOf(
                    PlaybackLinkOption("A", null),
                    PlaybackLinkOption("A", "720p"),
                    PlaybackLinkOption(null, "480p"),
                    PlaybackLinkOption("B", null),
                ),
            ),
        )
    }
}
