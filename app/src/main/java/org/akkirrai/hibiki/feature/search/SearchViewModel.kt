package org.akkirrai.hibiki.feature.search

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.shared.model.SearchUiState
import org.akkirrai.hibiki.core.source.AndroidAnimeCatalogRepository
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.search.SearchPresenter
import org.akkirrai.hibiki.shared.search.SearchScreenState

class SearchViewModel(
    private val repository: AnimeSearchRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val presenter = SearchPresenter()
    val uiState: StateFlow<SearchScreenState> = presenter.state
    private val catalogRepository = AndroidAnimeCatalogRepository(repository)
    private val catalogPresenter = AnimeCatalogPresenter(catalogRepository, viewModelScope, SEARCH_PAGE_SIZE)
    private var searchJob: Job? = null

    init {
        loadFilterCatalog()
        viewModelScope.launch {
            catalogPresenter.state.collect { presenterState ->
                val activeQuery = uiState.value.query.trim()
                if (presenterState.query != activeQuery) return@collect
                val result = when {
                    activeQuery.length < MIN_QUERY_LENGTH -> SearchUiState.Idle
                    presenterState.error != null -> SearchUiState.Error(
                        presenterState.error ?: appString(R.string.error_search_failed),
                    )
                    presenterState.isLoading && presenterState.items.isEmpty() -> SearchUiState.Loading
                    presenterState.items.isEmpty() -> SearchUiState.Empty
                    else -> SearchUiState.Content(
                        items = presenterState.items,
                        canLoadMore = presenterState.canLoadMore,
                        isLoadingMore = presenterState.isLoading,
                    )
                }
                presenter.update { it.copy(result = result) }
            }
        }
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect {
                searchJob?.cancel()
                catalogPresenter.clear()
                presenter.update { state -> state.copy(result = SearchUiState.Idle, filterCatalog = null) }
                loadFilterCatalog()
                if (currentSearchQuery() != null) search()
            }
        }
    }

    fun onQueryChange(value: String) {
        presenter.update { state -> state.copy(query = value) }
        if (value.isBlank() || value.trim().length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            catalogPresenter.clear()
            presenter.update { it.copy(result = SearchUiState.Idle) }
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
            presenter.update { it.copy(result = SearchUiState.Idle) }
            return
        }

        searchJob = viewModelScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            val activeQuery = currentSearchQuery()
            if (activeQuery == null) {
                presenter.update { it.copy(result = SearchUiState.Idle) }
                return@launch
            }

            catalogPresenter.onQueryChange(activeQuery)
        }
    }

    private fun currentSearchQuery(): String? = uiState.value.query.trim()
        .takeIf { it.length >= MIN_QUERY_LENGTH }

    fun loadMore() {
        catalogPresenter.loadMore()
    }

    override fun onCleared() {
        searchJob?.cancel()
        catalogPresenter.close()
        catalogRepository.close()
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
            presenter.update { it.copy(isFilterCatalogLoading = true) }
            val catalog = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    catalogRepository.filterCatalog()
                }
            }.getOrNull()
            presenter.update {
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
