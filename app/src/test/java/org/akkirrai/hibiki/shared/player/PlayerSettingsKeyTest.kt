package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSettingsKeyTest {
    @Test
    fun includesPlaybackSourceEpisodeAndSelections() {
        val state = PlayerUiState(
            currentSourceId = "source",
            currentEpisodeId = "episode",
            selectedPlayerName = "Player",
            selectedQualityLabel = "1080p",
        )

        assertEquals("source:episode:Player:1080p", state.settingsOptionsKey())
    }
}
