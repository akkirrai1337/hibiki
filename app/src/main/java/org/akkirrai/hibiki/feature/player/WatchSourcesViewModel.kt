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
import org.akkirrai.hibiki.shared.player.initialWatchSourcesState
import org.akkirrai.hibiki.shared.player.showAllWatchSources
import org.akkirrai.hibiki.shared.player.withLoadedSources
import org.akkirrai.hibiki.shared.player.withWatchSourcesError

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
            presenter.update(WatchSourcesScreenState::showAllWatchSources)
        }
    }

    private fun loadSources(forceRefresh: Boolean) {
        val cached = repository.getCachedSources(animeId)
        val offlineSources = offlineDownloadRepository.getOfflineSources(animeId)
        presenter.setState(
            initialWatchSourcesState(
                cachedSources = cached?.sources,
                offlineSources = offlineSources,
                forceRefresh = forceRefresh,
            ),
        )
        if (!forceRefresh && cached != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.loadSources(animeId = animeId) { updated ->
                    val currentOfflineSources = offlineDownloadRepository.getOfflineSources(animeId)
                    presenter.update { state ->
                        state.withLoadedSources(
                            sources = updated,
                            offlineSources = currentOfflineSources,
                            isLoading = true,
                        )
                    }
                }
            }.onSuccess { sources ->
                val currentOfflineSources = offlineDownloadRepository.getOfflineSources(animeId)
                presenter.update { state ->
                    state.withLoadedSources(
                        sources = sources,
                        offlineSources = currentOfflineSources,
                        isLoading = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                presenter.update { it.withWatchSourcesError(throwable.toUiMessage()) }
            }
        }
    }

    fun loadMore() {
        presenter.update(WatchSourcesScreenState::showAllWatchSources)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
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
