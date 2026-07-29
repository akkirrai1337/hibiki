package org.akkirrai.hibiki.shared.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
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
    val optionText: @Composable (org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption) -> String,
)

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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(CatalogSortMenuWidth),
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
    val selectedSort = catalogSortFromAlias(state.filters.sortAlias)
    val availableSorts = remember(state.filterCatalog?.capabilities) {
        state.filterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }
    val hasCatalogFilters = state.filterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true

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

    Box(modifier = modifier) {
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

        val sortAlpha by animateFloatAsState(
            targetValue = if (isSortVisible) 1f else 0f,
            animationSpec = tween(CatalogSortAnimationDurationMs),
            label = "catalog_sort_alpha",
        )
        AppCatalogTopOverlay(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = { onQueryChange("") },
            placeholder = labels.searchPlaceholder,
            filterContentDescription = labels.filterContentDescription,
            clearContentDescription = labels.clearContentDescription,
            onFilterClick = { isFilterSheetOpen = true },
            showFilterButton = hasCatalogFilters,
            sortModifier = Modifier.alpha(sortAlpha),
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
