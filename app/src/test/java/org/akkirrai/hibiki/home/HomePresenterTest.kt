package org.akkirrai.hibiki.home
import org.akkirrai.hibiki.home.data.*
import org.akkirrai.hibiki.home.model.*
import org.akkirrai.hibiki.home.presentation.*
import org.akkirrai.hibiki.home.screen.*
import org.akkirrai.hibiki.home.state.*
import org.akkirrai.hibiki.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.catalog.model.Anime

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
