package org.akkirrai.hibiki.feature.search

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
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.source.AnimeSearchRepository

class SearchViewModel(
    private val repository: AnimeSearchRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(SearchScreenState())
    val uiState: StateFlow<SearchScreenState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadFilterCatalog()
    }

    fun onQueryChange(value: String) {
        _uiState.update { state -> state.copy(query = value) }
        if (value.isBlank() || value.trim().length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            _uiState.update { it.copy(result = SearchUiState.Idle) }
            return
        }
        scheduleSearch(immediate = false)
    }

    fun search() {
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean) {
        searchJob?.cancel()
        if (currentSearchQuery() == null) {
            _uiState.update { it.copy(result = SearchUiState.Idle) }
            return
        }

        searchJob = viewModelScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            val activeQuery = currentSearchQuery()
            if (activeQuery == null) {
                _uiState.update { it.copy(result = SearchUiState.Idle) }
                return@launch
            }

            loadMoreJob?.cancel()
            _uiState.update { it.copy(result = SearchUiState.Loading) }
            loadFirstSearchPage(activeQuery)
        }
    }

    private suspend fun loadFirstSearchPage(activeQuery: String) {
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.search(
                    query = activeQuery,
                    limit = SEARCH_PAGE_SIZE,
                    offset = 0,
                )
            }
            if (activeQuery != uiState.value.query.trim()) return
            val result = if (items.isEmpty()) {
                SearchUiState.Empty
            } else {
                SearchUiState.Content(
                    items = items,
                    canLoadMore = items.size >= SEARCH_PAGE_SIZE,
                )
            }
            _uiState.update { it.copy(result = result) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            if (activeQuery != uiState.value.query.trim()) return
            val message = when (throwable) {
                is SourceException -> throwable.message ?: appString(R.string.error_source_generic)
                else -> throwable.message ?: appString(R.string.error_search_failed)
            }
            _uiState.update { it.copy(result = SearchUiState.Error(message)) }
        }
    }

    private fun currentSearchQuery(): String? = uiState.value.query.trim()
        .takeIf { it.length >= MIN_QUERY_LENGTH }

    fun loadMore() {
        val query = uiState.value.query.trim()
        val content = uiState.value.result as? SearchUiState.Content ?: return
        if (query.isBlank() || content.isLoadingMore || !content.canLoadMore) return

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { state ->
                val current = state.result as? SearchUiState.Content ?: return@update state
                state.copy(result = current.copy(isLoadingMore = true))
            }

            try {
                val nextItems = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.search(
                        query = query,
                        limit = SEARCH_PAGE_SIZE,
                        offset = content.items.size,
                    )
                }
                if (query != uiState.value.query.trim()) return@launch
                _uiState.update { state ->
                    val current = state.result as? SearchUiState.Content ?: return@update state
                    val merged = (current.items + nextItems).distinctBy { it.id }
                    state.copy(
                        result = current.copy(
                            items = merged,
                            canLoadMore = nextItems.size >= SEARCH_PAGE_SIZE,
                            isLoadingMore = false,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { state ->
                    val current = state.result as? SearchUiState.Content ?: return@update state
                    state.copy(result = current.copy(isLoadingMore = false))
                }
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        repository.close()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return SearchViewModel(
                repository = dependencies.animeSearchRepository(),
                context = context.applicationContext,
            ) as T
        }
    }

    private fun appString(@StringRes resId: Int): String = appContext.getString(resId)

    private fun loadFilterCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterCatalogLoading = true) }
            val catalog = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.getSearchFilterCatalog()
                }
            }.getOrNull()
            _uiState.update {
                it.copy(
                    filterCatalog = catalog ?: it.filterCatalog,
                    isFilterCatalogLoading = false,
                )
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_PAGE_SIZE = 20
    }
}
