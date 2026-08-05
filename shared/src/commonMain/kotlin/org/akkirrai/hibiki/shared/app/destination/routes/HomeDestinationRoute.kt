package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.catalog.sort.CatalogSort
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.shared.home.screen.HomeScreen
import org.akkirrai.hibiki.shared.home.state.HomeUiState
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

@Composable
internal fun ColumnScope.HomeDestinationRoute(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    sourcesById: Map<String, AppSourceDescriptor>,
    libraryEntries: List<LibraryEntry>,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    onQueryChange: (String) -> Unit,
    homeSearchState: HomeSearchUiState,
    onFilterApply: (AnimeSearchFilters) -> Unit,
    onSearchClear: () -> Unit,
    onSearchLoadMore: () -> Unit,
    onSearchRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    baseHomeState: HomeUiState,
    onItemVisible: (Anime) -> Unit,
    onHomeRefresh: () -> Unit,
    bottomContentPadding: Dp,
) {
    HomeScreen(
        state = state,
        listState = listState,
        sourcesById = sourcesById,
        libraryEntries = libraryEntries,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        onQueryChange = onQueryChange,
        homeSearchState = homeSearchState,
        onFilterApply = onFilterApply,
        onSearchClear = onSearchClear,
        onSearchLoadMore = onSearchLoadMore,
        onSearchRetry = onSearchRetry,
        onAnimeClick = onAnimeClick,
        onRetry = onRetry,
        onLoadMoreRetry = onLoadMoreRetry,
        onSortSelected = onSortSelected,
        onBrowseCatalog = onBrowseCatalog,
        onOpenLibrary = onOpenLibrary,
        baseHomeState = baseHomeState,
        onItemVisible = onItemVisible,
        onHomeRefresh = onHomeRefresh,
        bottomContentPadding = bottomContentPadding,
    )
}
