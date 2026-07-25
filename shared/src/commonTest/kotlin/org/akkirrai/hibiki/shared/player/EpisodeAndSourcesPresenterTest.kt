package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeAndSourcesPresenterTest {
    @Test
    fun updatesEpisodeState() {
        val presenter = EpisodesPresenter()

        presenter.update { it.copy(result = EpisodesUiState.Empty) }

        assertEquals(EpisodesUiState.Empty, presenter.state.value.result)
    }

    @Test
    fun updatesSourcesState() {
        val presenter = WatchSourcesPresenter()

        presenter.update { it.copy(showAllItems = true, hasMoreItems = false) }

        assertEquals(true, presenter.state.value.showAllItems)
        assertEquals(false, presenter.state.value.hasMoreItems)
    }
}
