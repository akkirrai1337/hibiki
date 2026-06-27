package org.akkirrai.hibiki.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.AnimeWatchRepository

class WatchSourcesViewModel(
    private val animeId: String,
    private val repository: AnimeWatchRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchSourcesScreenState())
    val uiState: StateFlow<WatchSourcesScreenState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        loadPrioritySources(forceRefresh = false)
    }

    fun retry() {
        val current = _uiState.value
        when {
            current.items.isEmpty() -> loadPrioritySources(forceRefresh = true)
            current.priorityLoadCompleted -> loadMore(forceRefresh = true)
            else -> loadPrioritySources(forceRefresh = true)
        }
    }

    private fun loadPrioritySources(forceRefresh: Boolean) {
        val cached = repository.getCachedSources(animeId)
        val cachedSources = mergeSources(
            primary = cached?.sources.orEmpty(),
            secondary = offlineDownloadRepository.getOfflineSources(animeId),
        )
        val cachedVisibleItems = visibleItems(cachedSources, showAllItems = false)
        if (forceRefresh) {
            _uiState.value = WatchSourcesScreenState(
                allItems = emptyList(),
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMoreItems = false,
                showAllItems = false,
                priorityLoadCompleted = false,
                allSourcesLoaded = false,
                errorMessage = null,
            )
        } else {
        _uiState.value = WatchSourcesScreenState(
            allItems = cachedSources,
            items = cachedVisibleItems,
            isLoading = cached?.priorityLoadCompleted != true,
            isLoadingMore = false,
            hasMoreItems = hasMoreItems(
                allItems = cachedSources,
                visibleItems = cachedVisibleItems,
                showAllItems = false,
                allSourcesLoaded = cached?.allSourcesLoaded == true,
            ),
            showAllItems = false,
            priorityLoadCompleted = cached?.priorityLoadCompleted == true,
            allSourcesLoaded = cached?.allSourcesLoaded == true,
            errorMessage = null,
        )
        }
        if (!forceRefresh && cached?.priorityLoadCompleted == true) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadSources(animeId = animeId, includeNonPriority = false) { updated ->
                    val merged = mergeSources(
                        primary = updated,
                        secondary = offlineDownloadRepository.getOfflineSources(animeId),
                    )
                    _uiState.update { state ->
                        state.withSources(
                            sources = merged,
                            isLoading = true,
                            isLoadingMore = false,
                            showAllItems = false,
                            allSourcesLoaded = false,
                        )
                    }
                }
            }.onSuccess { sources ->
                val merged = mergeSources(
                    primary = sources,
                    secondary = offlineDownloadRepository.getOfflineSources(animeId),
                )
                _uiState.update { state ->
                    state.withSources(
                        sources = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        showAllItems = false,
                        allSourcesLoaded = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = throwable.toUiMessage().takeIf { _ -> it.items.isEmpty() }
                    )
                }
            }
        }
    }

    fun loadMore() {
        loadAllSources(forceRefresh = false)
    }

    private fun loadMore(forceRefresh: Boolean) {
        loadAllSources(forceRefresh = forceRefresh)
    }

    private fun loadAllSources(
        forceRefresh: Boolean,
    ) {
        val current = _uiState.value
        if (current.isLoadingMore) {
            return
        }
        if (!forceRefresh && current.allSourcesLoaded) {
            _uiState.update {
                it.copy(
                    showAllItems = true,
                    items = it.allItems,
                    hasMoreItems = false,
                    isLoadingMore = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingMore = true,
                showAllItems = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadSources(animeId = animeId, includeNonPriority = true) { updated ->
                    val merged = mergeSources(
                        primary = updated,
                        secondary = offlineDownloadRepository.getOfflineSources(animeId),
                    )
                    _uiState.update { state ->
                        state.withSources(
                            sources = merged,
                            isLoading = false,
                            isLoadingMore = true,
                            showAllItems = true,
                            allSourcesLoaded = false,
                        )
                    }
                }
            }.onSuccess { sources ->
                val merged = mergeSources(
                    primary = sources,
                    secondary = offlineDownloadRepository.getOfflineSources(animeId),
                )
                _uiState.update { state ->
                    state.withSources(
                        sources = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        showAllItems = true,
                        allSourcesLoaded = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = throwable.toUiMessage().takeIf { _ -> it.items.isEmpty() }
                    )
                }
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    private fun WatchSourcesScreenState.withSources(
        sources: List<WatchSource>,
        isLoading: Boolean,
        isLoadingMore: Boolean,
        showAllItems: Boolean,
        allSourcesLoaded: Boolean,
    ): WatchSourcesScreenState {
        val visibleItems = visibleItems(sources, showAllItems)
        return copy(
            allItems = sources,
            items = visibleItems,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMoreItems = hasMoreItems(
                allItems = sources,
                visibleItems = visibleItems,
                showAllItems = showAllItems,
                allSourcesLoaded = allSourcesLoaded,
            ),
            showAllItems = showAllItems,
            priorityLoadCompleted = !isLoading,
            allSourcesLoaded = allSourcesLoaded,
            errorMessage = null,
        )
    }

    private fun visibleItems(
        allItems: List<WatchSource>,
        showAllItems: Boolean,
    ): List<WatchSource> {
        if (showAllItems) {
            return allItems
        }
        return allItems.take(INITIAL_VISIBLE_SOURCE_COUNT)
    }

    private fun hasMoreItems(
        allItems: List<WatchSource>,
        visibleItems: List<WatchSource>,
        showAllItems: Boolean,
        allSourcesLoaded: Boolean,
    ): Boolean {
        if (showAllItems) return false
        return allItems.size > visibleItems.size || !allSourcesLoaded
    }

    private fun mergeSources(
        primary: List<WatchSource>,
        secondary: List<WatchSource>,
    ): List<WatchSource> {
        return (primary + secondary)
            .associateBy(WatchSource::sourceId)
            .values
            .toList()
    }

    class Factory(
        private val animeId: String,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WatchSourcesViewModel(
                animeId = animeId,
                repository = AnimeWatchRepository(context.applicationContext),
                offlineDownloadRepository = OfflineDownloadRepository(context.applicationContext),
            ) as T
        }
    }

    private companion object {
        const val INITIAL_VISIBLE_SOURCE_COUNT = 6
    }
}

data class WatchSourcesScreenState(
    val allItems: List<WatchSource> = emptyList(),
    val items: List<WatchSource> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreItems: Boolean = false,
    val showAllItems: Boolean = false,
    val priorityLoadCompleted: Boolean = false,
    val allSourcesLoaded: Boolean = false,
    val errorMessage: String? = null,
)

internal fun Throwable.toUiMessage(): String {
    return when (this) {
        is SourceException -> message ?: "Не удалось загрузить озвучки"
        else -> message ?: "Не удалось загрузить данные"
    }
}
