package org.akkirrai.hibiki.home.screen

import org.akkirrai.hibiki.home.ui.*
import org.akkirrai.hibiki.home.*
import org.akkirrai.hibiki.home.state.*

import org.akkirrai.hibiki.catalog.filters.*

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.app.libraryText
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.catalog.model.buildCardMeta
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appSearchResultsCount
import org.akkirrai.hibiki.text.appText

data class HomeActions(
    val onQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onFilterApply: (AnimeSearchFilters) -> Unit,
    val onLoadMoreSearch: () -> Unit,
    val onAnimeClick: (Anime) -> Unit,
    val onBrowseCatalog: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onItemVisible: (Anime) -> Unit,
    val onRetrySearch: () -> Unit = {},
    val onDismissIme: () -> Unit = {},
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    listState: LazyListState,
    bottomContentPadding: Dp,
    currentYear: Int,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    sourceBadgeContent: @Composable (Anime) -> Unit = {},
    isImeVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var searchFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchBackAction = homeSearchBackAction(
        isImeVisible = isImeVisible || searchFieldFocused,
        isSearchActive = state.isSearchActive,
    )
    BackHandler(
        enabled = searchBackAction != HomeSearchBackAction.None,
        onBack = {
            when (searchBackAction) {
                HomeSearchBackAction.DismissIme -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    searchFieldFocused = false
                    actions.onDismissIme()
                }
                HomeSearchBackAction.ClearSearch -> actions.onClearSearch()
                HomeSearchBackAction.None -> Unit
            }
        },
    )
    run {
        var isFilterSheetOpen by remember { mutableStateOf(false) }
        androidx.compose.foundation.layout.Box(modifier = modifier) {
            AppHomeContentSwitcher(
            isSearchActive = state.isSearchActive,
            searchContent = {
                AppHomeSearchResultsZone(
                    state = state.searchResult,
                    topContentPadding = HomeContentTopPadding,
                    bottomContentPadding = bottomContentPadding,
                    onAnimeClick = actions.onAnimeClick,
                    metaText = { anime ->
                        anime.buildCardMeta(
                            appText(AppTextKey.Announcement),
                            appText(AppTextKey.Type),
                        )
                    },
                    onLoadMore = actions.onLoadMoreSearch,
                    onRetrySearch = actions.onRetrySearch,
                    loadMoreLabel = appText(AppTextKey.HomeSearchLoadMore),
                    resultsCountLabel = { count -> appSearchResultsCount(count) },
                    emptyTitle = appText(AppTextKey.HomeSearchEmptyTitle),
                    emptyMessage = appText(AppTextKey.HomeSearchEmptyBody),
                    emptyIcon = Icons.Outlined.SearchOff,
                    libraryStatusByAnimeId = libraryStatusByAnimeId,
                    libraryStatusLabel = { category -> category.libraryText() },
                    onItemVisible = actions.onItemVisible,
                )
            },
            feedContent = {
                AppHomeFeedZone(
                    state = state,
                    listState = listState,
                    topContentPadding = HomeContentTopPadding,
                    bottomContentPadding = bottomContentPadding,
                    continueSectionTitle = appText(AppTextKey.HomeContinueTitle),
                    continueEmptyTitle = appText(AppTextKey.HomeContinueEmptyTitle),
                    continueEmptyMessage = appText(AppTextKey.HomeContinueEmptyBody),
                    continueOpenHint = appText(AppTextKey.HomeContinueOpenHint),
                    recentlyWatchedTitle = appText(AppTextKey.HomeRecentlyWatched),
                    recentlyAddedTitle = appText(AppTextKey.HomeRecentlyAdded),
                    announcementLabel = appText(AppTextKey.Announcement),
                    movieLabel = appText(AppTextKey.Type),
                    personalEmptyTitle = appText(AppTextKey.HomePersonalEmptyTitle),
                    personalEmptyMessage = appText(AppTextKey.HomePersonalEmptyBody),
                    personalEmptyActionLabel = appText(AppTextKey.HomeBrowseCatalog),
                    onAnimeClick = actions.onAnimeClick,
                    onBrowseCatalog = actions.onBrowseCatalog,
                    onOpenLibrary = actions.onOpenLibrary,
                    sourceBadgeContent = sourceBadgeContent,
                )
            },
        )
        AppHomeSearchOverlay(
            query = state.searchQuery,
            onQueryChange = actions.onQueryChange,
            onClear = actions.onClearSearch,
            placeholder = appText(AppTextKey.SearchPlaceholder),
            filterContentDescription = appText(AppTextKey.SearchFilters),
            clearContentDescription = appText(AppTextKey.Back),
            onFilterClick = { isFilterSheetOpen = true },
            showFilterButton = state.searchFilterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true ||
                state.isSearchFilterCatalogLoading,
            scrimHeight = HomeTopSearchScrimHeight,
            onSearchFocusChanged = { searchFieldFocused = it },
            )
        }

        if (isFilterSheetOpen) {
            AppCatalogFilterSheet(
            initialFilters = state.searchFilters,
            filterCatalog = state.searchFilterCatalog,
            isFilterCatalogLoading = state.isSearchFilterCatalogLoading,
            onApply = { filters ->
                actions.onFilterApply(filters)
                isFilterSheetOpen = false
            },
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            )
        }
    }
}
