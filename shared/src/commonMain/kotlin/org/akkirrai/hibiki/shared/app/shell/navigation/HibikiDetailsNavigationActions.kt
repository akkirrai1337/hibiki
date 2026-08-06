package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.navigateBackFromDetails
import org.akkirrai.hibiki.shared.navigation.navigateToDetails

internal class HibikiDetailsNavigationActions(
    private val presenter: AnimeCatalogPresenter,
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
) {
    fun open(anime: Anime) {
        presenter.openDetails(anime)
        val currentRoute = navigationState().currentRoute
        if (currentRoute !is AppRoute.Details || currentRoute.animeId != anime.id) {
            setNavigationState(navigationState().navigateToDetails(anime.id))
        }
    }

    fun close() {
        presenter.closeDetails()
        setNavigationState(navigationState().navigateBackFromDetails())
    }
}
