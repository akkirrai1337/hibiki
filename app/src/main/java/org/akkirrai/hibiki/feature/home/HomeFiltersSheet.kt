package org.akkirrai.hibiki.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.feature.search.SearchViewModel

/**
 * The search filters, opened from Home's search bar. Home has no search of its own, so this only
 * reads the active source's filter catalog and hands the chosen filters back to be searched with.
 */
@Composable
fun HomeFiltersSheet(
    onApply: (AnimeSearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        key = "home-filters",
        factory = SearchViewModel.Factory(LocalContext.current),
    )
    val state by viewModel.uiState.collectAsState()
    AnimeSearchFiltersSheet(
        initialFilters = state.filters,
        filterCatalog = state.filterCatalog,
        isFilterCatalogLoading = state.isFilterCatalogLoading,
        onApply = onApply,
        onDismissRequest = onDismissRequest,
    )
}
