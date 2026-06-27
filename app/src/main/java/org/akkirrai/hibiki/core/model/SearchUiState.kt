package org.akkirrai.hibiki.core.model

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Content(
        val items: List<Anime>,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : SearchUiState
}
