package org.akkirrai.hibiki.feature.home

import android.content.Context
import androidx.annotation.StringRes
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.model.SearchUiState

class HomeViewModel(
    private val repository: HomeRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(
        HomeUiState(isLoading = true)
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
        loadSearchFilterCatalog()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.refreshHomeState() }
                .onSuccess { state ->
                    val current = _uiState.value
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: appString(R.string.home_error_refresh_failed),
                        )
                    }
                }
        }
    }

    private var searchJob: Job? = null

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        if (value.isBlank() || value.trim().length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            _uiState.update { it.copy(searchResult = SearchUiState.Idle) }
            return
        }
        scheduleSearch(immediate = false)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResult = SearchUiState.Idle) }
    }

    fun applySearchFilters(filters: HomeSearchFilters) {
        _uiState.update { it.copy(searchFilters = filters) }
        val query = uiState.value.searchQuery.trim()
        if (query.length >= MIN_QUERY_LENGTH || filters.hasActiveFilters()) {
            scheduleSearch(immediate = true, allowFilterOnly = true)
        } else {
            searchJob?.cancel()
            _uiState.update { it.copy(searchResult = SearchUiState.Idle) }
        }
    }

    fun resetSearchFilters() {
        applySearchFilters(HomeSearchFilters())
    }

    private fun scheduleSearch(
        immediate: Boolean,
        allowFilterOnly: Boolean = false,
    ) {
        searchJob?.cancel()
        val query = uiState.value.searchQuery.trim()
        val canSearchByFilters = allowFilterOnly && uiState.value.searchFilters.hasActiveFilters()
        if (query.length < MIN_QUERY_LENGTH && !canSearchByFilters) {
            _uiState.update { it.copy(searchResult = SearchUiState.Idle) }
            return
        }

        searchJob = viewModelScope.launch {
            if (!immediate) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            val activeQuery = uiState.value.searchQuery.trim()
            val activeFilters = uiState.value.searchFilters
            if (activeQuery.length < MIN_QUERY_LENGTH && !activeFilters.hasActiveFilters()) {
                _uiState.update { it.copy(searchResult = SearchUiState.Idle) }
                return@launch
            }

            _uiState.update { it.copy(searchResult = SearchUiState.Loading) }

            try {
                val filters = activeFilters
                val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.search(
                        query = activeQuery,
                        filters = filters,
                        limit = SEARCH_PAGE_SIZE + 1,
                        offset = 0,
                    )
                }
                if (activeQuery != uiState.value.searchQuery.trim()) return@launch
                _uiState.update {
                    it.copy(
                        searchResult = if (items.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Content(
                                items = items.take(SEARCH_PAGE_SIZE),
                                canLoadMore = items.size > SEARCH_PAGE_SIZE,
                            )
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (activeQuery != uiState.value.searchQuery.trim()) return@launch
                val message = when (throwable) {
                    is SourceException -> throwable.message ?: appString(R.string.error_source_generic)
                    else -> throwable.message ?: appString(R.string.error_search_failed)
                }
                _uiState.update { it.copy(searchResult = SearchUiState.Error(message)) }
            }
        }
    }

    fun loadMoreSearchResults() {
        val content = uiState.value.searchResult as? SearchUiState.Content ?: return
        if (!content.canLoadMore || content.isLoadingMore) return

        val query = uiState.value.searchQuery.trim()
        val filters = uiState.value.searchFilters
        if (query.length < MIN_QUERY_LENGTH && !filters.hasActiveFilters()) return
        val offset = content.items.size

        viewModelScope.launch {
            _uiState.update { state ->
                val current = state.searchResult as? SearchUiState.Content ?: return@update state
                state.copy(
                    searchResult = current.copy(
                        isLoadingMore = true,
                        loadMoreError = null,
                    )
                )
            }

            try {
                val nextItems = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.search(
                        query = query,
                        filters = filters,
                        limit = SEARCH_PAGE_SIZE + 1,
                        offset = offset,
                    )
                }
                if (query != uiState.value.searchQuery.trim() ||
                    filters != uiState.value.searchFilters
                ) {
                    return@launch
                }
                _uiState.update { state ->
                    val current = state.searchResult as? SearchUiState.Content
                        ?: return@update state
                    state.copy(
                        searchResult = current.copy(
                            items = (
                                current.items + nextItems.take(SEARCH_PAGE_SIZE)
                            ).distinctBy { it.id },
                            canLoadMore = nextItems.size > SEARCH_PAGE_SIZE,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                val message = when (throwable) {
                    is SourceException ->
                        throwable.message ?: appString(R.string.error_source_generic)
                    else -> throwable.message ?: appString(R.string.error_search_failed)
                }
                _uiState.update { state ->
                    val current = state.searchResult as? SearchUiState.Content
                        ?: return@update state
                    state.copy(
                        searchResult = current.copy(
                            isLoadingMore = false,
                            loadMoreError = message,
                        )
                    )
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadHomeState() }
                .onSuccess { state ->
                    val current = _uiState.value
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: appString(R.string.home_error_load_failed),
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        repository.close()
        super.onCleared()
    }

    private fun loadSearchFilterCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchFilterCatalogLoading = true) }
            val catalog = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.getSearchFilterCatalog()
                }
            }.getOrNull()
            _uiState.update {
                it.copy(
                    searchFilterCatalog = catalog ?: it.searchFilterCatalog,
                    isSearchFilterCatalogLoading = false,
                )
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_PAGE_SIZE = 24
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                repository = HomeRepository(context = context),
                context = context.applicationContext,
            ) as T
        }
    }

    private fun appString(@StringRes resId: Int): String = appContext.getString(resId)
}
