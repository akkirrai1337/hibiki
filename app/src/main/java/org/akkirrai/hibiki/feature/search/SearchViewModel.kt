package org.akkirrai.hibiki.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.source.toSearchErrorMessage
import org.akkirrai.hibiki.feature.home.HomeRepository

/** The app's single search: query, filters and paged results for the active source. */
class SearchViewModel(
    private val repository: HomeRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val _uiState = MutableStateFlow(SearchScreenState())
    val uiState: StateFlow<SearchScreenState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var filterCatalogJob: Job? = null

    init {
        loadFilterCatalog()
        viewModelScope.launch {
            repository.cardMetadata.collect { metadata ->
                _uiState.update { it.copy(result = it.result.withCardMetadata(metadata)) }
            }
        }
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect {
                resetForNewSource()
            }
        }
        viewModelScope.launch {
            appPreferences.state.map { it.languageMode }.distinctUntilChanged().drop(1).collect {
                resetForNewSource()
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        if (!canSearch(_uiState.value)) {
            cancelSearch()
            _uiState.update { it.copy(result = SearchUiState.Idle) }
            return
        }
        scheduleSearch(immediate = false)
    }

    /** Text handed over from another screen's search bar: search right away, no debounce. */
    fun startSearch(query: String) {
        _uiState.update { it.copy(query = query) }
        scheduleSearch(immediate = true)
    }

    fun retry() = scheduleSearch(immediate = true)

    fun clear() {
        cancelSearch()
        _uiState.update { it.copy(query = "", result = SearchUiState.Idle) }
        if (_uiState.value.filters.hasActiveFilters()) scheduleSearch(immediate = true)
    }

    fun applyFilters(filters: AnimeSearchFilters) {
        _uiState.update { it.copy(filters = filters) }
        scheduleSearch(immediate = true)
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
    }

    private fun resetForNewSource() {
        cancelSearch()
        _uiState.update {
            it.copy(filters = AnimeSearchFilters(), filterCatalog = null, result = SearchUiState.Idle)
        }
        loadFilterCatalog()
        if (canSearch(_uiState.value)) scheduleSearch(immediate = true)
    }

    private fun canSearch(state: SearchScreenState): Boolean =
        state.query.trim().length >= MIN_QUERY_LENGTH || state.filters.hasActiveFilters()

    private fun scheduleSearch(immediate: Boolean) {
        cancelSearch()
        if (!canSearch(_uiState.value)) {
            _uiState.update { it.copy(result = SearchUiState.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            val snapshot = _uiState.value
            if (!canSearch(snapshot)) {
                _uiState.update { it.copy(result = SearchUiState.Idle) }
                return@launch
            }
            _uiState.update { it.copy(result = SearchUiState.Loading) }
            loadFirstPage(snapshot.query.trim(), snapshot.filters)
        }
    }

    private suspend fun loadFirstPage(query: String, filters: AnimeSearchFilters) {
        // Only the query length is logged - the text itself is what the user typed.
        val startedAt = System.currentTimeMillis()
        AppLogger.d(LOG_TAG, "search start queryLength=${query.length} filtered=${filters.hasActiveFilters()}")
        try {
            val items = withContext(Dispatchers.IO) {
                repository.search(query, filters, limit = PAGE_SIZE + 1, offset = 0)
            }
            AppLogger.d(LOG_TAG, "search ok items=${items.size} in ${System.currentTimeMillis() - startedAt}ms")
            if (isStale(query, filters)) return
            val result = if (items.isEmpty()) {
                SearchUiState.Empty
            } else {
                SearchUiState.Content(items = items.take(PAGE_SIZE), canLoadMore = items.size > PAGE_SIZE)
            }
            _uiState.update { it.copy(result = result.withCardMetadata(repository.cardMetadata.value)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            AppLogger.w(LOG_TAG, "search failed in ${System.currentTimeMillis() - startedAt}ms", throwable)
            if (isStale(query, filters)) return
            _uiState.update { it.copy(result = SearchUiState.Error(throwable.toSearchErrorMessage(appContext))) }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val content = state.result as? SearchUiState.Content ?: return
        if (content.isLoadingMore || !content.canLoadMore || !canSearch(state)) return
        val query = state.query.trim()
        val filters = state.filters

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            updateContent { it.copy(isLoadingMore = true, loadMoreError = null) }
            try {
                val next = withContext(Dispatchers.IO) {
                    repository.search(query, filters, limit = PAGE_SIZE + 1, offset = content.items.size)
                }
                if (isStale(query, filters)) return@launch
                updateContent {
                    it.copy(
                        items = (it.items + next.take(PAGE_SIZE)).distinctBy(Anime::id)
                            .withCardMetadata(repository.cardMetadata.value),
                        canLoadMore = next.size > PAGE_SIZE,
                        isLoadingMore = false,
                        loadMoreError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                val message = throwable.toSearchErrorMessage(appContext)
                updateContent { it.copy(isLoadingMore = false, loadMoreError = message) }
            }
        }
    }

    private fun isStale(query: String, filters: AnimeSearchFilters): Boolean =
        query != _uiState.value.query.trim() || filters != _uiState.value.filters

    private fun updateContent(transform: (SearchUiState.Content) -> SearchUiState.Content) {
        _uiState.update { state ->
            val current = state.result as? SearchUiState.Content ?: return@update state
            state.copy(result = transform(current))
        }
    }

    private fun loadFilterCatalog() {
        filterCatalogJob?.cancel()
        filterCatalogJob = viewModelScope.launch {
            _uiState.update { it.copy(isFilterCatalogLoading = true) }
            val catalog = runCatching {
                withContext(Dispatchers.IO) { repository.getSearchFilterCatalog() }
            }.getOrNull()
            _uiState.update {
                it.copy(filterCatalog = catalog ?: it.filterCatalog, isFilterCatalogLoading = false)
            }
        }
    }

    private fun List<Anime>.withCardMetadata(metadata: Map<String, Anime>): List<Anime> = map { anime ->
        metadata[anime.id]?.copy(title = anime.title) ?: anime
    }

    private fun SearchUiState.withCardMetadata(metadata: Map<String, Anime>): SearchUiState = when (this) {
        is SearchUiState.Content -> copy(items = items.withCardMetadata(metadata))
        else -> this
    }

    override fun onCleared() {
        cancelSearch()
        filterCatalogJob?.cancel()
        repository.close()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            return SearchViewModel(
                repository = appContext.hibikiDependencies().homeRepository(),
                context = appContext,
            ) as T
        }
    }

    private companion object {
        const val LOG_TAG = "Search"
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 3
        const val PAGE_SIZE = 24
    }
}
