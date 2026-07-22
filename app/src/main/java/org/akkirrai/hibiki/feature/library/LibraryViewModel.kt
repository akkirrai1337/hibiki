package org.akkirrai.hibiki.feature.library

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryEntry
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.library.LibraryPresenter

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
    private val presenter = LibraryPresenter()
    val uiState: StateFlow<LibraryUiState> = presenter.state
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

    fun onLanguageChanged() {
        lastDetailsRefreshAt = 0L
        syncFromStorage(force = true)
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
        presenter.setRefreshing(true)
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
                        offlineTitleMetadataRepository.save(freshAnime)
                    }
                    currentCategories
                        .sortedBy(LibraryCategory::ordinal)
                        .map { category -> baseEntry.copy(anime = anime, category = category) }
                }
            lastDetailsRefreshAt = SystemClock.elapsedRealtime()
            presenter.updateEntries(refreshed)
            presenter.setRefreshing(false)
            PerfLogger.mark(
                event = "Library details refresh finished",
                details = "entries=${saved.size}, refreshed=${refreshed.size}, duration=${PerfLogger.elapsedMs(startedAt)}ms",
            )
        } finally {
            presenter.setRefreshing(false)
        }
    }

    fun selectCategory(category: LibraryCategory) {
        presenter.selectCategory(category)
    }

    private fun updateEntries(entries: List<LibraryEntry>) {
        presenter.updateEntries(entries)
    }

    fun onSearchQueryChange(query: String) {
        presenter.onSearchQueryChange(query)
    }

    fun clearSearch() {
        presenter.clearSearch()
    }

    fun applySearchFilters(filters: LibrarySearchFilters) {
        presenter.applySearchFilters(filters)
    }

    fun resetSearchFilters() {
        presenter.resetSearchFilters()
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

private const val LOCAL_SYNC_THROTTLE_MS = 2_000L
private const val DETAILS_REFRESH_INTERVAL_MS = 30 * 60 * 1_000L
