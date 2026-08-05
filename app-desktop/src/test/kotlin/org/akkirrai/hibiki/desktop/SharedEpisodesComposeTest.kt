package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.AppEpisodesContent
import org.akkirrai.hibiki.shared.player.EpisodeRow
import org.akkirrai.hibiki.shared.player.EpisodesUiState
import org.akkirrai.hibiki.shared.player.buildEpisodeHeadline
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedEpisodesComposeTest {
    @Test
    fun episodeRowDeliversSelectedCommonEpisode() = runComposeUiTest {
        val episode = WatchEpisode("episode-1", 1.0, "Pilot")
        var selectedEpisode: WatchEpisode? = null

        setContent {
            MaterialTheme {
                AppEpisodesContent(
                    result = EpisodesUiState.Content(listOf(episode)),
                    sourceTitle = "AniLiberty",
                    emptyMessage = "No episodes",
                    retryLabel = "Retry",
                    onRetry = {},
                    episodeContent = { item, shape ->
                        EpisodeRow(
                            headline = buildEpisodeHeadline("Episode 1", null),
                            subtitle = item.title,
                            inProgress = false,
                            enabled = true,
                            showDownloadAction = false,
                            shape = shape,
                            onClick = { selectedEpisode = item },
                        )
                    },
                )
            }
        }

        onNodeWithText("Episode 1")
            .assertIsDisplayed()
            .performClick()

        assertEquals(episode, selectedEpisode)
    }
}
