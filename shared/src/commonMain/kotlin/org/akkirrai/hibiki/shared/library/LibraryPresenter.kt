package org.akkirrai.hibiki.shared.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Lifecycle-neutral state holder for library browsing, usable by Android and Desktop hosts. */
class LibraryPresenter(
    initialState: LibraryUiState = LibraryUiState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun updateEntries(entries: List<LibraryEntry>) {
        _state.update {
            it.copy(
                entries = entries,
                selectedCategory = preferredCategory(entries, it.selectedCategory),
            )
        }
    }

    fun setRefreshing(isRefreshing: Boolean) {
        _state.update { it.copy(isRefreshing = isRefreshing) }
    }

    fun selectCategory(category: LibraryCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _state.update { it.copy(searchQuery = "") }
    }

    fun applySearchFilters(filters: LibrarySearchFilters) {
        _state.update { it.copy(searchFilters = filters) }
    }

    fun resetSearchFilters() {
        _state.update { it.copy(searchFilters = LibrarySearchFilters()) }
    }

    private fun preferredCategory(
        entries: List<LibraryEntry>,
        current: LibraryCategory,
    ): LibraryCategory {
        if (entries.any { it.category == current }) return current
        return orderedLibraryCategories(entries)
            .firstOrNull { category -> entries.any { it.category == category } }
            ?: current
    }
}
