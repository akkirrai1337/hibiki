package org.akkirrai.hibiki.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.Anime

class HomePresenterTest {
    @Test
    fun updatesSharedHomeState() {
        val presenter = HomePresenter()
        val anime = Anime(
            id = "1",
            title = "Demo",
            subtitle = "",
            episodesLabel = "",
            status = "",
        )

        presenter.update { it.copy(featuredAnime = listOf(anime), isLoading = false) }

        assertEquals(listOf(anime), presenter.state.value.featuredAnime)
        assertEquals(false, presenter.state.value.isLoading)
    }
}
