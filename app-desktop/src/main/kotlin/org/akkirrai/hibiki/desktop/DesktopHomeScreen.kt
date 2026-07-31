package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.design.component.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.AppBottomBarHeight
import org.akkirrai.hibiki.shared.home.AppHomeContentSwitcher
import org.akkirrai.hibiki.shared.home.AppHomeFeedZone
import org.akkirrai.hibiki.shared.home.AppHomeSearchOverlay
import org.akkirrai.hibiki.shared.home.AppHomeSearchResultsZone
import org.akkirrai.hibiki.shared.home.HomeSearchEmptyIcon
import org.akkirrai.hibiki.shared.home.HomePresenter
import org.akkirrai.hibiki.shared.home.HomePullRefreshIndicatorTopOffset
import org.akkirrai.hibiki.shared.home.HomeContentTopPadding
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopHomeScreen(
    repository: DesktopHomeRepository,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(repository) {
        HomePresenter(initialState = repository.fallbackHomeState().copy(isLoading = true))
    }
    val state by presenter.state.collectAsState()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        scope.launch {
            runCatching { repository.refreshHomeState() }
                .onSuccess(presenter::setState)
                .onFailure { error ->
                    presenter.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Home loading failed")
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        presenter.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            presenter.update { it.copy(searchResult = SearchUiState.Idle) }
            return
        }
        searchJob = scope.launch {
            presenter.update { it.copy(searchResult = SearchUiState.Loading) }
            delay(350)
            runCatching {
                repository.search(query, AnimeSearchFilters(), limit = 20, offset = 0)
            }.onSuccess { items ->
                presenter.update {
                    it.copy(
                        searchResult = if (items.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Content(items = items, canLoadMore = items.size == 20)
                        },
                    )
                }
            }.onFailure { error ->
                presenter.update {
                    it.copy(searchResult = SearchUiState.Error(error.message ?: "Search failed"))
                }
            }
        }
    }

    fun loadMoreSearchResults() {
        val current = presenter.state.value.searchResult as? SearchUiState.Content ?: return
        if (!current.canLoadMore || current.isLoadingMore) return
        presenter.update { it.copy(searchResult = current.copy(isLoadingMore = true)) }
        searchJob = scope.launch {
            runCatching {
                repository.search(
                    presenter.state.value.searchQuery,
                    AnimeSearchFilters(),
                    limit = 20,
                    offset = current.items.size,
                )
            }.onSuccess { items ->
                presenter.update {
                    it.copy(
                        searchResult = current.copy(
                            items = current.items + items,
                            canLoadMore = items.size == 20,
                            isLoadingMore = false,
                        ),
                    )
                }
            }.onFailure { error ->
                presenter.update {
                    it.copy(searchResult = current.copy(isLoadingMore = false, loadMoreError = error.message))
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        AppHomeContentSwitcher(
            isSearchActive = state.searchQuery.isNotBlank(),
            modifier = Modifier,
            searchContent = {
                AppHomeSearchResultsZone(
                    state = state.searchResult,
                    topContentPadding = HomeContentTopPadding,
                    bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
                    onAnimeClick = onAnimeClick,
                    metaText = { anime -> anime.title },
                    onLoadMore = ::loadMoreSearchResults,
                    loadMoreLabel = "Load more",
                    resultsCountLabel = { count -> "$count results" },
                    emptyTitle = "No results",
                    emptyMessage = "Try another search.",
                    emptyIcon = HomeSearchEmptyIcon,
                    libraryStatusByAnimeId = emptyMap(),
                    libraryStatusLabel = { "Saved" },
                    onItemVisible = {},
                    modifier = Modifier,
                )
            },
            feedContent = {
                AppHomeFeedZone(
                    state = state,
                    listState = listState,
                    pullToRefreshState = pullToRefreshState,
                    topContentPadding = HomeContentTopPadding,
                    bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
                    indicatorTopPadding = HomePullRefreshIndicatorTopOffset,
                    continueSectionTitle = "Continue watching",
                    continueEmptyTitle = "Nothing to continue",
                    continueEmptyMessage = "Start watching anime to see it here.",
                    continueOpenHint = "Open",
                    recentlyWatchedTitle = "Recently watched",
                    recentlyAddedTitle = "Recently added",
                    announcementLabel = "Announcement",
                    movieLabel = "Movie",
                    personalEmptyTitle = "Your home is empty",
                    personalEmptyMessage = "Browse the catalog to start building your library.",
                    personalEmptyActionLabel = "Browse catalog",
                    onRefresh = ::refresh,
                    onAnimeClick = onAnimeClick,
                    onBrowseCatalog = {},
                    onOpenLibrary = {},
                    sourceBadgeContent = {},
                    modifier = Modifier,
                )
            },
        )
        AppHomeSearchOverlay(
            query = state.searchQuery,
            onQueryChange = ::updateSearchQuery,
            onClear = { updateSearchQuery("") },
            placeholder = "Search anime",
            filterContentDescription = "Filters",
            clearContentDescription = "Clear search",
            onFilterClick = {},
            showFilterButton = false,
            scrimHeight = org.akkirrai.hibiki.shared.home.HomeTopSearchScrimHeight,
        )
    }
}
