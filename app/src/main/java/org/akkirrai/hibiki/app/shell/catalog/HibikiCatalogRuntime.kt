package org.akkirrai.hibiki.app.shell.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.catalog.sort.CatalogSort
import org.akkirrai.hibiki.catalog.sort.toAlias
import org.akkirrai.hibiki.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

private object NotLoadedSentinel

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
    // Sentinel distinct from any real catalogRefreshKey (including null) so the first
    // genuine load always runs once catalogReady/catalogVisible become true.
    var loadedRefreshKey by remember(presenter) { mutableStateOf<Any?>(NotLoadedSentinel) }
    LaunchedEffect(presenter, catalogRefreshKey, catalogReady, catalogVisible) {
        if (!catalogReady || !catalogVisible) return@LaunchedEffect
        // Merely returning to an already-visible Catalog tab flips catalogVisible again,
        // which used to re-run this effect and wipe+refetch an already-loaded grid (visible
        // as posters disappearing then reappearing). Only reload when the refresh key
        // actually changed, e.g. installed sources changed.
        if (loadedRefreshKey == catalogRefreshKey) return@LaunchedEffect
        loadedRefreshKey = catalogRefreshKey
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
