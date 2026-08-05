package org.akkirrai.hibiki.shared.app.shell.catalog

import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.sort.CatalogSort
import org.akkirrai.hibiki.shared.catalog.sort.toAlias
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

internal class HibikiCatalogActions(
    private val presenter: AnimeCatalogPresenter,
) {
    val onQueryChange: (String) -> Unit = presenter::onQueryChange
    val onFiltersChange: (AnimeSearchFilters) -> Unit = presenter::updateFilters
    val onRetry: () -> Unit = presenter::search
    val onLoadMoreRetry: () -> Unit = presenter::loadMore
    val onSortSelected: (CatalogSort) -> Unit = { sort ->
        presenter.setFilters(presenter.state.value.filters.copy(sortAlias = sort.toAlias()))
        presenter.search()
    }
}
