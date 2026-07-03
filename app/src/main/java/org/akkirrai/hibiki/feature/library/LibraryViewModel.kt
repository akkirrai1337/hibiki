package org.akkirrai.hibiki.feature.library

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryEntry
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository

class LibraryViewModel(
    context: Context,
    libraryRepository: LibraryRepository? = null,
    searchRepository: AnimeSearchRepository? = null,
    offlineDownloadRepository: OfflineDownloadRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val libraryRepositoryDelegate = lazy { libraryRepository ?: LibraryRepository(appContext) }
    private val searchRepositoryDelegate = lazy { searchRepository ?: AnimeSearchRepository(appContext) }
    private val offlineDownloadRepositoryDelegate = lazy {
        offlineDownloadRepository ?: OfflineDownloadRepository(appContext)
    }
    private val offlineTitleMetadataRepositoryDelegate = lazy {
        offlineTitleMetadataRepository ?: OfflineTitleMetadataRepository(appContext)
    }
    private val libraryRepository by libraryRepositoryDelegate
    private val searchRepository by searchRepositoryDelegate
    private val offlineDownloadRepository by offlineDownloadRepositoryDelegate
    private val offlineTitleMetadataRepository by offlineTitleMetadataRepositoryDelegate
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private var syncJob: Job? = null
    private var lastStorageSyncAt = 0L
    private var lastDetailsRefreshAt = 0L

    init {
        PerfLogger.mark("LibraryViewModel created")
    }

    fun syncFromStorage(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && syncJob?.isActive == true) {
            PerfLogger.mark("Library sync skipped", "reason=already_running")
            return
        }
        if (!force && lastStorageSyncAt > 0L && now - lastStorageSyncAt < LOCAL_SYNC_THROTTLE_MS) {
            PerfLogger.mark(
                event = "Library sync skipped",
                details = "reason=throttled, sinceLast=${now - lastStorageSyncAt}ms",
            )
            return
        }

        PerfLogger.mark("Library sync scheduled", "force=$force")
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            val syncStartedAt = SystemClock.elapsedRealtime()
            var saved = PerfLogger.measure("Library storage read") {
                libraryRepository.getLibraryEntries()
            }
            lastStorageSyncAt = SystemClock.elapsedRealtime()
            updateEntries(saved)

            val changed = PerfLogger.measure("Library reconcile downloads") {
                reconcileSavedDownloads()
            }
            if (changed) {
                saved = PerfLogger.measure("Library storage reread after reconcile") {
                    libraryRepository.getLibraryEntries()
                }
                updateEntries(saved)
            }

            val shouldRefreshDetails = force ||
                lastDetailsRefreshAt == 0L ||
                SystemClock.elapsedRealtime() - lastDetailsRefreshAt >= DETAILS_REFRESH_INTERVAL_MS
            if (shouldRefreshDetails) {
                refreshDetails(saved)
            } else {
                PerfLogger.mark(
                    event = "Library details refresh skipped",
                    details = "reason=interval, entries=${saved.size}",
                )
            }
            PerfLogger.mark(
                event = "Library sync finished",
                details = "entries=${saved.size}, changed=$changed, duration=${PerfLogger.elapsedMs(syncStartedAt)}ms",
            )
        }
    }

    private fun reconcileSavedDownloads(): Boolean {
        var changed = false
        offlineDownloadRepository.getOfflineTitleIds().forEach { titleId ->
            val metadata = offlineTitleMetadataRepository.get(titleId) ?: return@forEach
            if (LibraryCategory.Saved !in libraryRepository.getLibraryCategories(titleId)) {
                libraryRepository.saveToLibrary(metadata, LibraryCategory.Saved)
                changed = true
            }
        }
        return changed
    }

    private suspend fun refreshDetails(saved: List<LibraryEntry>) {
        if (saved.isEmpty()) {
            PerfLogger.mark("Library details refresh skipped", "reason=empty")
            return
        }

        val startedAt = SystemClock.elapsedRealtime()
        PerfLogger.mark("Library details refresh started", "entries=${saved.size}")
        _uiState.update { it.copy(isRefreshing = true) }
        try {
            val refreshed = saved
                .groupBy { entry -> entry.anime.id }
                .flatMap { (_, groupedEntries) ->
                    val baseEntry = groupedEntries.first()
                    val currentCategories = libraryRepository.getLibraryCategories(baseEntry.anime.id)
                    if (currentCategories.isEmpty()) {
                        return@flatMap emptyList()
                    }

                    val freshAnime = runCatching { searchRepository.getDetails(baseEntry.anime.id, baseEntry.anime) }
                        .onFailure { throwable ->
                            if (throwable is CancellationException) {
                                throw throwable
                            }
                        }
                        .getOrNull()
                    val anime = freshAnime ?: baseEntry.anime
                    if (freshAnime != null) {
                        currentCategories.sortedBy(LibraryCategory::ordinal).forEach { category ->
                            libraryRepository.saveToLibrary(freshAnime, category)
                        }
                    }
                    currentCategories
                        .sortedBy(LibraryCategory::ordinal)
                        .map { category -> baseEntry.copy(anime = anime, category = category) }
                }
            lastDetailsRefreshAt = SystemClock.elapsedRealtime()
            _uiState.update {
                it.copy(
                    entries = refreshed,
                    selectedCategory = preferredCategory(refreshed, it.selectedCategory),
                    isRefreshing = false,
                )
            }
            PerfLogger.mark(
                event = "Library details refresh finished",
                details = "entries=${saved.size}, refreshed=${refreshed.size}, duration=${PerfLogger.elapsedMs(startedAt)}ms",
            )
        } finally {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectCategory(category: LibraryCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun updateEntries(entries: List<LibraryEntry>) {
        _uiState.update {
            it.copy(
                entries = entries,
                selectedCategory = preferredCategory(entries, it.selectedCategory),
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun applySearchFilters(filters: LibrarySearchFilters) {
        _uiState.update { it.copy(searchFilters = filters) }
    }

    fun resetSearchFilters() {
        _uiState.update { it.copy(searchFilters = LibrarySearchFilters()) }
    }

    private fun preferredCategory(
        entries: List<LibraryEntry>,
        current: LibraryCategory,
    ): LibraryCategory {
        if (entries.any { it.category == current }) return current
        return orderedLibraryCategories(entries)
            .firstOrNull { category -> entries.any { it.category == category } }
            ?: current
    }

    override fun onCleared() {
        if (searchRepositoryDelegate.isInitialized()) {
            PerfLogger.mark("LibraryViewModel close search repository")
            searchRepository.close()
        }
        super.onCleared()
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return LibraryViewModel(
                context = context.applicationContext,
                libraryRepository = dependencies.libraryRepository(),
                searchRepository = dependencies.animeSearchRepository(),
                offlineDownloadRepository = dependencies.offlineDownloadRepository(),
                offlineTitleMetadataRepository = dependencies.offlineTitleMetadataRepository(),
            ) as T
        }
    }
}

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val selectedCategory: LibraryCategory = LibraryCategory.Watching,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchFilters: LibrarySearchFilters = LibrarySearchFilters(),
) {
    val orderedCategories: List<LibraryCategory>
        get() = orderedLibraryCategories(entries)

    val visibleEntries: List<LibraryEntry>
        get() {
            val normalizedQuery = searchQuery.trim()
            return entries.filter { entry ->
                entry.category == selectedCategory &&
                    searchFilters.matches(entry) &&
                    (
                        normalizedQuery.isBlank() ||
                            entry.anime.title.contains(normalizedQuery, ignoreCase = true) ||
                            entry.anime.subtitle.contains(normalizedQuery, ignoreCase = true)
                        )
            }
        }

    val categoryCounts: Map<LibraryCategory, Int>
        get() = entries.groupingBy { it.category }.eachCount()

    val filterCatalog: LibraryFilterCatalog
        get() {
            val categoryEntries = entries.filter { it.category == selectedCategory }
            return LibraryFilterCatalog(
                typeOptions = categoryEntries.mapNotNull { it.anime.extractLibraryType() }.distinct().sorted(),
                statusOptions = categoryEntries.map { it.anime.status.trim() }.filter(String::isNotBlank).distinct().sorted(),
                genreOptions = categoryEntries.flatMap { it.anime.genres }.map(String::trim).filter(String::isNotBlank).distinct().sorted(),
            )
        }
}

data class LibrarySearchFilters(
    val type: String? = null,
    val status: String? = null,
    val includedGenres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
) {
    fun matches(entry: LibraryEntry): Boolean {
        val anime = entry.anime
        val typeMatches = type == null || anime.extractLibraryType() == type
        val statusMatches = status == null || anime.status.equals(status, ignoreCase = true)
        val animeGenres = anime.genres.map(String::trim).filter(String::isNotBlank).toSet()
        val includesMatch = includedGenres.isEmpty() || includedGenres.all { it in animeGenres }
        val excludesMatch = excludedGenres.none { it in animeGenres }
        return typeMatches && statusMatches && includesMatch && excludesMatch
    }

    fun hasActiveFilters(): Boolean {
        return type != null || status != null || includedGenres.isNotEmpty() || excludedGenres.isNotEmpty()
    }
}

data class LibraryFilterCatalog(
    val typeOptions: List<String> = emptyList(),
    val statusOptions: List<String> = emptyList(),
    val genreOptions: List<String> = emptyList(),
)

private fun org.akkirrai.hibiki.core.model.Anime.extractLibraryType(): String? {
    return subtitle
        .split(Regex("\\s*[•·|]\\s*"))
        .map(String::trim)
        .firstOrNull { value ->
            value.isNotBlank() && value.any(Char::isLetter) && value.none(Char::isDigit)
        }
}

private fun orderedLibraryCategories(entries: List<LibraryEntry>): List<LibraryCategory> {
    return if (entries.any { it.category == LibraryCategory.Saved }) {
        listOf(LibraryCategory.Saved) + LibraryCategory.entries.filter { it != LibraryCategory.Saved }
    } else {
        LibraryCategory.entries.toList()
    }
}

private const val LOCAL_SYNC_THROTTLE_MS = 2_000L
private const val DETAILS_REFRESH_INTERVAL_MS = 30 * 60 * 1_000L
