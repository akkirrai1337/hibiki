package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.player.model.PlaybackSelection

class PlaybackSelectionResolverTest {
    @Test
    fun ignoresSavedSelectionForAnotherSource() {
        val result = resolvePlaybackPreferences(
            sourceId = "source-current",
            savedSelection = selection(sourceId = "source-old"),
            explicitPlayerName = null,
            explicitQuality = null,
            allowSavedSelection = true,
        )

        assertEquals(EffectivePlaybackPreferences(null, null), result)
    }

    @Test
    fun explicitSelectionOverridesSavedValues() {
        val result = resolvePlaybackPreferences(
            sourceId = "source-current",
            savedSelection = selection(sourceId = "source-current"),
            explicitPlayerName = "explicit-player",
            explicitQuality = "720p",
            allowSavedSelection = true,
        )

        assertEquals(EffectivePlaybackPreferences("explicit-player", "720p"), result)
    }

    private fun selection(sourceId: String) = PlaybackSelection(
        titleId = "title",
        sourceId = sourceId,
        sourceTitle = "Source",
        quality = "1080p",
        playerName = "saved-player",
    )
}
