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
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.SourceSearchSectionState
import org.akkirrai.hibiki.shared.source.SOURCE_SEARCH_MIN_QUERY_LENGTH
import org.akkirrai.hibiki.shared.source.SOURCE_SEARCH_DEBOUNCE_MS
import org.akkirrai.hibiki.shared.source.SOURCE_SEARCH_RESULTS_PER_SOURCE
import org.akkirrai.hibiki.shared.source.SourcesSearchUiState
import org.akkirrai.hibiki.shared.source.shouldRestrictSourceSearchToRussian

typealias SourceSearchSection = SourceSearchSectionState<Anime>

class SourcesSearchViewModel(
    context: Context,
) : ViewModel() {
    private val repository = AnimeSearchRepository(context.applicationContext)
    private val _uiState = MutableStateFlow(SourcesSearchUiState())
    val uiState: StateFlow<SourcesSearchUiState> = _uiState.asStateFlow()
    private val searchSlots = Semaphore(3)
    private var searchJob: Job? = null
    private var searchGeneration = 0L

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
        if (query.trim().length < SOURCE_SEARCH_MIN_QUERY_LENGTH) return

        val generation = searchGeneration
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SOURCE_SEARCH_DEBOUNCE_MS)
            search(query, generation)
        }
    }

    fun clearQuery() = onQueryChange("")

    fun retry(sourceId: SourceId) {
        val query = _uiState.value.query.trim()
        if (query.length < SOURCE_SEARCH_MIN_QUERY_LENGTH) return
        searchJob?.cancel()
        searchGeneration += 1
        val generation = searchGeneration
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            searchSingleSource(query, sourceId, generation)
        }
    }

    private suspend fun search(query: String, generation: Long) {
        val sources = sourcesForQuery(query)
        if (generation != searchGeneration) return
        _uiState.update {
            it.copy(
                sections = sources.map { source ->
                    SourceSearchSection(
                        sourceId = source.id.value,
                        sourceName = source.name,
                        isLoading = true,
                    )
                },
                isSearching = true,
                hasSearched = true,
            )
        }

        try {
            coroutineScope {
                sources.map { source ->
                    async {
                        searchSlots.withPermit {
                            AppLogger.d(
                                TAG,
                                "source search started source=${source.name} querySearch=true",
                            )
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
                                        if (section.sourceId != source.id.value) section
                                        else result.fold(
                        onSuccess = { items -> section.copy(items = items.take(SOURCE_SEARCH_RESULTS_PER_SOURCE), isLoading = false) },
                                            onFailure = { section.copy(hasError = true, isLoading = false) },
                                        )
                                    },
                                )
                            }
                            result.onSuccess { items ->
                                AppLogger.d(TAG, "source search finished source=${source.name} resultCount=${items.size}")
                            }.onFailure { error ->
                                AppLogger.w(TAG, "source search failed source=${source.name}", error)
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
                if (section.sourceId == sourceId.value) section.copy(hasError = false, isLoading = true) else section
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
                    if (section.sourceId != sourceId.value) section
                    else result.fold(
                        onSuccess = { items -> section.copy(items = items.take(SOURCE_SEARCH_RESULTS_PER_SOURCE), hasError = false, isLoading = false) },
                        onFailure = { section.copy(hasError = true, isLoading = false) },
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

    private fun request(query: String): AnimeSearchRequest = AnimeSearchRequest(
            query = query,
            limit = SOURCE_SEARCH_RESULTS_PER_SOURCE,
            offset = 0,
        )

    private fun sourcesForQuery(query: String): List<AnimeSourceDescriptor> =
        AnimeSourceRegistry.sources.filterForQuery(query).also { sources ->
            AppLogger.d(TAG, "source search candidates=${sources.joinToString { it.name }}")
        }

    private fun List<AnimeSourceDescriptor>.filterForQuery(query: String): List<AnimeSourceDescriptor> {
        if (!shouldRestrictSourceSearchToRussian(query)) return this
        return filter { it.language == SourceLanguage.RUSSIAN }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SourcesSearchViewModel(context.applicationContext) as T
    }

    private companion object {
        const val TAG = "SourcesSearch"
    }
}
