package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchEpisode
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EpisodesScreenState(
    val result: EpisodesUiState = EpisodesUiState.Loading,
)

sealed interface EpisodesUiState {
    data object Loading : EpisodesUiState
    data object Empty : EpisodesUiState
    data class Error(val message: String) : EpisodesUiState
    data class Content(val items: List<WatchEpisode>) : EpisodesUiState
}

class EpisodesPresenter(
    initialState: EpisodesScreenState = EpisodesScreenState(),
) {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(initialState)
    val state: kotlinx.coroutines.flow.StateFlow<EpisodesScreenState> = _state.asStateFlow()

    fun update(transform: (EpisodesScreenState) -> EpisodesScreenState) {
        _state.update(transform)
    }

    fun setState(state: EpisodesScreenState) {
        _state.value = state
    }
}
