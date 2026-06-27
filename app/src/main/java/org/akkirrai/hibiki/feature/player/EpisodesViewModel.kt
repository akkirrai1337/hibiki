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
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.source.AnimeWatchRepository

class EpisodesViewModel(
    private val sourceId: String,
    private val repository: AnimeWatchRepository,
    private val offlineDownloadRepository: OfflineDownloadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EpisodesScreenState())
    val uiState: StateFlow<EpisodesScreenState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val offlineEpisodes = offlineDownloadRepository.getOfflineEpisodes(sourceId)
        _uiState.update {
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
                    _uiState.update {
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
                    _uiState.update {
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
            return EpisodesViewModel(
                sourceId = sourceId,
                repository = AnimeWatchRepository(context.applicationContext),
                offlineDownloadRepository = OfflineDownloadRepository(context.applicationContext),
            ) as T
        }
    }
}

data class EpisodesScreenState(
    val result: EpisodesUiState = EpisodesUiState.Loading,
)

sealed interface EpisodesUiState {
    data object Loading : EpisodesUiState
    data object Empty : EpisodesUiState
    data class Error(val message: String) : EpisodesUiState
    data class Content(val items: List<WatchEpisode>) : EpisodesUiState
}
