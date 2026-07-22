package org.akkirrai.hibiki.shared.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

data class AnimeCatalogUiState(
    val query: String = "",
    val filters: AnimeSearchFilters = AnimeSearchFilters(),
    val items: List<Anime> = emptyList(),
    val page: Int = 1,
    val canLoadMore: Boolean = false,
    val isLoading: Boolean = false,
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

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, error = null) }
        search()
    }

    fun clear() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                items = repository.initialItems,
                page = 1,
                canLoadMore = false,
                isLoading = false,
                error = null,
            )
        }
    }

    fun updateFilters(filters: AnimeSearchFilters) {
        _state.update { it.copy(filters = filters, error = null) }
        search()
    }

    fun openDetails(anime: Anime) {
        detailsJob?.cancel()
        detailsJob = scope.launch {
            _state.update { it.copy(selectedAnime = anime, isDetailsLoading = true, detailsError = null) }
            try {
                val details = repository.getDetails(anime.id, anime)
                _state.update { it.copy(selectedAnime = details, isDetailsLoading = false) }
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

    fun closeDetails() {
        detailsJob?.cancel()
        _state.update { it.copy(selectedAnime = null, isDetailsLoading = false, detailsError = null) }
    }

    fun search() {
        searchJob?.cancel()
        searchJob = scope.launch {
            val current = state.value
            _state.update { it.copy(isLoading = true, error = null, page = 1) }
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
                        items = result.items,
                        page = result.page,
                        canLoadMore = result.canLoadMore,
                        isLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update { it.copy(isLoading = false, error = throwable.message ?: "Catalog request failed") }
            }
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || !current.canLoadMore) return
        searchJob?.cancel()
        searchJob = scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
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
                    it.copy(
                        items = (it.items + result.items).distinctBy(Anime::id),
                        page = result.page,
                        canLoadMore = result.canLoadMore,
                        isLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update { it.copy(isLoading = false, error = throwable.message ?: "Catalog request failed") }
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
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
