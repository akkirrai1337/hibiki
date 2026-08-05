package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.AppWatchSourcesContent
import org.akkirrai.hibiki.shared.player.WatchSourcesScreenState
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedWatchSourcesComposeTest {
    @Test
    fun sourceRowDeliversSelectedCommonWatchSource() = runComposeUiTest {
        val source = WatchSource(
            sourceId = "title:ani-liberty:0",
            title = "AniLiberty",
            episodeCount = 12,
        )
        var selectedSource: WatchSource? = null

        setContent {
            MaterialTheme {
                AppWatchSourcesContent(
                    state = WatchSourcesScreenState(
                        allItems = listOf(source),
                        items = listOf(source),
                        isLoading = false,
                    ),
                    emptyTitle = "No sources",
                    emptyMessage = "No sources available",
                    retryLabel = "Retry",
                    episodeLabel = "episodes",
                    loadMoreLabel = "Load more",
                    enabled = true,
                    onRetry = {},
                    onSourceClick = { selectedSource = it },
                    onLoadMore = {},
                )
            }
        }

        onNodeWithText("AniLiberty")
            .assertIsDisplayed()
            .performClick()

        assertEquals(source, selectedSource)
    }
}
