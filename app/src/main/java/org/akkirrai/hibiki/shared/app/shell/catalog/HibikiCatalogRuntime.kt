package org.akkirrai.hibiki.shared.app.shell.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.catalog.sort.CatalogSort
import org.akkirrai.hibiki.shared.catalog.sort.toAlias
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

internal class HibikiCatalogActions(
    private val presenter: AnimeCatalogPresenter,
) {
    val onQueryChange: (String) -> Unit = presenter::onQueryChange
    val onFiltersChange: (AnimeSearchFilters) -> Unit = presenter::updateFilters
    val onRetry: () -> Unit = presenter::search
    val onRefresh: () -> Unit = presenter::refresh
    val onLoadMoreRetry: () -> Unit = presenter::loadMore
    val onSortSelected: (CatalogSort) -> Unit = { sort ->
        presenter.setFilters(presenter.state.value.filters.copy(sortAlias = sort.toAlias()))
        presenter.search()
    }
}

@Composable
internal fun HibikiCatalogPresenterLifecycle(
    presenter: AnimeCatalogPresenter,
    homeSearchPresenter: HomeSearchPresenter,
    sourceSearchPresenter: SourcesSearchPresenter,
    catalogRefreshKey: Any?,
    catalogReady: Boolean,
    catalogVisible: Boolean,
    onDisposed: () -> Unit,
) {
    LaunchedEffect(presenter, catalogRefreshKey, catalogReady, catalogVisible) {
        if (!catalogReady || !catalogVisible) return@LaunchedEffect
        presenter.loadFilterCatalog()
        presenter.search()
    }
    DisposableEffect(presenter) {
        homeSearchPresenter.loadFilterCatalog()
        onDispose {
            presenter.close()
            homeSearchPresenter.close()
            sourceSearchPresenter.close()
            onDisposed()
        }
    }
}
