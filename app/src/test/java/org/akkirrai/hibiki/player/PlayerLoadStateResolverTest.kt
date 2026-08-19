package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.PlaybackStreamType
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource

class PlayerLoadStateResolverTest {
    @Test
    fun loadingMarksExcludedStreamAndRecoveryAttempt() {
        val state = PlayerUiState(failedStreamUrls = setOf("old"))
            .beginPlaybackLoad(setOf("failed"))

        assertTrue(state.isLoading)
        assertEquals(setOf("old", "failed"), state.failedStreamUrls)
        assertTrue(state.recoveryAttempted)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun loadingKeepsCurrentPlaybackForTheLoadingOverlay() {
        val stream = PlaybackStream(
            animeTitle = "Anime",
            sourceTitle = "Source",
            episodeTitle = "Episode",
            streamUrl = "https://video",
            streamType = PlaybackStreamType.HLS,
        )

        val state = PlayerUiState(playback = stream)
            .beginPlaybackLoad(emptySet())

        assertTrue(state.isLoading)
        assertEquals(stream, state.playback)
    }

    @Test
    fun loadedPlaybackUpdatesEpisodeAndClearsRecoveredStream() {
        val stream = PlaybackStream(
            animeTitle = "  Anime  ",
            sourceTitle = "Source",
            episodeTitle = "Episode",
            streamUrl = "https://video",
            streamType = PlaybackStreamType.HLS,
            qualityLabel = "1080p",
        )
        val state = PlayerUiState(
            failedStreamUrls = setOf("https://video"),
            pendingSeekMs = 100L,
            recoveryAttempted = true,
        ).withPlaybackLoaded(
            stream = stream,
            episodes = listOf(WatchEpisode("ep-2", 2.0, "Two")),
            episodeId = "ep-2",
            episodeNumber = 2.0,
            savedSeekMs = null,
        )

        assertFalse(state.isLoading)
        assertEquals(stream, state.playback)
        assertEquals("Anime", state.animeTitle)
        assertEquals(100L, state.pendingSeekMs)
        assertEquals(emptySet(), state.failedStreamUrls)
        assertFalse(state.recoveryAttempted)
    }

    @Test
    fun failureClearsPlaybackButKeepsResolvedEpisode() {
        val state = PlayerUiState(playback = PlaybackStream(
            animeTitle = "Anime",
            sourceTitle = "Source",
            episodeTitle = "Episode",
            streamUrl = "https://video",
            streamType = PlaybackStreamType.MP4,
        )).withPlaybackError(
            message = "failure",
            episodes = emptyList(),
            episodeId = "ep-1",
            episodeNumber = 1.0,
        )

        assertFalse(state.isLoading)
        assertEquals(null, state.playback)
        assertEquals("failure", state.errorMessage)
        assertEquals("ep-1", state.currentEpisodeId)
    }

    @Test
    fun keepsTheFailedPlaybackRequestForExactRetry() {
        val request = PlaybackRequest(
            episode = WatchEpisode("episode-1", 1.0, "Episode"),
            source = WatchSource("source-1", "Source", 12),
            preferredPlayerName = "mpv",
            preferredQuality = "1080p",
        )
        val state = PlayerUiState(lastPlaybackRequest = request)
            .withPlaybackError("failed", emptyList(), request.episode.id, request.episode.number)

        assertEquals(request, state.lastPlaybackRequest)
    }

    @Test
    fun resetForNavigationClearsPlaybackErrorAndRetryRequest() {
        val request = PlaybackRequest(
            episode = WatchEpisode("episode-1", 1.0, "Episode"),
            source = WatchSource("source-1", "Source", 12),
        )
        val state = PlayerUiState(
            isLoading = true,
            playback = PlaybackStream(
                animeTitle = "Anime",
                sourceTitle = "Source",
                episodeTitle = "Episode",
                streamUrl = "https://video",
                streamType = PlaybackStreamType.MP4,
            ),
            errorMessage = "failed",
            lastPlaybackRequest = request,
        ).resetForNavigation()

        assertFalse(state.isLoading)
        assertEquals(null, state.playback)
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.lastPlaybackRequest)
    }
}
