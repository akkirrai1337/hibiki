package org.akkirrai.hibiki.desktop

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackStreamType
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.isPlayerSettingsOverlayActive
import org.akkirrai.hibiki.shared.navigation.reduce
import org.akkirrai.hibiki.shared.player.AppPlaybackOverlayHost
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedPlaybackOverlayHostComposeTest {
    @Test
    fun selectingQualityDismissesCommonPlayerSettingsOverlay() = runComposeUiTest {
        val navigationState = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source", "episode")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
        val playback = PlaybackStream(
            animeTitle = "Title",
            sourceTitle = "Source",
            episodeTitle = "Episode 1",
            streamUrl = "https://example.test/video.mp4",
            streamType = PlaybackStreamType.MP4,
        )
        val context = PlaybackContext(
            titleId = "title",
            sourceId = "source",
            episodeId = "episode",
            episodeNumber = 1.0,
            sourceTitle = "Source",
            settingsOptions = PlaybackSettingsOptions(),
        )
        var selectedAction: PlaybackSettingsAction? = null
        var overlayEvent: AppNavigationEvent? = null

        setContent {
            AppPlaybackOverlayHost(
                playback = playback,
                context = context,
                navigationState = navigationState,
                playbackLoading = false,
                playbackError = null,
                onRetry = {},
                onDismiss = {},
                content = { _, _, _, _, _, onSettingsAction, _ ->
                    Button(onClick = {
                        onSettingsAction(PlaybackSettingsAction.SelectQuality("1080p"))
                    }) {
                        Text("Select quality")
                    }
                },
                onEpisodeSelected = {},
                onSettingsAction = { selectedAction = it },
                onOverlayEvent = { overlayEvent = it },
            )
        }

        onNodeWithText("Select quality")
            .assertIsDisplayed()
            .performClick()

        assertEquals(PlaybackSettingsAction.SelectQuality("1080p"), selectedAction)
        assertEquals(AppNavigationEvent.DismissOverlay, overlayEvent)
        assertTrue(navigationState.isPlayerSettingsOverlayActive)
    }

    @Test
    fun selectingPlaylistEpisodeDismissesCommonPlaylistOverlay() = runComposeUiTest {
        val navigationState = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source", "episode")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
        val playback = PlaybackStream(
            animeTitle = "Title",
            sourceTitle = "Source",
            episodeTitle = "Episode 1",
            streamUrl = "https://example.test/video.mp4",
            streamType = PlaybackStreamType.MP4,
        )
        val context = PlaybackContext(
            titleId = "title",
            sourceId = "source",
            episodeId = "episode",
            episodeNumber = 1.0,
            sourceTitle = "Source",
        )
        val episode = WatchEpisode("episode-2", 2.0, "Second episode")
        var selectedEpisode: WatchEpisode? = null
        var overlayEvent: AppNavigationEvent? = null

        setContent {
            AppPlaybackOverlayHost(
                playback = playback,
                context = context,
                navigationState = navigationState,
                playbackLoading = false,
                playbackError = null,
                onRetry = {},
                onDismiss = {},
                content = { _, _, _, _, onEpisodeSelected, _, _ ->
                    Button(onClick = { onEpisodeSelected(episode) }) {
                        Text("Select episode")
                    }
                },
                onEpisodeSelected = { selectedEpisode = it },
                onSettingsAction = {},
                onOverlayEvent = { overlayEvent = it },
            )
        }

        onNodeWithText("Select episode")
            .assertIsDisplayed()
            .performClick()

        assertEquals(episode, selectedEpisode)
        assertEquals(AppNavigationEvent.DismissOverlay, overlayEvent)
    }

    @Test
    fun playbackErrorOverlayDeliversRetryCallback() = runComposeUiTest {
        val context = PlaybackContext(
            titleId = "title",
            sourceId = "source",
            episodeId = "episode",
            episodeNumber = 1.0,
            sourceTitle = "Source",
        )
        var retryCount = 0

        setContent {
            AppPlaybackOverlayHost(
                playback = null,
                context = context,
                navigationState = AppNavigationState(),
                playbackLoading = false,
                playbackError = "Stream failed",
                onRetry = { retryCount++ },
                onDismiss = {},
                content = { _, _, _, _, _, _, _ -> },
                onEpisodeSelected = {},
                onSettingsAction = {},
                onOverlayEvent = {},
            )
        }

        onNodeWithText("Stream failed")
            .assertIsDisplayed()
        onNodeWithText("Retry")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retryCount)
    }
}
