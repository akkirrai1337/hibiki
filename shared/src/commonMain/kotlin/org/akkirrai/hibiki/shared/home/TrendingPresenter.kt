package org.akkirrai.hibiki.shared.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
