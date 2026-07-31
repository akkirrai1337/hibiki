package org.akkirrai.hibiki.desktop

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
import org.akkirrai.hibiki.shared.home.AppHomeScreen
import org.akkirrai.hibiki.shared.home.AppHomeScreenLabels
import org.akkirrai.hibiki.shared.home.HomePresenter
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

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

    AppHomeScreen(
        state = state,
        listState = androidx.compose.foundation.lazy.rememberLazyListState(),
        bottomContentPadding = 96.dp,
        currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
        libraryStatusByAnimeId = emptyMap(),
        labels = desktopHomeScreenLabels(),
        onQueryChange = ::updateSearchQuery,
        onClearSearch = { updateSearchQuery("") },
        onFilterApply = {},
        onRefresh = ::refresh,
        onLoadMoreSearch = ::loadMoreSearchResults,
        onAnimeClick = onAnimeClick,
        onBrowseCatalog = {},
        onOpenLibrary = {},
        onItemVisible = {},
        modifier = modifier,
    )
}

private fun desktopHomeScreenLabels() = AppHomeScreenLabels(
    searchPlaceholder = "Search anime",
    searchFilters = "Filters",
    searchClear = "Clear search",
    searchLoadMore = "Load more",
    searchEmptyTitle = "No results",
    searchEmptyMessage = "Try another search.",
    resultsCountLabel = { count -> "$count results" },
    continueTitle = "Continue watching",
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
    filterUnavailable = "Filters unavailable",
    typeTitle = "Type",
    genresTitle = "Genres",
    yearTitle = "Release date",
    yearAllLabel = "All years",
    yearFromLabel = "From",
    yearToLabel = "To",
    statusTitle = "Status",
    resetLabel = "Reset",
    applyLabel = "Apply",
    libraryStatusLabel = { category -> category.name },
    optionText = { it.title },
)
