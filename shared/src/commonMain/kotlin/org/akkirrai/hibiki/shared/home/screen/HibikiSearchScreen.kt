package org.akkirrai.hibiki.shared.home.screen

import org.akkirrai.hibiki.shared.app.defaultCatalogScreenLabels
import org.akkirrai.hibiki.shared.catalog.screen.CatalogScreenContent

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.hibiki.shared.catalog.screen.AppCatalogScreen
import org.akkirrai.hibiki.shared.catalog.sort.CatalogSort
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState

@Composable
internal fun ColumnScope.SearchScreen(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
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
        onQueryChange = onQueryChange,
        onRetry = onRetry,
        onLoadMoreRetry = onLoadMoreRetry,
        onItemVisible = {},
        onSortSelected = onSortSelected,
        onFiltersApply = onFiltersChange,
        onAnimeClick = onAnimeClick,
        modifier = Modifier.fillMaxSize(),
    )
}
