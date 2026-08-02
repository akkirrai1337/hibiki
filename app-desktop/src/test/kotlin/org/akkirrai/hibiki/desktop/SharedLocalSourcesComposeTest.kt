package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.source.AppLocalSourcesScreen
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.SourceSearchSectionState
import org.akkirrai.hibiki.shared.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedLocalSourcesComposeTest {
    @Test
    fun sourceSectionDisplaysAndSelectionUsesCommonScreen() = runComposeUiTest {
        val source = AppSourceDescriptor(
            id = "ani-liberty",
            name = "AniLiberty",
            language = "RUSSIAN",
        )
        var selectedSourceId: String? = null

        setContent {
            MaterialTheme {
                AppLocalSourcesScreen(
                    sources = listOf(source),
                    selectedSourceId = null,
                    bottomContentPadding = 24.dp,
                    emptyText = "No sources",
                    languageLabel = { "Russian" },
                    onSourceSelected = { selectedSourceId = it },
                    searchQuery = "",
                    searchItems = emptyList(),
                    isSearchLoading = false,
                    searchError = false,
                    searchSourceId = "",
                    searchSourceName = "",
                    onSearchQueryChange = {},
                    onSearchClear = {},
                    searchPlaceholder = "Search",
                    searchErrorLabel = "Search failed",
                    searchRetryLabel = "Retry",
                    searchEmptyTitle = "Nothing found",
                    onSearchRetry = {},
                    onAnimeClick = {},
                    sourceIconContent = { _, _ -> },
                )
            }
        }

        onNodeWithText("Russian")
            .assertIsDisplayed()
        onNodeWithText("AniLiberty")
            .assertIsDisplayed()
            .performClick()

        assertEquals("ani-liberty", selectedSourceId)
    }

    @Test
    fun failedSearchRetriesOnlyTheReportedSource() = runComposeUiTest {
        var retriedSourceId: String? = null

        setContent {
            MaterialTheme {
                AppLocalSourcesScreen(
                    sources = emptyList(),
                    selectedSourceId = null,
                    bottomContentPadding = 24.dp,
                    emptyText = "No sources",
                    onSourceSelected = {},
                    searchQuery = "anime",
                    searchItems = emptyList(),
                    isSearchLoading = false,
                    searchError = true,
                    searchSourceId = "",
                    searchSourceName = "",
                    onSearchQueryChange = {},
                    onSearchClear = {},
                    searchPlaceholder = "Search",
                    searchErrorLabel = "Search failed",
                    searchRetryLabel = "Retry",
                    searchEmptyTitle = "Nothing found",
                    onSearchRetry = {},
                    onSearchRetryForSource = { retriedSourceId = it },
                    onAnimeClick = {},
                    searchSections = listOf(
                        SourceSearchSectionState<Anime>(
                            sourceId = "ani-liberty",
                            sourceName = "AniLiberty",
                            hasError = true,
                        ),
                    ),
                    sourceIconContent = { _, _ -> },
                )
            }
        }

        onNodeWithText("Retry")
            .assertIsDisplayed()
            .performClick()

        assertEquals("ani-liberty", retriedSourceId)
    }
}
