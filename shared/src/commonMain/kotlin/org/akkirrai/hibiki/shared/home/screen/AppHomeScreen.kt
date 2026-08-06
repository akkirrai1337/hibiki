package org.akkirrai.hibiki.shared.home.screen

import org.akkirrai.hibiki.shared.home.ui.*
import org.akkirrai.hibiki.shared.home.*
import org.akkirrai.hibiki.shared.home.state.*

import org.akkirrai.hibiki.shared.catalog.filters.*

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.catalog.filters.AppCatalogFilterSheet
import org.akkirrai.hibiki.shared.catalog.filters.defaultCatalogFilterYearRange
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.catalog.model.buildCardMeta

data class AppHomeScreenLabels(
    val searchPlaceholder: String,
    val searchFilters: String,
    val searchClear: String,
    val searchLoadMore: String,
    val searchEmptyTitle: String,
    val searchEmptyMessage: String,
    val resultsCountLabel: @Composable (Int) -> String,
    val continueTitle: String,
    val continueEmptyTitle: String,
    val continueEmptyMessage: String,
    val continueOpenHint: String,
    val recentlyWatchedTitle: String,
    val recentlyAddedTitle: String,
    val announcementLabel: String,
    val movieLabel: String,
    val personalEmptyTitle: String,
    val personalEmptyMessage: String,
    val personalEmptyActionLabel: String,
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
fun AppHomeScreen(
    state: HomeUiState,
    listState: LazyListState,
    bottomContentPadding: Dp,
    currentYear: Int,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    labels: AppHomeScreenLabels,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterApply: (AnimeSearchFilters) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onRetrySearch: () -> Unit = {},
    onAnimeClick: (Anime) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    sourceBadgeContent: @Composable (Anime) -> Unit = {},
    onItemVisible: (Anime) -> Unit,
    isImeVisible: Boolean = false,
    onDismissIme: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val searchBackAction = homeSearchBackAction(isImeVisible, state.isSearchActive)
    AppSystemBackHandler(
        enabled = searchBackAction != HomeSearchBackAction.None,
        onBack = {
            when (searchBackAction) {
                HomeSearchBackAction.DismissIme -> onDismissIme()
                HomeSearchBackAction.ClearSearch -> onClearSearch()
                HomeSearchBackAction.None -> Unit
            }
        },
    ) {
        var isFilterSheetOpen by remember { mutableStateOf(false) }
        val pullToRefreshState = rememberPullToRefreshState()

        androidx.compose.foundation.layout.Box(modifier = modifier) {
            AppHomeContentSwitcher(
            isSearchActive = state.isSearchActive,
            searchContent = {
                AppHomeSearchResultsZone(
                    state = state.searchResult,
                    topContentPadding = HomeContentTopPadding,
                    bottomContentPadding = bottomContentPadding,
                    onAnimeClick = onAnimeClick,
                    metaText = { anime ->
                        anime.buildCardMeta(
                            labels.announcementLabel,
                            labels.movieLabel,
                        )
                    },
                    onLoadMore = onLoadMoreSearch,
                    onRetrySearch = onRetrySearch,
                    loadMoreLabel = labels.searchLoadMore,
                    resultsCountLabel = labels.resultsCountLabel,
                    emptyTitle = labels.searchEmptyTitle,
                    emptyMessage = labels.searchEmptyMessage,
                    emptyIcon = Icons.Outlined.SearchOff,
                    libraryStatusByAnimeId = libraryStatusByAnimeId,
                    libraryStatusLabel = labels.libraryStatusLabel,
                    onItemVisible = onItemVisible,
                )
            },
            feedContent = {
                AppHomeFeedZone(
                    state = state,
                    listState = listState,
                    pullToRefreshState = pullToRefreshState,
                    topContentPadding = HomeContentTopPadding,
                    bottomContentPadding = bottomContentPadding,
                    indicatorTopPadding = HomePullRefreshIndicatorTopOffset,
                    continueSectionTitle = labels.continueTitle,
                    continueEmptyTitle = labels.continueEmptyTitle,
                    continueEmptyMessage = labels.continueEmptyMessage,
                    continueOpenHint = labels.continueOpenHint,
                    recentlyWatchedTitle = labels.recentlyWatchedTitle,
                    recentlyAddedTitle = labels.recentlyAddedTitle,
                    announcementLabel = labels.announcementLabel,
                    movieLabel = labels.movieLabel,
                    personalEmptyTitle = labels.personalEmptyTitle,
                    personalEmptyMessage = labels.personalEmptyMessage,
                    personalEmptyActionLabel = labels.personalEmptyActionLabel,
                    onRefresh = onRefresh,
                    onAnimeClick = onAnimeClick,
                    onBrowseCatalog = onBrowseCatalog,
                    onOpenLibrary = onOpenLibrary,
                    sourceBadgeContent = sourceBadgeContent,
                )
            },
        )
        AppHomeSearchOverlay(
            query = state.searchQuery,
            onQueryChange = onQueryChange,
            onClear = onClearSearch,
            placeholder = labels.searchPlaceholder,
            filterContentDescription = labels.searchFilters,
            clearContentDescription = labels.searchClear,
            onFilterClick = { isFilterSheetOpen = true },
            showFilterButton = state.searchFilterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true ||
                state.isSearchFilterCatalogLoading,
            scrimHeight = HomeTopSearchScrimHeight,
            )
        }

        if (isFilterSheetOpen) {
            AppCatalogFilterSheet(
            initialFilters = state.searchFilters,
            filterCatalog = state.searchFilterCatalog,
            isFilterCatalogLoading = state.isSearchFilterCatalogLoading,
            onApply = { filters ->
                onFilterApply(filters)
                isFilterSheetOpen = false
            },
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            )
        }
    }
}
