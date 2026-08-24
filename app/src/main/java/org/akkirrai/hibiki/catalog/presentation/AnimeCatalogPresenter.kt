package org.akkirrai.hibiki.catalog.presentation

import org.akkirrai.hibiki.catalog.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.core.anilist.AniListRepository
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

data class AnimeCatalogUiState(
    val query: String = "",
    val filters: AnimeSearchFilters = AnimeSearchFilters(sortAlias = "popular"),
    val items: List<Anime> = emptyList(),
    val page: Int = 1,
    val canLoadMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedAnime: Anime? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: String? = null,
    val filterCatalog: AnimeCatalogFilterCatalog? = null,
    val isFilterCatalogLoading: Boolean = false,
)

/**
 * The slice of [AnimeCatalogUiState] that drives navigation to Details/Watch from outside the
 * Catalog screen. Hosts (the app shell) that only care about "is a title selected right now"
 * should collect this instead of the full state, so a change to the catalog's own query/items/
 * filters/pagination doesn't ripple into a shell-wide recomposition.
 */
data class CatalogDetailsNavigationState(
    val selectedAnime: Anime? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: String? = null,
)

/** Lifecycle-neutral presenter that can be hosted by Android ViewModel or Desktop Compose. */
class AnimeCatalogPresenter(
    private val repository: AnimeCatalogRepository,
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val aniListRepository: AniListRepository? = null,
) {
    private val _state = MutableStateFlow(
        AnimeCatalogUiState(items = repository.initialItems),
    )
    val state: StateFlow<AnimeCatalogUiState> = _state.asStateFlow()

    // A plain Flow, not stateIn'd: this projection should only be collected while a composable
    // is actually watching it (Compose's collectAsState honors that lifecycle on its own). A
    // stateIn(scope, SharingStarted.Eagerly, ...) here would launch a collector that outlives
    // every test's TestScope, since Eagerly never stops -- runTest then fails waiting for it.
    val detailsNavigationState: Flow<CatalogDetailsNavigationState> = state
        .map { CatalogDetailsNavigationState(it.selectedAnime, it.isDetailsLoading, it.detailsError) }
        .distinctUntilChanged()

    private var searchJob: Job? = null
    private var filterCatalogJob: Job? = null
    private var detailsJob: Job? = null
    private var enrichmentJob: Job? = null
    private val detailsBackStack = mutableListOf<Anime>()

    fun onQueryChange(query: String) {
        setQuery(query)
        // Short prefixes (0-2 chars) either 400 on sources that reject too-short queries or just
        // waste a request on results the next keystroke immediately invalidates -- wait for a
        // real query. An empty box means "back to browsing", so that one searches right away
        // instead of debouncing a no-op.
        scheduleSearch(immediate = query.isBlank())
    }

    private fun scheduleSearch(immediate: Boolean) {
        searchJob?.cancel()
        val query = state.value.query.trim()
        if (query.isNotEmpty() && query.length < MIN_QUERY_LENGTH) return
        searchJob = scope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            performSearch()
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
    }

    fun clear() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                items = repository.initialItems,
                filterCatalog = null,
                page = 1,
                canLoadMore = false,
                isLoading = false,
                isLoadingMore = false,
                error = null,
            )
        }
    }

    fun updateFilters(filters: AnimeSearchFilters) {
        setFilters(filters)
        search()
    }

    fun setFilters(filters: AnimeSearchFilters) {
        _state.update { it.copy(filters = filters, error = null) }
    }

    fun updateItem(updated: Anime) {
        _state.update { state ->
            state.copy(items = state.items.map { item -> if (item.id == updated.id) updated else item })
        }
    }

    fun openDetails(anime: Anime) {
        val current = state.value.selectedAnime
        if (current == null) {
            detailsBackStack.clear()
        } else if (current.id != anime.id) {
            detailsBackStack += current
        }
        loadDetails(anime)
    }

    fun closeDetails() {
        val previous = if (detailsBackStack.isEmpty()) {
            null
        } else {
            detailsBackStack.removeAt(detailsBackStack.lastIndex)
        }
        if (previous == null) {
            detailsJob?.cancel()
            enrichmentJob?.cancel()
            _state.update { it.copy(selectedAnime = null, isDetailsLoading = false, detailsError = null) }
        } else {
            loadDetails(previous)
        }
    }

    fun search() {
        scheduleSearch(immediate = true)
    }

    private suspend fun performSearch() {
        val current = state.value
        _state.update {
            it.copy(
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                error = null,
                page = 1,
                canLoadMore = false,
            )
        }
        try {
            val result = repository.search(
                AnimeCatalogQuery(
                    text = current.query.trim(),
                    page = 1,
                    pageSize = pageSize,
                    filters = current.filters,
                ),
            )
            _state.update {
                it.copy(
                    items = preserveLoadedDescriptions(it.items, result.items),
                    page = result.page,
                    canLoadMore = canRequestNextPage(result),
                    isLoading = false,
                    isLoadingMore = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            _state.update { it.copy(isLoading = false, isLoadingMore = false, error = throwable.message ?: "Catalog request failed") }
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || !current.canLoadMore) return
        searchJob?.cancel()
        searchJob = scope.launch {
            _state.update { it.copy(isLoading = true, isLoadingMore = true, error = null) }
            try {
                val result = repository.search(
                    AnimeCatalogQuery(
                        text = current.query.trim(),
                        page = current.page + 1,
                        pageSize = pageSize,
                        filters = current.filters,
                    ),
                )
                _state.update {
                    val mergedItems = (it.items + result.items).distinctBy(Anime::id)
                    it.copy(
                        items = mergedItems,
                        page = result.page,
                        canLoadMore = canRequestNextPage(result, hasNewItems = mergedItems.size > it.items.size),
                        isLoading = false,
                        isLoadingMore = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, error = throwable.message ?: "Catalog request failed") }
            }
        }
    }

    fun loadFilterCatalog() {
        filterCatalogJob?.cancel()
        filterCatalogJob = scope.launch {
            _state.update { it.copy(isFilterCatalogLoading = true) }
            try {
                _state.update {
                    it.copy(
                        filterCatalog = repository.filterCatalog(),
                        isFilterCatalogLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.update { it.copy(isFilterCatalogLoading = false) }
            }
        }
    }

    fun close() {
        searchJob?.cancel()
        filterCatalogJob?.cancel()
        detailsJob?.cancel()
        enrichmentJob?.cancel()
        detailsBackStack.clear()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_DEBOUNCE_MS = 400L
    }

    fun restore(state: AnimeCatalogUiState) {
        searchJob?.cancel()
        filterCatalogJob?.cancel()
        detailsJob?.cancel()
        enrichmentJob?.cancel()
        detailsBackStack.clear()
        _state.value = state.copy(
            isLoading = false,
            isLoadingMore = false,
            error = null,
            isDetailsLoading = false,
        )
    }

    private fun loadDetails(anime: Anime) {
        detailsJob?.cancel()
        enrichmentJob?.cancel()
        _state.update { it.copy(selectedAnime = anime, isDetailsLoading = true, detailsError = null) }
        detailsJob = scope.launch {
            try {
                val details = repository.getDetails(anime.id, anime)
                _state.update {
                    // Keep the object reference stable when the fetch resolved to data
                    // equal to what's already shown, so downstream `remember(anime, ...)`
                    // blocks don't recompute and flash the screen right after it settles.
                    val nextAnime = if (details == it.selectedAnime) it.selectedAnime else details
                    it.copy(selectedAnime = nextAnime, isDetailsLoading = false)
                }
                enrichWithAniList(details)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        selectedAnime = anime,
                        isDetailsLoading = false,
                        detailsError = throwable.message ?: "Details request failed",
                    )
                }
            }
        }
    }

    /** Best-effort banner/score/characters/directors patch-in, independent of [detailsJob] so a
     *  slow or failed AniList lookup never blocks or errors the base details screen. */
    private fun enrichWithAniList(details: Anime) {
        val repo = aniListRepository ?: return
        enrichmentJob = scope.launch {
            val enrichment = runCatching { repo.enrich(details) }.getOrNull() ?: return@launch
            _state.update { state ->
                val current = state.selectedAnime
                if (current == null || current.id != details.id) return@update state
                state.copy(
                    selectedAnime = current.copy(
                        bannerUrl = enrichment.bannerUrl ?: current.bannerUrl,
                        averageScore = enrichment.averageScore ?: current.averageScore,
                        characters = enrichment.characters,
                        directors = enrichment.directors,
                    ),
                )
            }
        }
    }

    fun clearDetails() {
        detailsJob?.cancel()
        enrichmentJob?.cancel()
        detailsBackStack.clear()
        _state.update { it.copy(selectedAnime = null, isDetailsLoading = false, detailsError = null) }
    }

    private fun preserveLoadedDescriptions(
        previousItems: List<Anime>,
        updatedItems: List<Anime>,
    ): List<Anime> {
        val descriptions = previousItems
            .mapNotNull { anime ->
                anime.description
                    ?.takeIf(String::isNotBlank)
                    ?.let { description -> anime.id to description }
            }
            .toMap()

        return updatedItems.map { anime ->
            if (anime.description.isNullOrBlank()) {
                descriptions[anime.id]?.let { description -> anime.copy(description = description) } ?: anime
            } else {
                anime
            }
        }
    }

    fun refresh() {
        repository.invalidate()
        search()
    }

    private fun canRequestNextPage(
        result: AnimeCatalogPage,
        hasNewItems: Boolean = result.items.isNotEmpty(),
    ): Boolean = hasNewItems && (
        result.canLoadMore || repository.canContinuePaginationAfterShortPage()
    )
}
