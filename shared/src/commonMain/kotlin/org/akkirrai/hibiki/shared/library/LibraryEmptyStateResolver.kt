package org.akkirrai.hibiki.shared.library

data class LibraryEmptyStateText(
    val title: String,
    val message: String,
)

fun resolveLibraryEmptyStateText(
    filtered: Boolean,
    searchQuery: String,
    category: LibraryCategory,
    emptyTitle: String,
    emptyMessage: String,
    filteredTitle: String,
    searchTitle: String,
    filteredMessage: String,
    categoryLabels: Map<LibraryCategory, String>,
): LibraryEmptyStateText {
    if (!filtered) return LibraryEmptyStateText(emptyTitle, emptyMessage)

    return LibraryEmptyStateText(
        title = if (searchQuery.isBlank()) filteredTitle else searchTitle,
        message = if (searchQuery.isBlank()) {
            resolveLibraryEmptyStateMessage(category, categoryLabels)
        } else {
            filteredMessage
        },
    )
}
