package org.akkirrai.hibiki.shared.home
import org.akkirrai.hibiki.shared.home.data.*
import org.akkirrai.hibiki.shared.home.model.*
import org.akkirrai.hibiki.shared.home.presentation.*
import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.*
import org.akkirrai.hibiki.shared.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.catalog.model.Anime

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
