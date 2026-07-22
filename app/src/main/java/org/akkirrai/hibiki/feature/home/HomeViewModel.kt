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
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.model.SearchUiState

class HomeViewModel(
    private val repository: HomeRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val _uiState = MutableStateFlow(
        HomeUiState(isLoading = true)
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val descriptionUpdates = Channel<Anime>(Channel.UNLIMITED)

    init {
        PerfLogger.mark("HomeViewModel created")
        observeDescriptionUpdates()
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
                    val preparedState = prepareHomeFeed(state)
                    val current = _uiState.value
                    _uiState.value = preparedState.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                    ).preserveLoadedDescriptions(current)
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
    private val descriptionRequests = ConcurrentHashMap.newKeySet<String>()
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

    fun enrichDescription(anime: Anime) {
        if (!anime.description.isNullOrBlank() || !descriptionRequests.add(anime.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.enrichDescription(anime) }
                .onSuccess { enriched ->
                    if (enriched.description.isNullOrBlank()) {
                        descriptionRequests.remove(anime.id)
                        return@onSuccess
                    }
                    descriptionUpdates.trySend(enriched)
                }
                .onFailure { descriptionRequests.remove(anime.id) }
        }
    }

    private suspend fun loadFirstSearchPage(
        activeQuery: String,
        activeFilters: AnimeSearchFilters,
    ) {
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.search(
                    query = activeQuery,
                    filters = activeFilters,
                    limit = SEARCH_PAGE_SIZE + 1,
                    offset = 0,
                )
            }
            if (activeQuery != uiState.value.searchQuery.trim()) return
            val result = if (items.isEmpty()) {
                SearchUiState.Empty
            } else {
                SearchUiState.Content(
                    items = items.take(SEARCH_PAGE_SIZE),
                    canLoadMore = items.size > SEARCH_PAGE_SIZE,
                )
            }
            _uiState.update { it.copy(searchResult = result) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
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

    fun loadMoreTrending() {
        val current = uiState.value
        if (current.isLoading || current.isTrendingLoadingMore || !current.canLoadMoreTrending) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTrendingLoadingMore = true) }
            val page = runCatching {
                repository.loadTrendingPage(offset = current.trendingNextOffset, limit = TRENDING_PAGE_SIZE)
            }.getOrElse { emptyList() }
            _uiState.update { state ->
                state.copy(
                    trending = (state.trending + page).distinctBy { it.id },
                    isTrendingLoadingMore = false,
                    canLoadMoreTrending = page.size >= TRENDING_PAGE_SIZE,
                    trendingNextOffset = current.trendingNextOffset + page.size,
                )
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
                    val preparedState = prepareHomeFeed(state)
                    val current = _uiState.value
                    _uiState.value = preparedState.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                    ).preserveLoadedDescriptions(current)
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

    override fun onCleared() {
        searchJob?.cancel()
        homeLoadJob?.cancel()
        filterCatalogJob?.cancel()
        descriptionUpdates.close()
        repository.close()
        super.onCleared()
    }

    private fun observeDescriptionUpdates() {
        viewModelScope.launch {
            for (firstUpdate in descriptionUpdates) {
                val updates = linkedMapOf(firstUpdate.id to firstUpdate)
                delay(DESCRIPTION_UPDATE_BATCH_WINDOW_MS)
                while (true) {
                    val nextUpdate = descriptionUpdates.tryReceive().getOrNull() ?: break
                    updates[nextUpdate.id] = nextUpdate
                }
                _uiState.update { state -> state.replaceDescriptions(updates) }
            }
        }
    }

    private fun HomeUiState.replaceDescriptions(updates: Map<String, Anime>): HomeUiState {
        val updatedFeatured = featuredAnime.replaceDescriptions(updates)
        val updatedTrending = trending.replaceDescriptions(updates)
        val updatedRecent = recentlyUpdated.replaceDescriptions(updates)
        val updatedSearchResult = searchResult.replaceDescriptions(updates)
        return if (
            updatedFeatured === featuredAnime &&
            updatedTrending === trending &&
            updatedRecent === recentlyUpdated &&
            updatedSearchResult === searchResult
        ) {
            this
        } else {
            copy(
                featuredAnime = updatedFeatured,
                trending = updatedTrending,
                recentlyUpdated = updatedRecent,
                searchResult = updatedSearchResult,
            )
        }
    }

    private fun HomeUiState.preserveLoadedDescriptions(previous: HomeUiState): HomeUiState {
        val descriptions = (previous.featuredAnime + previous.trending + previous.recentlyUpdated)
            .mapNotNull { anime -> anime.description?.takeIf(String::isNotBlank)?.let { anime.id to it } }
            .toMap()
        if (descriptions.isEmpty()) return this
        return copy(
            featuredAnime = featuredAnime.withDescriptions(descriptions),
            trending = trending.withDescriptions(descriptions),
            recentlyUpdated = recentlyUpdated.withDescriptions(descriptions),
        )
    }

    /**
     * The first Home frame is rendered only after the visible feed has stable metadata. This
     * prevents cards from changing height while the user is already interacting with the list.
     */
    private suspend fun prepareHomeFeed(state: HomeUiState): HomeUiState {
        val enrichedTrending = repository.enrichDescriptions(state.trending)
        val descriptions = enrichedTrending
            .mapNotNull { anime -> anime.description?.takeIf(String::isNotBlank)?.let { anime.id to it } }
            .toMap()
        return state.copy(
            featuredAnime = state.featuredAnime.withDescriptions(descriptions),
            trending = enrichedTrending,
        )
    }

    private fun List<Anime>.withDescriptions(descriptions: Map<String, String>): List<Anime> = map { anime ->
        if (anime.description.isNullOrBlank()) {
            descriptions[anime.id]?.let { description -> anime.copy(description = description) } ?: anime
        } else {
            anime
        }
    }

    private fun SearchUiState.replaceDescriptions(updates: Map<String, Anime>): SearchUiState = when (this) {
        is SearchUiState.Content -> copy(items = items.replaceDescriptions(updates))
        else -> this
    }

    private fun List<Anime>.replaceDescriptions(updates: Map<String, Anime>): List<Anime> {
        var changed = false
        val updatedItems = map { anime ->
            updates[anime.id]?.also { changed = true } ?: anime
        }
        return if (changed) updatedItems else this
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
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_PAGE_SIZE = 24
        const val TRENDING_PAGE_SIZE = 20
        const val RECENT_UPDATES_PAGE_SIZE = 12
        const val DESCRIPTION_UPDATE_BATCH_WINDOW_MS = 100L
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
