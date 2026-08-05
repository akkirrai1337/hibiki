package org.akkirrai.hibiki.shared.home.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.akkirrai.hibiki.shared.home.model.TrendingAnimeUiState

/** Lifecycle-neutral state holder for the trending screen. */
class TrendingPresenter(
    initialState: TrendingAnimeUiState = TrendingAnimeUiState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<TrendingAnimeUiState> = _state.asStateFlow()

    fun update(transform: (TrendingAnimeUiState) -> TrendingAnimeUiState) {
        _state.update(transform)
    }

    fun setState(state: TrendingAnimeUiState) {
        _state.value = state
    }
}
