package org.akkirrai.hibiki.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.design.component.anime.BROWSE_GRID_COLUMNS
import org.akkirrai.hibiki.core.design.component.anime.PortraitGridSpacing
import org.akkirrai.hibiki.core.design.component.anime.searchStatePosterGridContent
import org.akkirrai.hibiki.core.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet

/** The app's one search screen, opened from Home and Catalog with the text typed so far. */
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onAnimeClick: (Anime) -> Unit,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(LocalContext.current)),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // Only the first arrival seeds the query; a restored screen keeps what was already typed.
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && state.query.isBlank()) viewModel.startSearch(initialQuery)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    val loadMoreLabel = stringResource(R.string.search_load_more)
    val idleTitle = stringResource(R.string.search_start_title)
    val idleMessage = stringResource(R.string.search_idle)
    val emptyTitle = stringResource(R.string.home_search_empty_title)
    val emptyMessage = stringResource(R.string.home_search_empty_message)
    val retryLabel = stringResource(R.string.search_retry)
    val barBottom = UiDimens.SearchBarTopPadding + UiDimens.SearchBarHeight + 12.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(BROWSE_GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = UiDimens.ScreenPadding,
                top = barBottom,
                end = UiDimens.ScreenPadding,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(PortraitGridSpacing),
            horizontalArrangement = Arrangement.spacedBy(PortraitGridSpacing),
        ) {
            searchStatePosterGridContent(
                state = state.result,
                onAnimeClick = onAnimeClick,
                onLoadMore = viewModel::loadMore,
                loadMoreLabel = loadMoreLabel,
                resultsCountLabel = { count -> pluralStringResource(R.plurals.search_results_count, count, count) },
                idleTitle = idleTitle,
                idleMessage = idleMessage,
                idleIcon = Icons.Outlined.Search,
                emptyTitle = emptyTitle,
                emptyMessage = emptyMessage,
                emptyIcon = Icons.Outlined.SearchOff,
                errorActionLabel = retryLabel,
                onErrorActionClick = viewModel::retry,
                titleOverlay = true,
            )
        }

        AppTopScrim(modifier = Modifier.align(Alignment.TopCenter), height = barBottom + 18.dp)

        AppSearchTopBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onClear = viewModel::clear,
            onFilterClick = {
                keyboard?.hide()
                focusManager.clearFocus(force = true)
                showFilters = true
            },
            showFilter = state.filterCatalog?.let {
                it.capabilities.supportedFilters.isNotEmpty() || it.sourceFilters.isNotEmpty()
            } ?: false,
            focusRequester = focusRequester,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = UiDimens.SearchBarTopPadding,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                ),
        )

        if (showFilters) {
            AnimeSearchFiltersSheet(
                initialFilters = state.filters,
                filterCatalog = state.filterCatalog,
                isFilterCatalogLoading = state.isFilterCatalogLoading,
                onApply = viewModel::applyFilters,
                onDismissRequest = { showFilters = false },
            )
        }
    }
}
