package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerPresenterTest {
    @Test
    fun updatesPlaybackSelection() {
        val presenter = PlayerPresenter()

        presenter.update {
            it.copy(
                currentEpisodeId = "episode-2",
                selectedQualityLabel = "1080p",
            )
        }

        assertEquals("episode-2", presenter.state.value.currentEpisodeId)
        assertEquals("1080p", presenter.state.value.selectedQualityLabel)
    }
}
