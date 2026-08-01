package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.details.AppDetailsScreen
import org.akkirrai.hibiki.shared.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedDetailsComposeTest {
    @Test
    fun detailsScreenRendersCommonContentAndWatchAction() = runComposeUiTest {
        val anime = Anime(
            id = "ani-liberty:test-title",
            title = "Test anime",
            subtitle = "TV | 2026",
            episodesLabel = "12 episodes",
            status = "Ongoing",
            description = "A shared description",
        )
        var watchClicks = 0

        setContent {
            MaterialTheme {
                AppDetailsScreen(
                    anime = anime,
                    onBackClick = {},
                    onRelatedAnimeClick = {},
                    canWatch = true,
                    onWatchClick = { watchClicks++ },
                )
            }
        }

        onNodeWithText("Test anime")
            .assertIsDisplayed()
        onNodeWithText("A shared description")
            .assertIsDisplayed()
        onNodeWithText("Watch")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, watchClicks)
    }

    @Test
    fun detailsScreenBackButtonDeliversCommonBackAction() = runComposeUiTest {
        var backClicks = 0

        setContent {
            MaterialTheme {
                AppDetailsScreen(
                    anime = Anime(
                        id = "ani-liberty:test-title",
                        title = "Test anime",
                        subtitle = "TV | 2026",
                        episodesLabel = "12 episodes",
                        status = "Ongoing",
                    ),
                    onBackClick = { backClicks++ },
                    onRelatedAnimeClick = {},
                )
            }
        }

        onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun detailsPosterPreviewUsesCommonDismissContract() = runComposeUiTest {
        val posterPreviewOpen = mutableStateOf(true)

        setContent {
            MaterialTheme {
                AppDetailsScreen(
                    anime = Anime(
                        id = "ani-liberty:test-title",
                        title = "Test anime",
                        subtitle = "TV | 2026",
                        episodesLabel = "12 episodes",
                        status = "Ongoing",
                    ),
                    onBackClick = {},
                    onRelatedAnimeClick = {},
                    posterPreviewOpen = posterPreviewOpen.value,
                    onPosterPreviewOpenChange = { posterPreviewOpen.value = it },
                )
            }
        }

        assertEquals(true, posterPreviewOpen.value)
        onAllNodesWithContentDescription("Back")
            .assertCountEquals(2)
            .get(1)
            .assertIsDisplayed()
            .performClick()
        waitForIdle()

        assertEquals(false, posterPreviewOpen.value)
    }

    @Test
    fun detailsTitleSheetOpensFromCommonHeroTitle() = runComposeUiTest {
        val titleSheetOpen = mutableStateOf(false)

        setContent {
            MaterialTheme {
                AppDetailsScreen(
                    anime = Anime(
                        id = "ani-liberty:test-title",
                        title = "Test anime",
                        subtitle = "TV | 2026",
                        episodesLabel = "12 episodes",
                        status = "Ongoing",
                        description = "A shared description",
                    ),
                    onBackClick = {},
                    onRelatedAnimeClick = {},
                    titleSheetOpen = titleSheetOpen.value,
                    onTitleSheetOpenChange = { titleSheetOpen.value = it },
                )
            }
        }

        onNodeWithText("Test anime")
            .assertIsDisplayed()
            .performClick()
        waitForIdle()

        assertEquals(true, titleSheetOpen.value)
        onAllNodesWithText("A shared description")
            .assertCountEquals(2)
            .get(1)
            .assertIsDisplayed()
    }
}
