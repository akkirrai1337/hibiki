package org.akkirrai.hibiki.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource

data class WatchSourcesScreenState(
    val allItems: List<WatchSource> = emptyList(),
    val items: List<WatchSource> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreItems: Boolean = false,
    val showAllItems: Boolean = false,
    val errorMessage: String? = null,
)

fun WatchSourcesScreenState.isRefreshing(): Boolean = isLoading && items.isNotEmpty()

class WatchSourcesPresenter(
    initialState: WatchSourcesScreenState = WatchSourcesScreenState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<WatchSourcesScreenState> = _state.asStateFlow()

    fun update(transform: (WatchSourcesScreenState) -> WatchSourcesScreenState) {
        _state.update(transform)
    }

    fun setState(state: WatchSourcesScreenState) {
        _state.value = state
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

class EpisodesPresenter(
    initialState: EpisodesScreenState = EpisodesScreenState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<EpisodesScreenState> = _state.asStateFlow()

    fun update(transform: (EpisodesScreenState) -> EpisodesScreenState) {
        _state.update(transform)
    }

    fun setState(state: EpisodesScreenState) {
        _state.value = state
    }
}
