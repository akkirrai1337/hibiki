package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchSource
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WatchSourcesScreenState(
    val allItems: List<WatchSource> = emptyList(),
    val items: List<WatchSource> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreItems: Boolean = false,
    val showAllItems: Boolean = false,
    val errorMessage: String? = null,
)

class WatchSourcesPresenter(
    initialState: WatchSourcesScreenState = WatchSourcesScreenState(),
) {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(initialState)
    val state: kotlinx.coroutines.flow.StateFlow<WatchSourcesScreenState> = _state.asStateFlow()

    fun update(transform: (WatchSourcesScreenState) -> WatchSourcesScreenState) {
        _state.update(transform)
    }

    fun setState(state: WatchSourcesScreenState) {
        _state.value = state
    }
}
