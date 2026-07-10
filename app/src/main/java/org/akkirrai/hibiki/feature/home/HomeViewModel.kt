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
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.feature.account.resolvedAvatarUrl

class HomeViewModel(
    private val repository: HomeRepository,
    private val accountRepository: YummyAccountRepository,
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
        load()
        refreshProfileAvatar()
        loadSearchFilterCatalog()
        observeLanguageChanges()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home refresh started")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.refreshHomeState() }
                .onSuccess { state ->
                    val current = _uiState.value
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                        profileAvatarUrl = current.profileAvatarUrl,
                    )
                    enrichRecentDescriptions()
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
                        details = "duration=${System.currentTimeMillis() - startedAt}ms, error=${throwable::class.java.simpleName}",
                    )
                }
        }
    }

    private var searchJob: Job? = null
    private var profileAvatarJob: Job? = null
    private var recentDescriptionsJob: Job? = null
    private var lastProfileAvatarReadAt = 0L

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

    fun applySearchFilters(filters: HomeSearchFilters) {
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
        applySearchFilters(HomeSearchFilters())
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
            if (!immediate) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            val activeQuery = uiState.value.searchQuery.trim()
            val activeFilters = uiState.value.searchFilters
            if (activeQuery.length < MIN_QUERY_LENGTH && !activeFilters.hasActiveFilters()) {
                _uiState.update { it.copy(searchResult = SearchUiState.Idle) }
                return@launch
            }

            _uiState.update { it.copy(searchResult = SearchUiState.Loading) }

            try {
                val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.search(
                        query = activeQuery,
                        filters = activeFilters,
                        limit = SEARCH_PAGE_SIZE + 1,
                        offset = 0,
                    )
                }
                if (activeQuery != uiState.value.searchQuery.trim()) return@launch
                _uiState.update {
                    it.copy(
                        searchResult = if (items.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Content(
                                items = items.take(SEARCH_PAGE_SIZE),
                                canLoadMore = items.size > SEARCH_PAGE_SIZE,
                            )
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (activeQuery != uiState.value.searchQuery.trim()) return@launch
                val message = when (throwable) {
                    is SourceException -> throwable.message ?: appString(R.string.error_source_generic)
                    else -> throwable.message ?: appString(R.string.error_search_failed)
                }
                _uiState.update { it.copy(searchResult = SearchUiState.Error(message)) }
            }
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
        if (current.isTrendingLoadingMore || !current.canLoadMoreTrending) return
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

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home load started")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadHomeState() }
                .onSuccess { state ->
                    val current = _uiState.value
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        searchQuery = current.searchQuery,
                        searchResult = current.searchResult,
                        searchFilterCatalog = current.searchFilterCatalog,
                        isSearchFilterCatalogLoading = current.isSearchFilterCatalogLoading,
                        searchFilters = current.searchFilters,
                        profileAvatarUrl = current.profileAvatarUrl,
                    )
                    enrichRecentDescriptions()
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
                        details = "duration=${System.currentTimeMillis() - startedAt}ms, error=${throwable::class.java.simpleName}",
                    )
                }
        }
    }

    fun refreshProfileAvatar() {
        val now = SystemClock.elapsedRealtime()
        if (profileAvatarJob?.isActive == true) {
            PerfLogger.mark("Home profile avatar refresh skipped", "reason=already_running")
            return
        }
        if (lastProfileAvatarReadAt > 0L && now - lastProfileAvatarReadAt < PROFILE_AVATAR_CACHE_READ_THROTTLE_MS) {
            PerfLogger.mark(
                event = "Home profile avatar refresh skipped",
                details = "reason=throttled, sinceLast=${now - lastProfileAvatarReadAt}ms",
            )
            return
        }

        profileAvatarJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home profile avatar refresh started")
            val cachedAvatarUrl = accountRepository.getCachedProfile()?.resolvedAvatarUrl()
            lastProfileAvatarReadAt = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(profileAvatarUrl = cachedAvatarUrl) }
            PerfLogger.mark(
                event = "Home profile avatar refresh finished",
                details = "source=cache, duration=${System.currentTimeMillis() - startedAt}ms, hasAvatar=${cachedAvatarUrl != null}",
            )
        }
    }

    private fun enrichRecentDescriptions() {
        recentDescriptionsJob?.cancel()
        val ids = _uiState.value.recentlyUpdated.map { it.id }
        if (ids.isEmpty()) return
        recentDescriptionsJob = viewModelScope.launch(Dispatchers.IO) {
            val enriched = repository.enrichDescriptions(_uiState.value.recentlyUpdated)
            _uiState.update { current ->
                if (current.recentlyUpdated.map { it.id } != ids) current
                else current.copy(recentlyUpdated = enriched)
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        profileAvatarJob?.cancel()
        recentDescriptionsJob?.cancel()
        repository.close()
        accountRepository.close()
        super.onCleared()
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
        viewModelScope.launch {
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
        const val PROFILE_AVATAR_CACHE_READ_THROTTLE_MS = 5_000L
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return HomeViewModel(
                repository = dependencies.homeRepository(),
                accountRepository = dependencies.accountRepository(),
                context = context.applicationContext,
            ) as T
        }
    }

    private fun appString(@StringRes resId: Int): String = appContext.getString(resId)
}
