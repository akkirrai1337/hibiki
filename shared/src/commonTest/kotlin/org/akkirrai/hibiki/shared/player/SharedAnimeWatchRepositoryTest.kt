package org.akkirrai.hibiki.shared.player

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.ExternalSourcePlaybackRuntime
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.RuntimeBackedPlaybackAnimeSource
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType

class SharedAnimeWatchRepositoryTest {
    @Test
    fun externalSourceFactoryFeedsWatchSourcesAndEpisodes() = runBlocking {
        val client = HttpClient()
        val sourceClient = HttpClient()
        val sourceId = SourceId("external-source")
        val group = PlaybackGroup(
            id = "group-1",
            title = "Dub",
            episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
        )
        val source = RuntimeBackedPlaybackAnimeSource(
            info = SourceInfo(
                id = sourceId,
                name = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
                capabilities = setOf(org.akkirrai.beakokit.api.SourceCapability.PLAYBACK),
            ),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = FakePlaybackRuntime(group),
        )
        val repository = SharedAnimeWatchRepository(
            client = client,
            sourceHttpClient = sourceClient,
            externalSourceFactory = { requestedId, context ->
                assertSame(sourceClient, context.httpClient)
                assertEquals("configured", context.config.value("setting"))
                source.takeIf { requestedId == sourceId }
            },
            sourceConfigProvider = {
                MapSourceConfig(values = mapOf("setting" to "configured"))
            },
        )

        val watchSources = repository.loadSources("source:external-source:title-1")
        val episodes = repository.getEpisodes(watchSources.single().sourceId)
        val playerLinks = repository.getPlayerLinks(watchSources.single().sourceId, episodes.single().id)
        val settings = repository.getPlaybackSettingsOptions(
            watchSources.single().sourceId,
            episodes.single().id,
        )

        assertEquals("Dub", watchSources.single().title)
        assertEquals(listOf("episode-1"), episodes.map { it.id })
        assertEquals("https://example.test/video.mp4", playerLinks.single().url)
        assertEquals(listOf("Dub"), settings.voiceovers.map { it.title })
        val persistedSourceId = watchSources.single().sourceId
        repository.invalidateSource(sourceId)
        assertEquals(listOf("episode-1"), repository.getEpisodes(persistedSourceId).map { it.id })
        repository.close()
        sourceClient.close()
    }

    @Test
    fun invalidatingSourceRecreatesItWithTheLatestConfiguration() = runBlocking {
        val client = HttpClient()
        val sourceId = SourceId("external-source")
        var creations = 0
        val source = RuntimeBackedPlaybackAnimeSource(
            info = SourceInfo(
                id = sourceId,
                name = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
                capabilities = setOf(org.akkirrai.beakokit.api.SourceCapability.PLAYBACK),
            ),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = FakePlaybackRuntime(
                PlaybackGroup("group-1", "Dub", listOf(Episode("episode-1", 1.0, "Episode 1"))),
            ),
        )
        val repository = SharedAnimeWatchRepository(
            client = client,
            externalSourceFactory = { requestedId, _ ->
                if (requestedId == sourceId) {
                    creations++
                    source
                } else {
                    null
                }
            },
        )

        repository.loadSources("source:external-source:title-1")
        repository.invalidateSource(sourceId)
        repository.loadSources("source:external-source:title-1")

        assertEquals(2, creations)
        repository.close()
    }

    private class FakePlaybackRuntime(
        private val group: PlaybackGroup,
    ) : ExternalSourcePlaybackRuntime {
        private val title = AnimeTitle(
            id = "title-1",
            russianName = null,
            englishName = "Title",
            originalName = "Title",
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = 1,
            posterUrl = null,
            status = null,
            description = null,
        )

        override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

        override suspend fun details(id: String): AnimeTitle = title.copy(id = id)

        override suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup> = listOf(group)

        override suspend fun playerLinks(
            title: AnimeTitle,
            group: PlaybackGroup,
            episode: Episode,
        ): List<PlayerLink> = listOf(
            PlayerLink("https://example.test/video.mp4", PlayerType.DIRECT_MP4, "720p"),
        )
    }
}
