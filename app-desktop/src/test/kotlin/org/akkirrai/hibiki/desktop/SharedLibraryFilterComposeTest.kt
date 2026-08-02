package org.akkirrai.hibiki.desktop

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.library.AppLibraryScreen
import org.akkirrai.hibiki.shared.library.AppLibraryScreenLabels
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.LibraryUiState
import org.akkirrai.hibiki.shared.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedLibraryFilterComposeTest {
    @Test
    fun libraryFilterSlotOpensAndDismissesThroughCommonContract() = runComposeUiTest {
        val filterVisible = mutableStateOf(false)
        var visibilityChanges = 0

        setContent {
            MaterialTheme {
                AppLibraryScreen(
                    state = LibraryUiState(),
                    labels = libraryLabels(),
                    bottomContentPadding = 24.dp,
                    onAnimeClick = {},
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onFilterClick = {},
                    onCategorySelected = {},
                    entryContent = { _, _: Modifier -> },
                    filterVisible = filterVisible.value,
                    onFilterVisibilityChange = {
                        filterVisible.value = it
                        visibilityChanges++
                    },
                    filterContent = { onDismiss ->
                        Button(onClick = onDismiss) { Text("Dismiss") }
                    },
                )
            }
        }

        onNodeWithContentDescription("Filters")
            .assertIsDisplayed()
            .performClick()
        waitForIdle()
        assertEquals(true, filterVisible.value)
        onNodeWithText("Dismiss")
            .assertIsDisplayed()
            .performClick()
        waitForIdle()

        assertEquals(false, filterVisible.value)
        assertEquals(2, visibilityChanges)
    }

    @Test
    fun libraryCategorySelectionUsesCommonStateCallback() = runComposeUiTest {
        var selectedCategory: LibraryCategory? = null
        val entries = listOf(
            LibraryEntry(
                anime = Anime("watching", "Watching title", "TV", "1", "Ongoing"),
                category = LibraryCategory.Watching,
            ),
            LibraryEntry(
                anime = Anime("completed", "Completed title", "TV", "1", "Released"),
                category = LibraryCategory.Completed,
            ),
        )

        setContent {
            MaterialTheme {
                AppLibraryScreen(
                    state = LibraryUiState(entries = entries),
                    labels = libraryLabels(),
                    bottomContentPadding = 24.dp,
                    onAnimeClick = {},
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onFilterClick = {},
                    onCategorySelected = { selectedCategory = it },
                    entryContent = { _, _: Modifier -> },
                )
            }
        }

        onNodeWithText("Completed 1")
            .assertIsDisplayed()
            .performClick()

        assertEquals(LibraryCategory.Completed, selectedCategory)
    }

    private fun libraryLabels() = AppLibraryScreenLabels(
        searchPlaceholder = "Search",
        filterContentDescription = "Filters",
        clearContentDescription = "Clear",
        categoryLabels = LibraryCategory.entries.associateWith { it.name },
        emptyTitle = "Empty",
        emptyMessage = "No items",
        filteredTitle = "Filtered",
        searchTitle = "Search empty",
        filteredMessage = "No filtered items",
        categoryEmptyLabels = LibraryCategory.entries.associateWith { "No ${it.name}" },
        announcementLabel = "Announcement",
        movieLabel = "Movie",
        libraryStatusLabel = { it.name },
    )
}
