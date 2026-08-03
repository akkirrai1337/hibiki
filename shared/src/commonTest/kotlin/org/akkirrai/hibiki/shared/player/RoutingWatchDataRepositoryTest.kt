package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackStreamType
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource

class RoutingWatchDataRepositoryTest {
    @Test
    fun routesExternalTitleAndWatchSourceToExternalDelegate() = runBlocking {
        val builtIn = FakeWatchDataRepository("built-in")
        val external = FakeWatchDataRepository("external")
        val repository = RoutingWatchDataRepository(
            builtIn = builtIn,
            external = external,
            isExternalSource = { it.value == "external-source" },
        )

        repository.loadSources("source:external-source:title-1")
        repository.refreshSources("source:external-source:title-1")
        repository.getEpisodes("source:external-source:title-1|watch|dub-0")
        repository.getPlayerLinks("source:external-source:title-1|watch|dub-0", "episode-1")
        repository.getPlaybackSettingsOptions("source:external-source:title-1|watch|dub-0", "episode-1")
        repository.resolvePlayback("source:external-source:title-1|watch|dub-0", "episode-1")

        assertEquals(listOf("external", "external", "external", "external", "external", "external"), external.calls)
        assertEquals(emptyList(), builtIn.calls)
    }

    @Test
    fun keepsBuiltInDelegateForUnscopedOrBuiltInTitles() = runBlocking {
        val builtIn = FakeWatchDataRepository("built-in")
        val external = FakeWatchDataRepository("external")
        val repository = RoutingWatchDataRepository(
            builtIn = builtIn,
            external = external,
            isExternalSource = { it.value == "external-source" },
        )

        repository.loadSources("source:built-in:title-1")
        repository.getEpisodes("legacy-title|watch|dub-0")

        assertEquals(listOf("built-in", "built-in"), builtIn.calls)
        assertEquals(emptyList(), external.calls)
    }

    private class FakeWatchDataRepository(private val name: String) : WatchDataRepository {
        val calls = mutableListOf<String>()

        override suspend fun loadSources(animeId: String): List<WatchSource> {
            calls += name
            return emptyList()
        }

        override suspend fun refreshSources(animeId: String): List<WatchSource> {
            calls += name
            return emptyList()
        }

        override suspend fun getEpisodes(sourceId: String): List<WatchEpisode> {
            calls += name
            return emptyList()
        }

        override suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink> {
            calls += name
            return emptyList()
        }

        override suspend fun getPlaybackSettingsOptions(
            sourceId: String,
            episodeId: String,
        ): PlaybackSettingsOptions {
            calls += name
            return PlaybackSettingsOptions()
        }

        override suspend fun resolvePlayback(
            sourceId: String,
            episodeId: String,
            preferredQuality: String?,
            preferredPlayerName: String?,
            forceRefresh: Boolean,
        ): PlaybackStream {
            calls += name
            return PlaybackStream(
                animeTitle = "Title",
                sourceTitle = name,
                episodeTitle = "Episode 1",
                streamUrl = "https://example.test/video.mp4",
                streamType = PlaybackStreamType.MP4,
            )
        }

        override fun close() = Unit
    }
}
