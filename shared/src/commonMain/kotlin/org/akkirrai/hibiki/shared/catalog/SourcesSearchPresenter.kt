package org.akkirrai.hibiki.shared.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.SOURCE_SEARCH_DEBOUNCE_MS
import org.akkirrai.hibiki.shared.source.SOURCE_SEARCH_MIN_QUERY_LENGTH
import org.akkirrai.hibiki.shared.source.SOURCE_SEARCH_RESULTS_PER_SOURCE
import org.akkirrai.hibiki.shared.source.SourceSearchSectionState
import org.akkirrai.hibiki.shared.source.SourcesSearchUiState
import org.akkirrai.hibiki.shared.source.shouldRestrictSourceSearchToRussian

/** Shared source search presenter. Results stay grouped by their originating source. */
class SourcesSearchPresenter(
    private val repository: AnimeCatalogRepository,
    sources: List<AppSourceDescriptor>,
    private val scope: CoroutineScope,
) {
    private val searchableSources = sources.filter(AppSourceDescriptor::supportsSearch)
    private val _state = MutableStateFlow(SourcesSearchUiState())
    val state: StateFlow<SourcesSearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        val normalizedQuery = query.trimStart()
        _state.update { it.copy(query = normalizedQuery) }
        searchJob?.cancel()
        if (normalizedQuery.trim().length < SOURCE_SEARCH_MIN_QUERY_LENGTH) {
            _state.update { it.copy(sections = emptyList(), isSearching = false, hasSearched = false) }
            return
        }
        searchJob = scope.launch {
            delay(SOURCE_SEARCH_DEBOUNCE_MS)
            search()
        }
    }

    fun clear() {
        searchJob?.cancel()
        _state.value = SourcesSearchUiState()
    }

    fun search() {
        searchJob?.cancel()
        val query = state.value.query.trim()
        if (query.length < SOURCE_SEARCH_MIN_QUERY_LENGTH) return
        searchJob = scope.launch {
            val sources = sourcesForQuery(query)
            val loadingSections = sources.map { source ->
                SourceSearchSectionState<Anime>(
                    sourceId = source.id,
                    sourceName = source.name,
                    isLoading = true,
                )
            }
            _state.update { it.copy(sections = loadingSections, isSearching = true, hasSearched = true) }
            val results = supervisorScope {
                sources.map { source ->
                    async {
                        runCatching {
                            val result = when (val multiSourceRepository = repository as? MultiSourceAnimeCatalogRepository) {
                                null -> repository.search(
                                    AnimeCatalogQuery(text = query, pageSize = SOURCE_SEARCH_RESULTS_PER_SOURCE),
                                )
                                else -> multiSourceRepository.searchSource(
                                    sourceId = source.id,
                                    query = AnimeCatalogQuery(text = query, pageSize = SOURCE_SEARCH_RESULTS_PER_SOURCE),
                                )
                            }
                            SourceSearchSectionState(
                                sourceId = source.id,
                                sourceName = source.name,
                                items = result.items,
                            )
                        }.getOrElse {
                            SourceSearchSectionState<Anime>(
                                sourceId = source.id,
                                sourceName = source.name,
                                hasError = true,
                            )
                        }
                    }
                }.awaitAll()
            }
            _state.update { current ->
                if (current.query.trim() != query) current else current.copy(
                    sections = results,
                    isSearching = false,
                )
            }
        }
    }

    fun retry(sourceId: String) {
        searchJob?.cancel()
        val query = state.value.query.trim()
        if (query.length < SOURCE_SEARCH_MIN_QUERY_LENGTH) return
        val source = searchableSources.firstOrNull { it.id == sourceId } ?: return
        searchJob = scope.launch {
            _state.update { current ->
                current.copy(
                    sections = current.sections.map { section ->
                        if (section.sourceId == sourceId) section.copy(hasError = false, isLoading = true)
                        else section
                    },
                    isSearching = true,
                )
            }
            val result = runCatching {
                when (val multiSourceRepository = repository as? MultiSourceAnimeCatalogRepository) {
                    null -> repository.search(
                        AnimeCatalogQuery(text = query, pageSize = SOURCE_SEARCH_RESULTS_PER_SOURCE),
                    )
                    else -> multiSourceRepository.searchSource(
                        sourceId = source.id,
                        query = AnimeCatalogQuery(text = query, pageSize = SOURCE_SEARCH_RESULTS_PER_SOURCE),
                    )
                }
            }
            _state.update { current ->
                current.copy(
                    sections = current.sections.map { section ->
                        if (section.sourceId != sourceId) section
                        else result.fold(
                            onSuccess = { page -> section.copy(items = page.items, hasError = false, isLoading = false) },
                            onFailure = { section.copy(hasError = true, isLoading = false) },
                        )
                    },
                    isSearching = false,
                )
            }
        }
    }

    private fun sourcesForQuery(query: String): List<AppSourceDescriptor> =
        if (shouldRestrictSourceSearchToRussian(query)) {
            searchableSources.filter { languageIsRussian(it.language) }
        } else {
            searchableSources
        }

    private fun languageIsRussian(language: String): Boolean =
        language.lowercase() in setOf("ru", "russian", "русский")

    fun close() {
        searchJob?.cancel()
    }
}
