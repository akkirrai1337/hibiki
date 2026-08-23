package org.akkirrai.hibiki.home.screen

import org.akkirrai.hibiki.app.defaultCatalogScreenLabels

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.hibiki.catalog.screen.AppCatalogScreen
import org.akkirrai.hibiki.catalog.screen.CatalogActions
import org.akkirrai.hibiki.catalog.sort.CatalogSort
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState

@Composable
internal fun ColumnScope.SearchScreen(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    bottomContentPadding: Dp,
) {
    AppCatalogScreen(
        state = state,
        listState = listState,
        bottomContentPadding = bottomContentPadding,
        currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        labels = defaultCatalogScreenLabels(),
        actions = CatalogActions(
            onQueryChange = onQueryChange,
            onRetry = onRetry,
            onRefresh = onRefresh,
            onLoadMoreRetry = onLoadMoreRetry,
            onItemVisible = {},
            onSortSelected = onSortSelected,
            onFiltersApply = onFiltersChange,
            onAnimeClick = onAnimeClick,
        ),
        modifier = Modifier.fillMaxSize(),
    )
}
