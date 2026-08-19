package org.akkirrai.hibiki.app.destination.catalog

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.catalog.sort.CatalogSort
import org.akkirrai.hibiki.home.screen.SearchScreen
import org.akkirrai.hibiki.library.LibraryEntry
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
