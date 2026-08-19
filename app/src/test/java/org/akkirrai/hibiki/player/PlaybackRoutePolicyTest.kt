package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.app.navigation.AppRoute

class PlaybackRoutePolicyTest {
    @Test
    fun playbackHostRequiresPlayerRouteAndPlaybackState() {
        val player = AppRoute.Player(sourceId = "source-1", episodeId = "episode-1")

        assertTrue(shouldShowPlaybackHost(player, hasPlayback = true, hasPendingContext = false))
        assertTrue(shouldShowPlaybackHost(player, hasPlayback = false, hasPendingContext = true))
        assertFalse(shouldShowPlaybackHost(player, hasPlayback = false, hasPendingContext = false))
    }

    @Test
    fun stalePlaybackStateDoesNotShowHostOnAnotherRoute() {
        assertFalse(
            shouldShowPlaybackHost(
                currentRoute = AppRoute.Episodes(source = testWatchSource()),
                hasPlayback = true,
                hasPendingContext = true,
            ),
        )
    }
}

private fun testWatchSource() = org.akkirrai.hibiki.player.model.WatchSource(
    sourceId = "source-1",
    title = "Source",
    episodeCount = 1,
)
