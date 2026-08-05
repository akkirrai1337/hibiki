package org.akkirrai.hibiki.desktop
import org.akkirrai.hibiki.desktop.data.*

import java.util.UUID
import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.PlaybackStreamType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DesktopPlaybackProgressRepositoryTest {
    private lateinit var preferences: Preferences
    private lateinit var repository: DesktopPlaybackProgressRepository

    @Before
    fun setUp() {
        preferences = Preferences.userRoot().node("hibiki-progress-tests/${UUID.randomUUID()}")
        repository = DesktopPlaybackProgressRepository(preferences)
    }

    @After
    fun tearDown() {
        preferences.removeNode()
    }

    @Test
    fun savesAndRestoresResumePositionAndActivityDelta() {
        val context = PlaybackContext(
            titleId = "title-1",
            sourceId = "source-1",
            episodeId = "episode-1",
            episodeNumber = 1.0,
            sourceTitle = "Dub",
        )
        val stream = PlaybackStream(
            animeTitle = "Title",
            sourceTitle = "Dub",
            episodeTitle = "Episode 1",
            streamUrl = "https://example.test/video.mp4",
            streamType = PlaybackStreamType.MP4,
            qualityLabel = "1080p",
        )

        repository.saveEpisodeProgress(context, stream, positionMs = 1_000L, durationMs = 10_000L)
        repository.saveEpisodeProgress(context, stream, positionMs = 3_000L, durationMs = 10_000L)

        val progress = repository.getPlaybackProgress("title-1", "episode-1")
        assertEquals(3_000L, progress?.positionMs)
        assertEquals(10_000L, progress?.durationMs)
        assertEquals("1080p", progress?.quality)
        assertEquals(3_000L, repository.getDailyWatchActivity().sumOf { it.watchedMs })
    }
}
