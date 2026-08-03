package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType

class ExternalSourceRuntimeTest {
    @Test
    fun runtimeBackedSourceDelegatesSearchAndDetails() = runBlocking {
        val runtime = FakeRuntime()
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        val searchRequest = AnimeSearchRequest(query = "made in abyss")
        assertEquals(runtime.searchResult, source.search(searchRequest))
        assertEquals(runtime.detailsResult, source.getById("title-1"))
        assertEquals(searchRequest, runtime.lastSearchRequest)
        assertEquals("title-1", runtime.lastDetailsId)
    }

    @Test
    fun playbackRuntimeDelegatesGroupsAndPlayerLinks() = runBlocking {
        val runtime = FakePlaybackRuntime()
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )
        val title = runtime.detailsResult
        val group = runtime.playbackGroup
        val episode = group.episodes.single()

        assertEquals(listOf(group), source.getPlaybackGroups(title))
        assertEquals(runtime.playerLinksResult, source.getPlayerLinks(title, group, episode))
    }

    private fun sourceInfo() = SourceInfo(
        id = SourceId("external-test"),
        name = "External test source",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    private open class FakeRuntime : ExternalSourceRuntime {
        val searchResult = emptyList<AnimeTitle>()
        val detailsResult = AnimeTitle(
            id = "title-1",
            russianName = null,
            englishName = "Test title",
            originalName = "Test title",
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )
        var lastSearchRequest: AnimeSearchRequest? = null
        var lastDetailsId: String? = null

        override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
            lastSearchRequest = request
            return searchResult
        }

        override suspend fun details(id: String): AnimeTitle {
            lastDetailsId = id
            return detailsResult
        }
    }

    private class FakePlaybackRuntime : FakeRuntime(), ExternalSourcePlaybackRuntime {
        val playbackGroup = PlaybackGroup(
            id = "group-1",
            title = "Subtitles",
            episodes = listOf(Episode(id = "episode-1", number = 1.0, title = "Episode 1")),
        )
        val playerLinksResult = listOf(
            PlayerLink(
                url = "https://example.test/video.mp4",
                type = PlayerType.DIRECT_MP4,
                quality = "720p",
            ),
        )

        override suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup> =
            listOf(playbackGroup)

        override suspend fun playerLinks(
            title: AnimeTitle,
            group: PlaybackGroup,
            episode: Episode,
        ): List<PlayerLink> = playerLinksResult
    }
}
