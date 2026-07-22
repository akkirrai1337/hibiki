package org.akkirrai.hibiki.shared.search

import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.SearchUiState
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SearchScreenState(
    val query: String = "",
    val filterCatalog: AnimeCatalogFilterCatalog? = null,
    val isFilterCatalogLoading: Boolean = false,
    val result: SearchUiState = SearchUiState.Idle,
)

class SearchPresenter(
    initialState: SearchScreenState = SearchScreenState(),
) {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(initialState)
    val state: kotlinx.coroutines.flow.StateFlow<SearchScreenState> = _state.asStateFlow()

    fun update(transform: (SearchScreenState) -> SearchScreenState) {
        _state.update(transform)
    }

    fun setState(state: SearchScreenState) {
        _state.value = state
    }
}
