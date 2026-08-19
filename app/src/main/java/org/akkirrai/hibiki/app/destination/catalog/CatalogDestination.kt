package org.akkirrai.hibiki.app.destination.catalog

import androidx.compose.foundation.lazy.LazyListState
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.catalog.sort.CatalogSort
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

internal data class AppDestinationCatalogActions(
    val onQueryChange: (String) -> Unit,
    val onFiltersChange: (AnimeSearchFilters) -> Unit,
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMoreRetry: () -> Unit,
    val onSortSelected: (CatalogSort) -> Unit,
)

internal data class AppDestinationCatalogState(
    val query: String,
    val items: List<Anime>,
    val filters: AnimeSearchFilters,
    val filterCatalog: AnimeCatalogFilterCatalog?,
    val ui: AnimeCatalogUiState,
    val listState: LazyListState,
)
