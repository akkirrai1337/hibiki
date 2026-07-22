package org.akkirrai.hibiki.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.EpisodesUiState

class EpisodesViewModel(
    private val sourceId: String,
    private val repository: AnimeWatchRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
) : ViewModel() {
    private val presenter = EpisodesPresenter()
    val uiState: StateFlow<EpisodesScreenState> = presenter.state

    init {
        load()
    }

    fun load() {
        val offlineEpisodes = offlineDownloadRepository.getOfflineEpisodes(sourceId)
        presenter.update {
            it.copy(
                result = if (offlineEpisodes.isEmpty()) {
                    EpisodesUiState.Loading
                } else {
                    EpisodesUiState.Content(offlineEpisodes)
                }
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.getEpisodes(sourceId) }
                .onSuccess { episodes ->
                    val merged = mergeEpisodes(
                        primary = episodes,
                        secondary = offlineDownloadRepository.getOfflineEpisodes(sourceId),
                    )
                    presenter.update {
                        it.copy(
                            result = if (merged.isEmpty()) {
                                EpisodesUiState.Empty
                            } else {
                                EpisodesUiState.Content(merged)
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    presenter.update {
                        val offline = offlineDownloadRepository.getOfflineEpisodes(sourceId)
                        it.copy(
                            result = if (offline.isNotEmpty()) {
                                EpisodesUiState.Content(offline)
                            } else {
                                EpisodesUiState.Error(throwable.toUiMessage())
                            }
                        )
                    }
                }
        }
    }

    private fun mergeEpisodes(
        primary: List<WatchEpisode>,
        secondary: List<WatchEpisode>,
    ): List<WatchEpisode> {
        return (primary + secondary)
            .associateBy(WatchEpisode::id)
            .values
            .sortedBy(WatchEpisode::number)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    class Factory(
        private val sourceId: String,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return EpisodesViewModel(
                sourceId = sourceId,
                repository = dependencies.animeWatchRepository(),
                offlineDownloadRepository = dependencies.offlineDownloadRepository(),
            ) as T
        }
    }
}
