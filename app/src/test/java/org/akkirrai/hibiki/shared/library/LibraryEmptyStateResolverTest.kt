package org.akkirrai.hibiki.shared.library
import org.akkirrai.hibiki.shared.library.presentation.*
import org.akkirrai.hibiki.shared.library.state.*
import org.akkirrai.hibiki.shared.library.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.library.ui.resolveLibraryEmptyStateText

class LibraryEmptyStateResolverTest {
    private val categoryLabels = mapOf(
        LibraryCategory.Watching to "Nothing watching",
        LibraryCategory.Completed to "Nothing completed",
    )

    @Test
    fun unfilteredLibraryUsesBaseEmptyState() {
        assertEquals(
            LibraryEmptyStateText(
                title = "Library is empty",
                message = "Add a title to see it here",
            ),
            resolveLibraryEmptyStateText(
                filtered = false,
                searchQuery = "query",
                category = LibraryCategory.Completed,
                emptyTitle = "Library is empty",
                emptyMessage = "Add a title to see it here",
                filteredTitle = "Category is empty",
                searchTitle = "Nothing found",
                filteredMessage = "No matches",
                categoryLabels = categoryLabels,
            ),
        )
    }

    @Test
    fun filteredCategoryUsesCategoryEmptyStateWhenSearchIsBlank() {
        assertEquals(
            LibraryEmptyStateText(
                title = "Category is empty",
                message = "Nothing watching",
            ),
            resolveLibraryEmptyStateText(
                filtered = true,
                searchQuery = "  ",
                category = LibraryCategory.Watching,
                emptyTitle = "Library is empty",
                emptyMessage = "Add a title to see it here",
                filteredTitle = "Category is empty",
                searchTitle = "Nothing found",
                filteredMessage = "No matches",
                categoryLabels = categoryLabels,
            ),
        )
    }

    @Test
    fun filteredSearchUsesSearchEmptyStateWhenQueryIsPresent() {
        assertEquals(
            LibraryEmptyStateText(
                title = "Nothing found",
                message = "No matches",
            ),
            resolveLibraryEmptyStateText(
                filtered = true,
                searchQuery = "naruto",
                category = LibraryCategory.Watching,
                emptyTitle = "Library is empty",
                emptyMessage = "Add a title to see it here",
                filteredTitle = "Category is empty",
                searchTitle = "Nothing found",
                filteredMessage = "No matches",
                categoryLabels = categoryLabels,
            ),
        )
    }
}
