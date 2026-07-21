package org.akkirrai.hibiki.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.hibiki.app.di.hibikiDependencies
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
        loadSources(forceRefresh = false)
    }

    fun retry() {
        val current = _uiState.value
        if (current.items.isEmpty()) {
            loadSources(forceRefresh = true)
        } else {
            _uiState.update {
                it.copy(
                    showAllItems = true,
                    items = it.allItems,
                    hasMoreItems = false,
                    isLoadingMore = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun loadSources(forceRefresh: Boolean) {
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
                errorMessage = null,
            )
        } else {
            _uiState.value = WatchSourcesScreenState(
                allItems = cachedSources,
                items = cachedVisibleItems,
                isLoading = cached == null,
                isLoadingMore = false,
                hasMoreItems = hasMoreItems(
                    allItems = cachedSources,
                    visibleItems = cachedVisibleItems,
                    showAllItems = false,
                ),
                showAllItems = false,
                errorMessage = null,
            )
        }
        if (!forceRefresh && cached != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadSources(animeId = animeId) { updated ->
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
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
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
        _uiState.update {
            it.copy(
                showAllItems = true,
                items = it.allItems,
                hasMoreItems = false,
                isLoadingMore = false,
                errorMessage = null,
            )
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
            ),
            showAllItems = showAllItems,
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
    ): Boolean {
        if (showAllItems) return false
        return allItems.size > visibleItems.size
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
            val dependencies = context.applicationContext.hibikiDependencies()
            return WatchSourcesViewModel(
                animeId = animeId,
                repository = dependencies.animeWatchRepository(),
                offlineDownloadRepository = dependencies.offlineDownloadRepository(),
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
    val errorMessage: String? = null,
)

internal fun Throwable.toUiMessage(): String {
    return when (this) {
        is SourceException -> message ?: "Не удалось загрузить озвучки"
        else -> message ?: "Не удалось загрузить данные"
    }
}
