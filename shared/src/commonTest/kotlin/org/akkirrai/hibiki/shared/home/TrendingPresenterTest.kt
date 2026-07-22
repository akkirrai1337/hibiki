package org.akkirrai.hibiki.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.Anime

class TrendingPresenterTest {
    @Test
    fun updatesItemsAndFilter() {
        val presenter = TrendingPresenter()
        val anime = Anime(
            id = "1",
            title = "Demo",
            subtitle = "",
            episodesLabel = "",
            status = "",
        )

        presenter.update { it.copy(selectedFilter = TrendingFilter.Movies, items = listOf(anime)) }

        assertEquals(TrendingFilter.Movies, presenter.state.value.selectedFilter)
        assertEquals(listOf(anime), presenter.state.value.items)
    }
}
