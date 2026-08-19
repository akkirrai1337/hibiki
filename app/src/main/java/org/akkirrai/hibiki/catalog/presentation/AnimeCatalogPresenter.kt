package org.akkirrai.hibiki.catalog.presentation

import org.akkirrai.hibiki.catalog.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
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

/** Lifecycle-neutral presenter that can be hosted by Android ViewModel or Desktop Compose. */
class AnimeCatalogPresenter(
    private val repository: AnimeCatalogRepository,
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val _state = MutableStateFlow(
        AnimeCatalogUiState(items = repository.initialItems),
    )
    val state: StateFlow<AnimeCatalogUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var filterCatalogJob: Job? = null
    private var detailsJob: Job? = null
    private val detailsBackStack = mutableListOf<Anime>()

    fun onQueryChange(query: String) {
        setQuery(query)
        search()
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
            _state.update { it.copy(selectedAnime = null, isDetailsLoading = false, detailsError = null) }
        } else {
            loadDetails(previous)
        }
    }

    fun search() {
        searchJob?.cancel()
        searchJob = scope.launch {
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
        detailsBackStack.clear()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    fun restore(state: AnimeCatalogUiState) {
        searchJob?.cancel()
        filterCatalogJob?.cancel()
        detailsJob?.cancel()
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

    fun clearDetails() {
        detailsJob?.cancel()
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
