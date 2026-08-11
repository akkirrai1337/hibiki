package org.akkirrai.hibiki.shared.catalog.screen

import org.akkirrai.hibiki.shared.catalog.filters.*
import org.akkirrai.hibiki.shared.catalog.sort.*
import org.akkirrai.hibiki.shared.catalog.state.*
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState

import org.akkirrai.hibiki.shared.catalog.filters.*

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
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

data class AppCatalogScreenLabels(
    val errorTitle: String,
    val retryLabel: String,
    val announcementLabel: String,
    val movieLabel: String,
    val searchPlaceholder: String,
    val filterContentDescription: String,
    val clearContentDescription: String,
    val sortTitle: String,
    val sortLabels: Map<CatalogSort, String>,
    val filterUnavailable: String,
    val typeTitle: String,
    val genresTitle: String,
    val yearTitle: String,
    val yearAllLabel: String,
    val yearFromLabel: String,
    val yearToLabel: String,
    val statusTitle: String,
    val resetLabel: String,
    val applyLabel: String,
    val libraryStatusLabel: @Composable (LibraryCategory) -> String,
    val optionText: @Composable (org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption) -> String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCatalogScreen(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    currentYear: Int,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    labels: AppCatalogScreenLabels,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onItemVisible: (Anime) -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    onFiltersApply: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    sortMenuContent: @Composable (
        selectedSort: CatalogSort,
        availableSorts: List<CatalogSort>,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onSortSelected: (CatalogSort) -> Unit,
        title: String,
        label: (CatalogSort) -> String,
    ) -> Unit = { selectedSort, availableSorts, expanded, onExpandedChange, onSortSelected, title, label ->
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current
        val screenWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
        val offsetX = (screenWidthDp - (UiDimens.ScreenPadding * 2) - CatalogSortMenuWidth) / 2
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(CatalogSortMenuWidth),
            offset = DpOffset(
                x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) offsetX else -offsetX,
                y = CatalogSortMenuOffsetY,
            ),
            shape = RoundedCornerShape(CatalogSortMenuCornerRadius),
        ) {
            AppCatalogSortMenuContent(
                title = title,
                sorts = availableSorts,
                selectedSort = selectedSort,
                label = label,
                expanded = expanded,
                onSortSelected = {
                    onExpandedChange(false)
                    onSortSelected(it)
                },
                orderContent = { atEnd, orderModifier ->
                    AppCatalogSortOrderIcon(atEnd = atEnd, modifier = orderModifier)
                },
            )
        }
    },
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
        state.filterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }
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
            )?.let(onSortSelected)
        }
    }

    AppCatalogSortVisibilityEffect(
        listState = listState,
        onVisibilityChange = { isSortVisible = it },
    )

    AppCatalogPaginationEffect(
        listState = listState,
        state = state,
        onLoadMore = onLoadMoreRetry,
    )

    LaunchedEffect(state.isLoading, state.isLoadingMore) {
        if (!state.isLoading || state.isLoadingMore) {
            isPullRefreshing = false
        }
    }

    AppSystemBackHandler(
        enabled = searchFieldFocused,
        onBack = {
            focusManager.clearFocus()
            keyboardController?.hide()
            searchFieldFocused = false
        },
    ) {
        Box(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = isPullRefreshing && state.isLoading && !state.isLoadingMore,
            onRefresh = {
                isPullRefreshing = true
                onRefresh()
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isPullRefreshing && state.isLoading && !state.isLoadingMore,
                    modifier = Modifier.padding(top = CatalogContentTopPadding),
                )
            },
        ) {
        AppCatalogScreenContent(
            state = state,
            listState = listState,
            topContentPadding = CatalogContentTopPadding,
            bottomContentPadding = bottomContentPadding,
            errorTitle = labels.errorTitle,
            retryLabel = labels.retryLabel,
            announcementLabel = labels.announcementLabel,
            movieLabel = labels.movieLabel,
            libraryStatusByAnimeId = libraryStatusByAnimeId,
            libraryStatusLabel = labels.libraryStatusLabel,
            onAnimeClick = onAnimeClick,
            onItemVisible = onItemVisible,
            onRetry = onRetry,
            onLoadMoreRetry = onLoadMoreRetry,
        )
        }

        AppCatalogTopOverlay(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = { onQueryChange("") },
            placeholder = labels.searchPlaceholder,
            filterContentDescription = labels.filterContentDescription,
            clearContentDescription = labels.clearContentDescription,
            onFilterClick = { isFilterSheetOpen = true },
            showFilterButton = hasCatalogFilters,
            showSort = isSortVisible &&
                state.items.isNotEmpty() &&
                !state.isLoading &&
                state.error == null,
            onSearchFocusChanged = { searchFieldFocused = it },
            sortContent = {
                AppCatalogSortControl(
                    sortKey = selectedSort.name,
                    icon = selectedSort.icon(),
                    label = labels.sortLabels.getValue(selectedSort),
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
                        sortMenuContent(
                            selectedSort,
                            availableSorts,
                            isSortMenuOpen,
                            { isSortMenuOpen = it },
                            onSortSelected,
                            labels.sortTitle,
                            labels.sortLabels::getValue,
                        )
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
            onApply = onFiltersApply,
            onDismissRequest = { isFilterSheetOpen = false },
            unavailableLabel = labels.filterUnavailable,
            typeTitle = labels.typeTitle,
            genresTitle = labels.genresTitle,
            yearTitle = labels.yearTitle,
            yearAllLabel = labels.yearAllLabel,
            yearFromLabel = labels.yearFromLabel,
            yearToLabel = labels.yearToLabel,
            statusTitle = labels.statusTitle,
            resetLabel = labels.resetLabel,
            applyLabel = labels.applyLabel,
            defaultYearRange = defaultCatalogFilterYearRange(currentYear),
            optionText = labels.optionText,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.LargeCorner),
            )
        }
    }
}
