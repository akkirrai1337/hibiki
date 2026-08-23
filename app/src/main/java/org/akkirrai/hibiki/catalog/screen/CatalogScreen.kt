package org.akkirrai.hibiki.catalog.screen

import org.akkirrai.hibiki.catalog.filters.*
import org.akkirrai.hibiki.catalog.sort.*
import org.akkirrai.hibiki.catalog.state.*
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import org.akkirrai.hibiki.design.UiDimens
import androidx.activity.compose.BackHandler
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.app.libraryText
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

private fun catalogSortLabel(sort: CatalogSort): AppTextKey = when (sort) {
    CatalogSort.Alphabetical -> AppTextKey.CatalogSortAlphabetical
    CatalogSort.Popular -> AppTextKey.CatalogSortPopular
    CatalogSort.Updated -> AppTextKey.CatalogSortUpdated
}

data class CatalogActions(
    val onQueryChange: (String) -> Unit,
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMoreRetry: () -> Unit,
    val onItemVisible: (Anime) -> Unit,
    val onSortSelected: (CatalogSort) -> Unit,
    val onFiltersApply: (AnimeSearchFilters) -> Unit,
    val onAnimeClick: (Anime) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    currentYear: Int,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    actions: CatalogActions,
    modifier: Modifier = Modifier,
) {
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isSortVisible by remember { mutableStateOf(true) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedSort = catalogSortFromAlias(state.filters.sortAlias)
    val availableSorts = remember(state.filterCatalog?.capabilities) {
        state.filterCatalog?.capabilities?.let(::availableCatalogSorts).orEmpty()
    }
    val hasSelectableCatalogSorts = availableSorts.size > 1
    val contentTopPadding = catalogContentTopPadding(hasSelectableCatalogSorts)
    val hasCatalogFilters = state.filterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true ||
        state.isFilterCatalogLoading

    AppCatalogFilterVisibilityEffect(
        hasFilters = hasCatalogFilters,
        onFiltersUnavailable = { isFilterSheetOpen = false },
    )

    LaunchedEffect(availableSorts, selectedSort) {
        if (selectedSort !in availableSorts) {
            fallbackCatalogSort(
                supportedSortAlias = state.filterCatalog?.capabilities?.supportedSorts?.firstOrNull(),
                availableSorts = availableSorts,
            )?.let(actions.onSortSelected)
        }
    }

    AppCatalogSortVisibilityEffect(
        listState = listState,
        onVisibilityChange = { isSortVisible = it },
    )

    AppCatalogPaginationEffect(
        listState = listState,
        state = state,
        onLoadMore = actions.onLoadMoreRetry,
    )

    LaunchedEffect(state.isLoading, state.isLoadingMore) {
        if (!state.isLoading || state.isLoadingMore) {
            isPullRefreshing = false
        }
    }

    BackHandler(
        enabled = searchFieldFocused,
        onBack = {
            focusManager.clearFocus()
            keyboardController?.hide()
            searchFieldFocused = false
        },
    )
    run {
        Box(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = isPullRefreshing && state.isLoading && !state.isLoadingMore,
            onRefresh = {
                isPullRefreshing = true
                actions.onRefresh()
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isPullRefreshing && state.isLoading && !state.isLoadingMore,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentTopPadding),
                )
            },
        ) {
        AppCatalogScreenContent(
            state = state,
            listState = listState,
            topContentPadding = contentTopPadding,
            bottomContentPadding = bottomContentPadding,
            errorTitle = appText(AppTextKey.CatalogError),
            retryLabel = appText(AppTextKey.SearchRetry),
            announcementLabel = appText(AppTextKey.Announcement),
            movieLabel = appText(AppTextKey.Type),
            libraryStatusByAnimeId = libraryStatusByAnimeId,
            libraryStatusLabel = { category -> category.libraryText() },
            onAnimeClick = actions.onAnimeClick,
            onItemVisible = actions.onItemVisible,
            onRetry = actions.onRetry,
            onLoadMoreRetry = actions.onLoadMoreRetry,
            isPullRefreshing = isPullRefreshing && state.isLoading && !state.isLoadingMore,
        )
        }

        AppCatalogTopOverlay(
            query = state.query,
            onQueryChange = actions.onQueryChange,
            onClear = { actions.onQueryChange("") },
            placeholder = appText(AppTextKey.SearchPlaceholder),
            filterContentDescription = appText(AppTextKey.SearchFilters),
            clearContentDescription = appText(AppTextKey.Back),
            onFilterClick = { isFilterSheetOpen = true },
            showFilterButton = hasCatalogFilters,
            showSort = isSortVisible &&
                hasSelectableCatalogSorts &&
                state.items.isNotEmpty() &&
                !state.isLoading &&
                state.error == null,
            onSearchFocusChanged = { searchFieldFocused = it },
            sortContent = {
                AppCatalogSortControl(
                    sortKey = selectedSort.name,
                    icon = selectedSort.icon(),
                    label = appText(catalogSortLabel(selectedSort)),
                    expanded = isSortMenuOpen,
                    onExpandedChange = { isSortMenuOpen = it },
                    orderContent = { orderModifier ->
                        AppCatalogSortOrderIcon(
                            atEnd = isSortMenuOpen,
                            modifier = orderModifier,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    },
                    menuContent = {
                        val layoutDirection = LocalLayoutDirection.current
                        val density = LocalDensity.current
                        val screenWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
                        val offsetX = (screenWidthDp - (UiDimens.ScreenPadding * 2) - CatalogSortMenuWidth) / 2
                        DropdownMenu(
                            expanded = isSortMenuOpen,
                            onDismissRequest = { isSortMenuOpen = false },
                            modifier = Modifier.width(CatalogSortMenuWidth),
                            offset = DpOffset(
                                x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) offsetX else -offsetX,
                                y = CatalogSortMenuOffsetY,
                            ),
                            shape = RoundedCornerShape(CatalogSortMenuCornerRadius),
                        ) {
                            AppCatalogSortMenuContent(
                                title = appText(AppTextKey.CatalogSortTitle),
                                sorts = availableSorts,
                                selectedSort = selectedSort,
                                label = { sort -> appText(catalogSortLabel(sort)) },
                                expanded = isSortMenuOpen,
                                onSortSelected = {
                                    isSortMenuOpen = false
                                    actions.onSortSelected(it)
                                },
                                orderContent = { atEnd, orderModifier ->
                                    AppCatalogSortOrderIcon(atEnd = atEnd, modifier = orderModifier)
                                },
                            )
                        }
                    },
                )
            },
        )
        }

        if (isFilterSheetOpen) {
            AppCatalogFilterSheet(
            initialFilters = state.filters,
            filterCatalog = state.filterCatalog,
            isFilterCatalogLoading = state.isFilterCatalogLoading,
            onApply = actions.onFiltersApply,
            onDismissRequest = { isFilterSheetOpen = false },
            unavailableLabel = appText(AppTextKey.FilterUnavailable),
            typeTitle = appText(AppTextKey.Type),
            genresTitle = appText(AppTextKey.Genres),
            yearTitle = appText(AppTextKey.ReleaseDate),
            yearAllLabel = appText(AppTextKey.FilterAllYears),
            yearFromLabel = appText(AppTextKey.FilterFromYear),
            yearToLabel = appText(AppTextKey.FilterToYear),
            statusTitle = appText(AppTextKey.Status),
            resetLabel = appText(AppTextKey.FilterReset),
            applyLabel = appText(AppTextKey.FilterApply),
            defaultYearRange = defaultCatalogFilterYearRange(currentYear),
            optionText = { it.title },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.LargeCorner),
            )
        }
    }
}
