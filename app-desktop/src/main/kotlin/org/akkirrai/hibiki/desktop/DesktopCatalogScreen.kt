package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.akkirrai.hibiki.shared.catalog.AppCatalogFilterSheet
import org.akkirrai.hibiki.shared.catalog.AppCatalogPaginationEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogQueryEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreenContent
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortControl
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortMenuContent
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortOrderIcon
import org.akkirrai.hibiki.shared.catalog.AppCatalogTopOverlay
import org.akkirrai.hibiki.shared.catalog.CatalogSort
import org.akkirrai.hibiki.shared.catalog.availableCatalogSorts
import org.akkirrai.hibiki.shared.catalog.catalogSortFromAlias
import org.akkirrai.hibiki.shared.catalog.toAlias
import org.akkirrai.hibiki.shared.catalog.icon
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.CatalogContentTopPadding
import org.akkirrai.hibiki.shared.design.component.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.AppBottomBarHeight
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

@Composable
fun DesktopCatalogScreen(
    repository: DesktopCatalogRepository,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(repository) { AnimeCatalogPresenter(repository, scope) }
    val state by presenter.state.collectAsState()
    val listState = rememberLazyListState()
    var sortExpanded by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    val selectedSort = catalogSortFromAlias(state.filters.sortAlias)
    val availableSorts = remember(state.filterCatalog?.capabilities) {
        state.filterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }

    LaunchedEffect(Unit) {
        presenter.loadFilterCatalog()
        presenter.search()
    }
    AppCatalogQueryEffect(query = state.query, onQuerySettled = presenter::search)
    AppCatalogPaginationEffect(listState = listState, state = state, onLoadMore = presenter::loadMore)

    Box(modifier = modifier.fillMaxSize()) {
        AppCatalogScreenContent(
            state = state,
            listState = listState,
            topContentPadding = CatalogContentTopPadding,
            bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
            errorTitle = "Catalog error",
            retryLabel = "Retry",
            announcementLabel = "Announcement",
            movieLabel = "Movie",
            libraryStatusByAnimeId = emptyMap(),
            libraryStatusLabel = { "Saved" },
            onAnimeClick = onAnimeClick,
            onItemVisible = {},
            onRetry = presenter::search,
            onLoadMoreRetry = presenter::loadMore,
        )
        AppCatalogTopOverlay(
            query = state.query,
            onQueryChange = presenter::onQueryChange,
            onClear = { presenter.setQuery("") },
            placeholder = "Search anime",
            filterContentDescription = "Filters",
            clearContentDescription = "Clear search",
            onFilterClick = { filterSheetOpen = true },
            showFilterButton = state.filterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true,
            sortModifier = Modifier,
            sortContent = {
                AppCatalogSortControl(
                    sortKey = selectedSort.name,
                    icon = selectedSort.icon(),
                    label = selectedSort.name,
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it },
                    orderContent = { orderModifier ->
                        AppCatalogSortOrderIcon(
                            atEnd = sortExpanded,
                            modifier = orderModifier,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    menuContent = {
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            AppCatalogSortMenuContent(
                                title = "Sort by",
                                sorts = availableSorts,
                                selectedSort = selectedSort,
                                label = { it.name },
                                expanded = sortExpanded,
                                onSortSelected = { sort ->
                                    presenter.updateFilters(
                                        AnimeSearchFilters(sortAlias = sort.toAlias())
                                    )
                                    sortExpanded = false
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

    if (filterSheetOpen) {
        AppCatalogFilterSheet(
            initialFilters = state.filters,
            filterCatalog = state.filterCatalog,
            isFilterCatalogLoading = state.isFilterCatalogLoading,
            onApply = presenter::updateFilters,
            onDismissRequest = { filterSheetOpen = false },
            unavailableLabel = "Unavailable",
            typeTitle = "Type",
            genresTitle = "Genres",
            yearTitle = "Year",
            yearAllLabel = "All years",
            yearFromLabel = "From",
            yearToLabel = "To",
            statusTitle = "Status",
            resetLabel = "Reset",
            applyLabel = "Apply",
            defaultYearRange = 1990..2030,
            optionText = { it.title },
            shape = RoundedCornerShape(24.dp),
        )
    }
}
