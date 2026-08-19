package org.akkirrai.hibiki.home.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.home.state.mergeAnimePreservingOrder
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.search.model.SearchUiState

data class HomeSearchUiState(
    val query: String = "",
    val filters: AnimeSearchFilters = AnimeSearchFilters(),
    val result: SearchUiState = SearchUiState.Idle,
    val page: Int = 1,
    val filterCatalog: AnimeCatalogFilterCatalog? = null,
    val isFilterCatalogLoading: Boolean = false,
)

/** Home-specific search state, independent from the top-level Catalog presenter. */
class HomeSearchPresenter(
    private val repository: AnimeCatalogRepository,
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val _state = MutableStateFlow(HomeSearchUiState())
    val state: StateFlow<HomeSearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var filterCatalogJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch(immediate = false)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(query = "", result = SearchUiState.Idle) }
    }

    fun applyFilters(filters: AnimeSearchFilters) {
        _state.update { it.copy(filters = filters) }
        scheduleSearch(immediate = true)
    }

    fun resetForSource() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                result = SearchUiState.Idle,
                filters = AnimeSearchFilters(),
                filterCatalog = null,
            )
        }
        loadFilterCatalog()
    }

    fun loadFilterCatalog() {
        filterCatalogJob?.cancel()
        filterCatalogJob = scope.launch {
            _state.update { it.copy(isFilterCatalogLoading = true) }
            try {
                val catalog = repository.filterCatalog()
                _state.update { it.copy(filterCatalog = catalog, isFilterCatalogLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.update { it.copy(isFilterCatalogLoading = false) }
            }
        }
    }

    fun loadMore() {
        val content = state.value.result as? SearchUiState.Content ?: return
        if (!content.canLoadMore || content.isLoadingMore) return
        val current = state.value
        searchJob?.cancel()
        searchJob = scope.launch {
            _state.update {
                it.copy(result = content.copy(isLoadingMore = true, loadMoreError = null))
            }
            try {
                val page = repository.search(
                    AnimeCatalogQuery(
                        text = current.query.trim(),
                        page = current.page + 1,
                        pageSize = pageSize,
                        filters = current.filters,
                    ),
                )
                _state.update { state ->
                    val currentContent = state.result as? SearchUiState.Content ?: return@update state
                    state.copy(
                        page = page.page,
                        result = currentContent.copy(
                            items = mergeAnimePreservingOrder(currentContent.items, page.items),
                            canLoadMore = page.canLoadMore,
                            isLoadingMore = false,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update { state ->
                    val currentContent = state.result as? SearchUiState.Content ?: return@update state
                    state.copy(
                        result = currentContent.copy(
                            isLoadingMore = false,
                            loadMoreError = throwable.message ?: "Search failed",
                        ),
                    )
                }
            }
        }
    }

    fun retrySearch() {
        scheduleSearch(immediate = true)
    }

    fun close() {
        searchJob?.cancel()
        filterCatalogJob?.cancel()
    }

    private fun scheduleSearch(immediate: Boolean) {
        searchJob?.cancel()
        val current = state.value
        val query = current.query.trim()
        if (query.length < MIN_QUERY_LENGTH && !current.filters.hasActiveFilters()) {
            _state.update { it.copy(result = SearchUiState.Idle) }
            return
        }
        searchJob = scope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            search()
        }
    }

    private suspend fun search() {
        val current = state.value
        _state.update { it.copy(result = SearchUiState.Loading) }
        try {
            val page = repository.search(
                AnimeCatalogQuery(
                    text = current.query.trim(),
                    page = 1,
                    pageSize = pageSize,
                    filters = current.filters,
                ),
            )
            _state.update {
                it.copy(
                    page = 1,
                    result = if (page.items.isEmpty()) {
                        SearchUiState.Empty
                    } else {
                        SearchUiState.Content(page.items, page.canLoadMore)
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            _state.update { it.copy(result = SearchUiState.Error(throwable.message ?: "Search failed")) }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 3
        const val DEFAULT_PAGE_SIZE = 24
    }
}
