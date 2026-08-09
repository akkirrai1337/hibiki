package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.home.screen.SearchScreen
import org.akkirrai.hibiki.shared.catalog.sort.CatalogSort
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.library.LibraryEntry
import androidx.compose.foundation.lazy.LazyListState

@Composable
internal fun ColumnScope.CatalogDestinationRoute(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    libraryEntries: List<LibraryEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    bottomContentPadding: Dp,
) {
    SearchScreen(
        state = state,
        listState = listState,
        libraryStatusByAnimeId = libraryEntries.associate { it.anime.id to it.category },
        query = query,
        onQueryChange = onQueryChange,
        items = items,
        filters = filters,
        filterCatalog = filterCatalog,
        onFiltersChange = onFiltersChange,
        onAnimeClick = onAnimeClick,
        onRetry = onRetry,
        onRefresh = onRefresh,
        onLoadMoreRetry = onLoadMoreRetry,
        onSortSelected = onSortSelected,
        bottomContentPadding = bottomContentPadding,
    )
}
