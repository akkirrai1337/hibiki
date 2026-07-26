package org.akkirrai.hibiki.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

data class SourceSearchSection(
    val source: AnimeSourceDescriptor,
    val items: List<Anime> = emptyList(),
    val error: Throwable? = null,
    val isLoading: Boolean = false,
)

data class SourcesSearchUiState(
    val query: String = "",
    val sections: List<SourceSearchSection> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val filters: AnimeSearchFilters = AnimeSearchFilters(),
    val filterCatalog: org.akkirrai.beakokit.model.AnimeSearchFilterCatalog? = null,
    val isFilterCatalogLoading: Boolean = false,
)

class SourcesSearchViewModel(
    context: Context,
) : ViewModel() {
    private val repository = AnimeSearchRepository(context.applicationContext)
    private val _uiState = MutableStateFlow(SourcesSearchUiState())
    val uiState: StateFlow<SourcesSearchUiState> = _uiState.asStateFlow()
    private val searchSlots = Semaphore(3)
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var loadedFilterSource: SourceId? = null

    fun onQueryChange(value: String) {
        val query = value.trimStart()
        searchJob?.cancel()
        searchGeneration += 1
        _uiState.update {
            it.copy(
                query = query,
                sections = emptyList(),
                isSearching = false,
                hasSearched = false,
            )
        }
        if (query.trim().length < MIN_QUERY_LENGTH && !_uiState.value.filters.hasActiveFilters()) return

        val generation = searchGeneration
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SEARCH_DEBOUNCE_MS)
            search(query, generation)
        }
    }

    fun clearQuery() = onQueryChange("")

    fun applyFilters(filters: AnimeSearchFilters) {
        searchJob?.cancel()
        searchGeneration += 1
        _uiState.update { it.copy(filters = filters) }
        if (_uiState.value.query.trim().length < MIN_QUERY_LENGTH && !filters.hasActiveFilters()) return
        val generation = searchGeneration
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            search(_uiState.value.query, generation)
        }
    }

    fun loadFilterCatalog(sourceId: SourceId) {
        if (loadedFilterSource == sourceId &&
            (_uiState.value.filterCatalog != null || _uiState.value.isFilterCatalogLoading)
        ) return
        loadedFilterSource = sourceId
        _uiState.update { it.copy(filterCatalog = null, isFilterCatalogLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = runCatching { repository.getSearchFilterCatalog(sourceId) }.getOrNull()
            _uiState.update {
                it.copy(
                    filterCatalog = catalog,
                    isFilterCatalogLoading = false,
                )
            }
        }
    }

    fun retry(sourceId: SourceId) {
        val query = _uiState.value.query.trim()
        if (query.length < MIN_QUERY_LENGTH) return
        searchJob?.cancel()
        searchGeneration += 1
        val generation = searchGeneration
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            searchSingleSource(query, sourceId, generation)
        }
    }

    private suspend fun search(query: String, generation: Long) {
        val sources = AnimeSourceRegistry.sources.filterForQuery(query)
        if (generation != searchGeneration) return
        _uiState.update {
            it.copy(
                sections = sources.map { source -> SourceSearchSection(source, isLoading = true) },
                isSearching = true,
                hasSearched = true,
            )
        }

        try {
            coroutineScope {
                sources.map { source ->
                    async {
                        searchSlots.withPermit {
                            val result = runCatching {
                                repository.search(
                                    source.id,
                                    request(query),
                                )
                            }
                            if (generation != searchGeneration) return@withPermit
                            _uiState.update { state ->
                                state.copy(
                                    sections = state.sections.map { section ->
                                        if (section.source.id != source.id) section
                                        else result.fold(
                                            onSuccess = { items -> section.copy(items = items.take(RESULTS_PER_SOURCE), isLoading = false) },
                                            onFailure = { error -> section.copy(error = error, isLoading = false) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } finally {
            if (generation == searchGeneration) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private suspend fun searchSingleSource(query: String, sourceId: SourceId, generation: Long) {
        if (generation != searchGeneration) return
        _uiState.update { state ->
            state.copy(sections = state.sections.map { section ->
                if (section.source.id == sourceId) section.copy(error = null, isLoading = true) else section
            })
        }
        searchSlots.withPermit {
            val result = runCatching {
                repository.search(
                    sourceId,
                request(query),
                )
            }
            if (generation != searchGeneration) return@withPermit
            _uiState.update { state ->
                state.copy(sections = state.sections.map { section ->
                    if (section.source.id != sourceId) section
                    else result.fold(
                        onSuccess = { items -> section.copy(items = items.take(RESULTS_PER_SOURCE), error = null, isLoading = false) },
                        onFailure = { error -> section.copy(error = error, isLoading = false) },
                    )
                })
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        repository.close()
        super.onCleared()
    }

    private fun request(query: String): AnimeSearchRequest {
        val filters = _uiState.value.filters
        return AnimeSearchRequest(
            query = query,
            limit = RESULTS_PER_SOURCE,
            offset = 0,
            typeAliases = listOfNotNull(filters.typeAlias),
            statusAliases = listOfNotNull(filters.statusAlias),
            includedGenreAliases = filters.includedGenreAliases.toList(),
            excludedGenreAliases = filters.excludedGenreAliases.toList(),
            yearFrom = filters.yearFrom,
            yearTo = filters.yearTo,
        )
    }

    private fun List<AnimeSourceDescriptor>.filterForQuery(query: String): List<AnimeSourceDescriptor> {
        if (!query.any { it in '\u0400'..'\u052F' }) return this
        return filter { it.language == SourceLanguage.RUSSIAN }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SourcesSearchViewModel(context.applicationContext) as T
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_DEBOUNCE_MS = 400L
        const val RESULTS_PER_SOURCE = 12
    }
}
