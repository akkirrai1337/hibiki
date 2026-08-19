package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackRoute
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.PlaybackStreamType
import org.akkirrai.hibiki.app.navigation.AppRoute

class HibikiPlaybackSessionTest {
    @Test
    fun beginRequestStoresPendingContextAndReturnRoute() {
        val session = HibikiPlaybackSession()
        val context = PlaybackContext("anime", "source", "episode", 1.0, "Dub")
        val returnRoute = AppRoute.Details("anime")

        session.beginRequest(returnRoute, context)

        assertEquals(context, session.pendingContext.value)
        assertEquals(returnRoute, session.returnRoute.value)
    }

    @Test
    fun publishPlaybackPromotesPendingContextToActiveRoute() {
        val session = HibikiPlaybackSession()
        val context = PlaybackContext("anime", "source", "episode", 1.0, "Dub")
        val playback = PlaybackStream(
            animeTitle = "Anime",
            sourceTitle = "Dub",
            episodeTitle = "Episode 1",
            streamUrl = "https://example.com/video.mp4",
            streamType = PlaybackStreamType.MP4,
        )

        session.publishPlayback(playback, context)

        assertEquals(PlaybackRoute(playback, context), session.activeRoute.value)
        assertNull(session.pendingContext.value)
    }
}
