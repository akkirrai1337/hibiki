package org.akkirrai.hibiki.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.player.WatchSourcesScreenState
import org.akkirrai.hibiki.shared.player.hasMoreWatchSources
import org.akkirrai.hibiki.shared.player.mergeWatchSources
import org.akkirrai.hibiki.shared.player.visibleWatchSources

class WatchSourcesViewModel(
    private val animeId: String,
    private val repository: AnimeWatchRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
) : ViewModel() {
    private val presenter = WatchSourcesPresenter()
    val uiState: StateFlow<WatchSourcesScreenState> = presenter.state

    init {
        load()
    }

    fun load() {
        loadSources(forceRefresh = false)
    }

    fun retry() {
        val current = presenter.state.value
        if (current.items.isEmpty()) {
            loadSources(forceRefresh = true)
        } else {
            presenter.update {
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
        val cachedSources = mergeWatchSources(
            primary = cached?.sources.orEmpty(),
            secondary = offlineDownloadRepository.getOfflineSources(animeId),
        )
        val cachedVisibleItems = visibleWatchSources(cachedSources, showAllItems = false)
        if (forceRefresh) {
            presenter.setState(WatchSourcesScreenState(
                allItems = emptyList(),
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMoreItems = false,
                showAllItems = false,
                errorMessage = null,
            ))
        } else {
            presenter.setState(WatchSourcesScreenState(
                allItems = cachedSources,
                items = cachedVisibleItems,
                isLoading = cached == null,
                isLoadingMore = false,
                hasMoreItems = hasMoreWatchSources(
                    allItems = cachedSources,
                    visibleItems = cachedVisibleItems,
                    showAllItems = false,
                ),
                showAllItems = false,
                errorMessage = null,
            ))
        }
        if (!forceRefresh && cached != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadSources(animeId = animeId) { updated ->
                    val merged = mergeWatchSources(
                        primary = updated,
                        secondary = offlineDownloadRepository.getOfflineSources(animeId),
                    )
                    presenter.update { state ->
                        state.withSources(
                            sources = merged,
                            isLoading = true,
                            isLoadingMore = false,
                            showAllItems = false,
                        )
                    }
                }
            }.onSuccess { sources ->
                val merged = mergeWatchSources(
                    primary = sources,
                    secondary = offlineDownloadRepository.getOfflineSources(animeId),
                )
                presenter.update { state ->
                    state.withSources(
                        sources = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        showAllItems = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                presenter.update {
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
        presenter.update {
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
        val visibleItems = visibleWatchSources(sources, showAllItems)
        return copy(
            allItems = sources,
            items = visibleItems,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMoreItems = hasMoreWatchSources(
                allItems = sources,
                visibleItems = visibleItems,
                showAllItems = showAllItems,
            ),
            showAllItems = showAllItems,
            errorMessage = null,
        )
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

}

internal fun Throwable.toUiMessage(): String {
    return when (this) {
        is SourceException -> message ?: "Не удалось загрузить озвучки"
        else -> message ?: "Не удалось загрузить данные"
    }
}
