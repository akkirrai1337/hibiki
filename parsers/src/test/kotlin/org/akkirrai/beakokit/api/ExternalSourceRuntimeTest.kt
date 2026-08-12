package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType

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

    @Test
    fun playbackRuntimeRejectsUndeclaredCleartextHosts() = runBlocking {
        val runtime = FakePlaybackRuntime().apply {
            playerLinksResult = listOf(
                PlayerLink(
                    url = "http://video.example.com/video.mp4",
                    type = PlayerType.DIRECT_MP4,
                    quality = "720p",
                ),
            )
        }
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.getPlayerLinks(runtime.detailsResult, runtime.playbackGroup, runtime.playbackGroup.episodes.single())
        }
    }

    @Test
    fun playbackRuntimeRejectsEmptyPlayerLinks() = runBlocking {
        val runtime = FakePlaybackRuntime().apply { playerLinksResult = emptyList() }
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.getPlayerLinks(
                runtime.detailsResult,
                runtime.playbackGroup,
                runtime.playbackGroup.episodes.single(),
            )
        }
    }

    @Test
    fun playbackRuntimeRejectsInvalidPlayerLinkSegments() = runBlocking {
        val runtime = FakePlaybackRuntime().apply {
            playerLinksResult = listOf(
                PlayerLink(
                    url = "https://example.test/video.mp4",
                    type = PlayerType.DIRECT_MP4,
                    quality = "720p",
                    segments = listOf(VideoSegment(VideoSegmentType.OPENING, 30_000, 30_000)),
                ),
            )
        }
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.getPlayerLinks(
                runtime.detailsResult,
                runtime.playbackGroup,
                runtime.playbackGroup.episodes.single(),
            )
        }
    }

    @Test
    fun playbackRuntimeRejectsInvalidPlaybackArgumentsBeforeCallingRuntime() = runBlocking {
        val runtime = FakePlaybackRuntime()
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )
        val invalidTitle = runtime.detailsResult.copy(id = "title\n-1")
        val invalidEpisode = runtime.playbackGroup.episodes.single().copy(number = Double.NaN)

        assertFailsWith<IllegalArgumentException> {
            source.getPlaybackGroups(invalidTitle)
        }
        assertFailsWith<IllegalArgumentException> {
            source.getPlayerLinks(runtime.detailsResult, runtime.playbackGroup, invalidEpisode)
        }
    }

    @Test
    fun playbackRuntimeRejectsInvalidGroups() = runBlocking {
        val runtime = FakePlaybackRuntime().apply {
            playbackGroupsResult = listOf(
                PlaybackGroup(
                    id = "",
                    title = "",
                    episodes = listOf(
                        Episode("episode-1", 1.0, "Episode 1"),
                        Episode("episode-1", 2.0, "Episode 2"),
                    ),
                ),
            )
        }
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.getPlaybackGroups(runtime.detailsResult)
        }
    }

    @Test
    fun playbackRuntimeRejectsControlCharactersInGroupAndEpisodeIds() = runBlocking {
        val runtime = FakePlaybackRuntime().apply {
            playbackGroupsResult = listOf(
                playbackGroup.copy(
                    id = "group\n-1",
                    episodes = listOf(Episode("episode-1\r", 1.0, "Episode 1")),
                ),
            )
        }
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.getPlaybackGroups(runtime.detailsResult)
        }
    }

    @Test
    fun catalogRuntimeRejectsInvalidTitleResults() = runBlocking {
        val runtime = FakeRuntime().apply {
            searchResult = listOf(detailsResult.copy(id = "title\n-1"))
        }
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(query = "broken"))
        }

        val latestRuntime = FakeLatestRuntime().apply {
            latestResult = listOf(detailsResult.copy(englishName = "", originalName = ""))
        }
        val latest = RuntimeBackedLatestAnimeSource(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.LATEST_RELEASES)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = latestRuntime,
        )

        assertFailsWith<IllegalArgumentException> { latest.latest(1) }
    }

    @Test
    fun catalogRuntimeRejectsInvalidDetailsIdBeforeCallingRuntime() = runBlocking {
        val runtime = FakeRuntime()
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> { source.getById("title\n-1") }
        assertEquals(null, runtime.lastDetailsId)
    }

    @Test
    fun catalogRuntimeRejectsDetailsForAnotherTitle() = runBlocking {
        val runtime = FakeRuntime().apply {
            detailsResult = detailsResult.copy(id = "different-title")
        }
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> { source.getById("title-1") }
    }

    @Test
    fun installedMetadataPolicyRejectsServiceGenreAliases() = runBlocking {
        val runtime = FakeRuntime().apply {
            searchResult = listOf(detailsResult.copy(genres = listOf("action")))
        }
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
            metadataPolicy = ExternalSourceMetadataPolicy.INSTALLED_PACKAGE,
        )

        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(limit = 1))
        }
    }

    @Test
    fun catalogRuntimeRejectsInvalidSearchBoundsAndOversizedResults() = runBlocking {
        val runtime = FakeRuntime().apply {
            searchResult = listOf(detailsResult, detailsResult.copy(id = "title-2"))
        }
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(limit = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(offset = -1))
        }
        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(yearFrom = 2025, yearTo = 2024))
        }
        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(limit = 1))
        }
    }

    @Test
    fun catalogRuntimeRejectsInvalidTitleMetadata() = runBlocking {
        val runtime = FakeRuntime().apply {
            searchResult = listOf(
                detailsResult.copy(
                    description = "",
                    posterFallbackUrl = "ftp://cdn.example.com/poster.jpg",
                ),
            )
        }
        val source = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = runtime,
        )

        assertFailsWith<IllegalArgumentException> {
            source.search(AnimeSearchRequest(limit = 1))
        }

        val invalidEpisodeCountRuntime = FakeRuntime().apply {
            searchResult = listOf(detailsResult.copy(episodeCount = 0))
        }
        val invalidEpisodeCountSource = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = invalidEpisodeCountRuntime,
        )

        assertFailsWith<IllegalArgumentException> {
            invalidEpisodeCountSource.search(AnimeSearchRequest(limit = 1))
        }

        val invalidPosterRuntime = FakeRuntime().apply {
            searchResult = listOf(detailsResult.copy(posterUrl = "javascript:alert(1)"))
        }
        val invalidPosterSource = RuntimeBackedAnimeSource(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = invalidPosterRuntime,
        )

        assertFailsWith<IllegalArgumentException> {
            invalidPosterSource.search(AnimeSearchRequest(limit = 1))
        }
    }

    private fun sourceInfo() = SourceInfo(
        id = SourceId("external-test"),
        name = "External test source",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    private open class FakeRuntime : ExternalSourceRuntime {
        var searchResult = emptyList<AnimeTitle>()
        var detailsResult = AnimeTitle(
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

    private class FakeLatestRuntime : FakeRuntime(), ExternalSourceLatestRuntime {
        var latestResult = listOf(detailsResult)

        override suspend fun latest(limit: Int): List<AnimeTitle> = latestResult
    }

    private class FakePlaybackRuntime : FakeRuntime(), ExternalSourcePlaybackRuntime {
        val playbackGroup = PlaybackGroup(
            id = "group-1",
            title = "Subtitles",
            episodes = listOf(Episode(id = "episode-1", number = 1.0, title = "Episode 1")),
        )
        var playerLinksResult = listOf(
            PlayerLink(
                url = "https://example.test/video.mp4",
                type = PlayerType.DIRECT_MP4,
                quality = "720p",
            ),
        )
        var playbackGroupsResult = listOf(playbackGroup)

        override suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup> =
            playbackGroupsResult

        override suspend fun playerLinks(
            title: AnimeTitle,
            group: PlaybackGroup,
            episode: Episode,
        ): List<PlayerLink> = playerLinksResult
    }
}
