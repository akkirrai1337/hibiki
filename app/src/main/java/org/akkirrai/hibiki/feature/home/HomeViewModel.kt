package org.akkirrai.hibiki.feature.home

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.SearchUiState

class HomeViewModel(
    internal val repository: HomeRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val _uiState = MutableStateFlow(
        HomeUiState(isLoading = true)
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        PerfLogger.mark("HomeViewModel created")
        observeCardMetadata()
        observePendingCardMetadata()
        load()
        loadSearchFilterCatalog()
        observeLanguageChanges()
        observeSourceChanges()
    }

    private var homeLoadJob: Job? = null
    private var filterCatalogJob: Job? = null

    fun refresh() {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home refresh started")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.refreshHomeState() }
                .onSuccess { state ->
                    val current = _uiState.value
                    // Source cards arrive immediately; aggregator decoration is delivered through
                    // the durable card overlay, so no source-details call blocks the feed.
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                    ).withCardMetadata(repository.cardMetadata.value)
                    PerfLogger.mark(
                        event = "Home refresh finished",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms",
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: appString(R.string.home_error_refresh_failed),
                        )
                    }
                    PerfLogger.mark(
                        event = "Home refresh failed",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms, " +
                            "error=${throwable::class.java.simpleName}:${throwable.message}",
                    )
                }
        }
    }

    private var searchJob: Job? = null
    private val recentRandomIds = ArrayDeque<String>()

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

    fun applySearchFilters(filters: AnimeSearchFilters) {
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
        applySearchFilters(AnimeSearchFilters())
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
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            val activeQuery = uiState.value.searchQuery.trim()
            val activeFilters = uiState.value.searchFilters
            if (activeQuery.length < MIN_QUERY_LENGTH && !activeFilters.hasActiveFilters()) {
                _uiState.update { it.copy(searchResult = SearchUiState.Idle) }
                return@launch
            }

            _uiState.update { it.copy(searchResult = SearchUiState.Loading) }
            loadFirstSearchPage(activeQuery, activeFilters)
        }
    }

    private suspend fun loadFirstSearchPage(
        activeQuery: String,
        activeFilters: AnimeSearchFilters,
    ) {
        // The query itself is not logged, only its length - it is what the user typed.
        val startedAt = System.currentTimeMillis()
        AppLogger.d(SEARCH_LOG_TAG, "search start queryLength=${activeQuery.length} filtered=${activeFilters.hasActiveFilters()}")
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.search(
                    query = activeQuery,
                    filters = activeFilters,
                    limit = SEARCH_PAGE_SIZE + 1,
                    offset = 0,
                )
            }
            AppLogger.d(SEARCH_LOG_TAG, "search ok items=${items.size} in ${System.currentTimeMillis() - startedAt}ms")
            if (activeQuery != uiState.value.searchQuery.trim()) {
                AppLogger.d(SEARCH_LOG_TAG, "search result discarded: the query changed while it ran")
                return
            }
            val result = if (items.isEmpty()) {
                SearchUiState.Empty
            } else {
                SearchUiState.Content(
                    items = items.take(SEARCH_PAGE_SIZE),
                    canLoadMore = items.size > SEARCH_PAGE_SIZE,
                )
            }
            _uiState.update { it.copy(searchResult = result.withCardMetadata(repository.cardMetadata.value)) }
        } catch (cancelled: CancellationException) {
            AppLogger.d(SEARCH_LOG_TAG, "search cancelled after ${System.currentTimeMillis() - startedAt}ms")
            throw cancelled
        } catch (throwable: Throwable) {
            AppLogger.w(
                SEARCH_LOG_TAG,
                "search failed in ${System.currentTimeMillis() - startedAt}ms: ${throwable::class.java.simpleName}: ${throwable.message}",
                throwable,
            )
            if (activeQuery != uiState.value.searchQuery.trim()) return
            val message = when (throwable) {
                is SourceException -> throwable.message ?: appString(R.string.error_source_generic)
                else -> throwable.message ?: appString(R.string.error_search_failed)
            }
            _uiState.update { it.copy(searchResult = SearchUiState.Error(message)) }
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
                            ).distinctBy { it.id }.withCardMetadata(repository.cardMetadata.value),
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

    fun loadMoreRecentUpdates() {
        val current = uiState.value
        if (current.isRecentUpdatesLoadingMore || !current.canLoadMoreRecentUpdates) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRecentUpdatesLoadingMore = true, recentUpdatesLoadMoreError = null) }
            runCatching {
                repository.loadRecentlyUpdatedPage(
                    offset = current.recentlyUpdated.size,
                    limit = RECENT_UPDATES_PAGE_SIZE,
                )
            }.onSuccess { page ->
                _uiState.update { state ->
                    state.copy(
                        recentlyUpdated = (state.recentlyUpdated + page).distinctBy { it.id },
                        isRecentUpdatesLoadingMore = false,
                        canLoadMoreRecentUpdates = page.size >= RECENT_UPDATES_PAGE_SIZE,
                        recentUpdatesLoadMoreError = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isRecentUpdatesLoadingMore = false,
                        recentUpdatesLoadMoreError = throwable.message ?: appString(R.string.home_error_refresh_failed),
                    )
                }
            }
        }
    }

    fun openRandomAnime(onAnimeClick: (org.akkirrai.hibiki.core.model.Anime) -> Unit) {
        if (_uiState.value.isRandomLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRandomLoading = true) }
            val anime = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.loadRandomAnime(recentRandomIds.toSet())
                }
            }.getOrNull()
            _uiState.update { it.copy(isRandomLoading = false) }
            anime?.let {
                recentRandomIds += it.id
                if (recentRandomIds.size > RANDOM_HISTORY_SIZE) recentRandomIds.removeFirst()
                onAnimeClick(it)
            }
        }
    }

    fun load() {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home load started")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadHomeState() }
                .onSuccess { state ->
                    // The source lists are sufficient for first paint; pending aggregator work is
                    // represented by a row-level loader and updates the same source-owned card.
                    val current = _uiState.value
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                    ).withCardMetadata(repository.cardMetadata.value)
                    PerfLogger.mark(
                        event = "Home load finished",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms",
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: appString(R.string.home_error_load_failed),
                        )
                    }
                    PerfLogger.mark(
                        event = "Home load failed",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms, " +
                            "error=${throwable::class.java.simpleName}:${throwable.message}",
                    )
                }
        }
    }

    /**
     * Forgets a title's watch progress, taking it out of the continue and recently-watched rows.
     *
     * The rows are recomputed here rather than by reloading Home: the state already holds the order
     * the repository would rebuild - continue is simply the most recently watched title and the row
     * is the rest of them - so a reload would spend a network round trip to show one card fewer.
     */
    fun forgetWatchProgress(anime: Anime) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.forgetWatchProgress(anime.id)
            _uiState.update { state ->
                if (state.continueAnime?.id == anime.id) {
                    state.copy(
                        continueAnime = state.recentlyWatched.firstOrNull(),
                        recentlyWatched = state.recentlyWatched.drop(1),
                    )
                } else {
                    state.copy(recentlyWatched = state.recentlyWatched.filterNot { it.id == anime.id })
                }
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        homeLoadJob?.cancel()
        filterCatalogJob?.cancel()
        repository.close()
        super.onCleared()
    }

    private fun observeCardMetadata() {
        viewModelScope.launch {
            repository.cardMetadata.collect { metadata ->
                _uiState.update { state -> state.withCardMetadata(metadata) }
            }
        }
    }

    private fun observePendingCardMetadata() {
        viewModelScope.launch {
            repository.pendingCardMetadata.collect { pending ->
                _uiState.update { it.copy(pendingCardMetadata = pending) }
            }
        }
    }

    private fun HomeUiState.withCardMetadata(metadata: Map<String, Anime>): HomeUiState = copy(
        featuredAnime = featuredAnime.withCardMetadata(metadata),
        trending = trending.withCardMetadata(metadata),
        recentlyUpdated = recentlyUpdated.withCardMetadata(metadata),
        searchResult = searchResult.withCardMetadata(metadata),
    )

    private fun List<Anime>.withCardMetadata(metadata: Map<String, Anime>): List<Anime> = map { anime ->
        metadata[anime.id]?.copy(title = anime.title) ?: anime
    }

    private fun SearchUiState.withCardMetadata(metadata: Map<String, Anime>): SearchUiState = when (this) {
        is SearchUiState.Content -> copy(items = items.withCardMetadata(metadata))
        else -> this
    }

    private fun observeLanguageChanges() {
        viewModelScope.launch {
            appPreferences.state
                .map { it.languageMode }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    clearSearch()
                    load()
                    loadSearchFilterCatalog()
                }
        }
    }

    private fun loadSearchFilterCatalog() {
        filterCatalogJob?.cancel()
        filterCatalogJob = viewModelScope.launch {
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
        const val SEARCH_LOG_TAG = "HomeSearch"
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_PAGE_SIZE = 24
        const val RECENT_UPDATES_PAGE_SIZE = 12
        const val RANDOM_HISTORY_SIZE = 20
    }

    private fun observeSourceChanges() {
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect {
                    searchJob?.cancel()
                    homeLoadJob?.cancel()
                    filterCatalogJob?.cancel()
                    recentRandomIds.clear()
                    _uiState.update {
                        it.copy(
                            searchResult = SearchUiState.Idle,
                            searchFilters = AnimeSearchFilters(),
                            searchFilterCatalog = null,
                        )
                    }
                    load()
                    loadSearchFilterCatalog()
                }
        }
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return HomeViewModel(
                repository = dependencies.homeRepository(),
                context = context.applicationContext,
            ) as T
        }
    }

    private fun appString(@StringRes resId: Int): String = appContext.getString(resId)
}
