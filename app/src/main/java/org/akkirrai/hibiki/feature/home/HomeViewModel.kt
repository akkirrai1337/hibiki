package org.akkirrai.hibiki.feature.home

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.toSearchErrorMessage

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
        load()
        observeLanguageChanges()
        observeSourceChanges()
        observeWatchProgress()
        observeSourceRegistryReadiness()
    }

    private var homeLoadJob: Job? = null
    private var homeSupplementJob: Job? = null

    fun refresh() {
        homeLoadJob?.cancel()
        homeSupplementJob?.cancel()
        homeLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home refresh started")
            val hasContinueHistory = repository.hasContinueHistory()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    hasContinueHistory = hasContinueHistory,
                )
            }
            runCatching { repository.refreshHomeState() }
                .onSuccess { state ->
                    val current = _uiState.value
                    // Source cards arrive immediately; aggregator decoration is delivered through
                    // the durable card overlay, so no source-details call blocks the feed.
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        hasContinueHistory = current.hasContinueHistory,
                    )
                    PerfLogger.mark(
                        event = "Home refresh finished",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms",
                    )
                    loadHomeSupplements(forceRefresh = true)
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

    private val recentRandomIds = ArrayDeque<String>()

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
        homeSupplementJob?.cancel()
        homeLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            PerfLogger.mark("Home load started")
            val hasContinueHistory = repository.hasContinueHistory()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    hasContinueHistory = hasContinueHistory,
                )
            }
            runCatching { repository.loadHomeState() }
                .onSuccess { state ->
                    // The source lists are sufficient for first paint; pending aggregator work is
                    // represented by a row-level loader and updates the same source-owned card.
                    val current = _uiState.value
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = null,
                        hasContinueHistory = current.hasContinueHistory,
                    )
                    PerfLogger.mark(
                        event = "Home load finished",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms",
                    )
                    loadHomeSupplements()
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
        homeLoadJob?.cancel()
        homeSupplementJob?.cancel()
        repository.close()
        super.onCleared()
    }

    /** The source catalog is enough for first paint; slow rows enrich it after the screen is live. */
    private fun loadHomeSupplements(forceRefresh: Boolean = false) {
        homeSupplementJob?.cancel()
        homeSupplementJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            runCatching { repository.loadHomeSupplements(forceRefresh) }
                .onSuccess { supplements ->
                    _uiState.update { state ->
                        state.copy(
                            continueAnime = supplements.continueAnime,
                            recentlyUpdated = supplements.recentlyUpdated,
                            trending = supplements.trending,
                        )
                    }
                    PerfLogger.mark(
                        event = "Home supplements finished",
                        details = "duration=${System.currentTimeMillis() - startedAt}ms, " +
                            "trending=${supplements.trending.size}, recentlyUpdated=${supplements.recentlyUpdated.size}",
                    )
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        AppLogger.w("HomeViewModel", "Home supplements failed", error)
                    }
                }
        }
    }

    private fun observeLanguageChanges() {
        viewModelScope.launch {
            appPreferences.state
                .map { it.languageMode }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    load()
                            }
        }
    }

    private companion object {
        const val RECENT_UPDATES_PAGE_SIZE = 12
        const val RANDOM_HISTORY_SIZE = 20
    }

    /**
     * APK extensions register asynchronously after process start, so the very first home load can
     * run against an empty registry and fail with NoSourcesInstalled. Retry once sources appear
     * instead of leaving the feed on that error until something else forces a reload.
     */
    private fun observeSourceRegistryReadiness() {
        viewModelScope.launch {
            snapshotFlow { AnimeSourceRegistry.sources.map { it.id } }
                .distinctUntilChanged()
                .collect { ids ->
                    if (ids.isNotEmpty() && _uiState.value.errorMessage != null && !_uiState.value.isLoading) {
                        load()
                    }
                }
        }
    }

    /** A finished or cleared watch shows up in the continue rows at once instead of after a restart. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeWatchProgress() {
        viewModelScope.launch {
            WatchStateRepository.changes
                .debounce(600)
                .collect {
                    val (continueAnime, recentlyWatched) = withContext(Dispatchers.IO) {
                        runCatching { repository.loadWatchRows() }.getOrNull()
                    } ?: return@collect
                    _uiState.update { state ->
                        if (state.isLoading) state
                        else state.copy(
                            continueAnime = continueAnime,
                            recentlyWatched = recentlyWatched,
                            hasContinueHistory = continueAnime != null,
                        )
                    }
                }
        }
    }

    private fun observeSourceChanges() {
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect {
                homeLoadJob?.cancel()
                recentRandomIds.clear()
                load()
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
