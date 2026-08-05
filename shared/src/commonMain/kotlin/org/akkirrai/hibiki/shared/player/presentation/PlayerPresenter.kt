package org.akkirrai.hibiki.shared.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Lifecycle-neutral state holder for platform-specific playback hosts. */
class PlayerPresenter(
    initialState: PlayerUiState = PlayerUiState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun update(transform: (PlayerUiState) -> PlayerUiState) {
        _state.update(transform)
    }

    fun setState(state: PlayerUiState) {
        _state.value = state
    }
}
