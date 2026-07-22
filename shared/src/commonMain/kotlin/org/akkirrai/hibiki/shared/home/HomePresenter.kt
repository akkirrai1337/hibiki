package org.akkirrai.hibiki.shared.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Lifecycle-neutral state holder for Home hosts. Loading orchestration stays platform-specific. */
class HomePresenter(
    initialState: HomeUiState = HomeUiState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun update(transform: (HomeUiState) -> HomeUiState) {
        _state.update(transform)
    }

    fun setState(state: HomeUiState) {
        _state.value = state
    }
}
