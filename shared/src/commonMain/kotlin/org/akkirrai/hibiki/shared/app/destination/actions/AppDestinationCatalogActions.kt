package org.akkirrai.hibiki.shared.app.destination.actions

import org.akkirrai.hibiki.shared.catalog.sort.CatalogSort
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

internal data class AppDestinationCatalogActions(
    val onQueryChange: (String) -> Unit,
    val onFiltersChange: (AnimeSearchFilters) -> Unit,
    val onRetry: () -> Unit,
    val onLoadMoreRetry: () -> Unit,
    val onSortSelected: (CatalogSort) -> Unit,
)
