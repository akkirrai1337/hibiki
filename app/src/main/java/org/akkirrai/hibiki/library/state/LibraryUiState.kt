package org.akkirrai.hibiki.library.state

import org.akkirrai.hibiki.library.*

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val selectedCategory: LibraryCategory = LibraryCategory.Watching,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchFilters: LibrarySearchFilters = LibrarySearchFilters(),
) {
    val orderedCategories: List<LibraryCategory>
        get() = orderedLibraryCategories(entries)

    val visibleEntries: List<LibraryEntry>
        get() {
            val normalizedQuery = searchQuery.trim()
            return entries
                .filter { entry ->
                    entry.category == selectedCategory &&
                        searchFilters.matches(entry) &&
                        (normalizedQuery.isBlank() ||
                            entry.anime.title.contains(normalizedQuery, ignoreCase = true) ||
                            entry.anime.subtitle.contains(normalizedQuery, ignoreCase = true))
                }
                .sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
        }

    val categoryCounts: Map<LibraryCategory, Int>
        get() = entries.groupingBy { it.category }.eachCount()

    val filterCatalog: LibraryFilterCatalog
        get() {
            val categoryEntries = entries.filter { it.category == selectedCategory }
            return LibraryFilterCatalog(
                typeOptions = categoryEntries.mapNotNull { it.anime.extractLibraryType() }.distinct().sorted(),
                statusOptions = categoryEntries.map { it.anime.status.trim() }
                    .filter(String::isNotBlank).distinct().sorted(),
                genreOptions = categoryEntries.flatMap { it.anime.genres }
                    .map(String::trim)
                    .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
                    .distinct().sorted(),
            )
        }
}

fun orderedLibraryCategories(entries: List<LibraryEntry>): List<LibraryCategory> {
    // Recent is a hidden bookkeeping flag, never a selectable tab -- unlike Saved, which is a
    // real (if special-ordered) category.
    val visibleCategories = LibraryCategory.entries.filter { it != LibraryCategory.Recent }
    return if (entries.any { it.category == LibraryCategory.Saved }) {
        listOf(LibraryCategory.Saved) + visibleCategories.filter { it != LibraryCategory.Saved }
    } else {
        visibleCategories
    }
}
