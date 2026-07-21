package org.akkirrai.beakokit.testkit

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.StreamValidator
import org.akkirrai.beakokit.playback.NoPlayerLinksException
import org.akkirrai.beakokit.playback.PlaybackResolver
import org.akkirrai.beakokit.playback.extractor.DirectHlsExtractor
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceTestKitTest {
    @Test
    fun `catalog contract checks search details and latest`() = runBlocking {
        val source = FakeCatalogSource(listOf(title("42")))

        val snapshot = SourceTestKit.assertCatalogContract(
            source,
            AnimeSearchRequest(query = "test", limit = 5),
        )
        val latest = SourceTestKit.assertLatestContract(source, limit = 5)

        assertEquals("42", snapshot.details.id)
        assertEquals(listOf("42"), latest.map(AnimeTitle::id))
    }

    @Test
    fun `search contract rejects duplicate opaque ids`() = runBlocking {
        val source = FakeCatalogSource(listOf(title("42"), title("42")))

        assertFailsWith<AssertionError> {
            SourceTestKit.assertSearchContract(
                source,
                AnimeSearchRequest(query = "test", limit = 5),
            )
        }
    }

    @Test
    fun `filter contract validates capabilities options and aliases`() = runBlocking {
        val capabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE, AnimeSearchSort.RATING),
            supportedFilters = AnimeSearchFilter.entries.toSet(),
        )
        val source = FakeCatalogSource(
            titles = listOf(title("42")),
            catalogCapabilities = capabilities,
            filterCatalog = AnimeSearchFilterCatalog(
                sortOptions = options("relevance", "rating"),
                typeOptions = options("tv"),
                statusOptions = options("ongoing"),
                genreOptions = options("action"),
                capabilities = capabilities,
            ),
        )

        val results = SourceTestKit.assertFilteredSearchContract(
            source,
            AnimeSearchRequest(
                limit = 1,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOf("tv"),
                statusAliases = listOf("ongoing"),
                includedGenreAliases = listOf("action"),
                excludedGenreAliases = listOf("action"),
                yearFrom = 2020,
                yearTo = 2026,
            ),
        )

        assertEquals(listOf("42"), results.map(AnimeTitle::id))
    }

    @Test
    fun `filtered search contract rejects unknown aliases`() = runBlocking {
        val capabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = setOf(AnimeSearchFilter.TYPE),
        )
        val source = FakeCatalogSource(
            titles = listOf(title("42")),
            catalogCapabilities = capabilities,
            filterCatalog = AnimeSearchFilterCatalog(
                sortOptions = options("relevance"),
                typeOptions = options("tv"),
                capabilities = capabilities,
            ),
        )

        assertFailsWith<AssertionError> {
            SourceTestKit.assertFilteredSearchContract(
                source,
                AnimeSearchRequest(typeAliases = listOf("unknown")),
            )
        }
    }

    @Test
    fun `filter catalog contract rejects a declared filter without options`() = runBlocking {
        val capabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = setOf(AnimeSearchFilter.TYPE),
        )
        val source = FakeCatalogSource(
            titles = listOf(title("42")),
            catalogCapabilities = capabilities,
            filterCatalog = AnimeSearchFilterCatalog(
                sortOptions = options("relevance"),
                capabilities = capabilities,
            ),
        )

        assertFailsWith<AssertionError> {
            SourceTestKit.assertFilterCatalogContract(source)
        }
    }

    @Test
    fun `pagination contract validates adjacent pages and rejects overlap`() = runBlocking {
        val source = FakeCatalogSource(listOf(title("42"), title("43")))
        val snapshot = SourceTestKit.assertPaginationContract(
            source,
            AnimeSearchRequest(limit = 1),
        )
        assertEquals(listOf("42"), snapshot.firstPage.map(AnimeTitle::id))
        assertEquals(listOf("43"), snapshot.secondPage.map(AnimeTitle::id))

        val brokenSource = object : AnimeSource by source {
            override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = listOf(title("42"))
        }
        assertFailsWith<AssertionError> {
            SourceTestKit.assertPaginationContract(brokenSource, AnimeSearchRequest(limit = 1))
        }
    }

    @Test
    fun `empty search contract requires an empty result`() = runBlocking {
        SourceTestKit.assertEmptySearchContract(
            FakeCatalogSource(emptyList()),
            AnimeSearchRequest(query = "missing", limit = 1),
        )
        assertFailsWith<AssertionError> {
            SourceTestKit.assertEmptySearchContract(
                FakeCatalogSource(listOf(title("42"))),
                AnimeSearchRequest(query = "missing", limit = 1),
            )
        }
    }

    @Test
    fun `metadata contract can require description and poster`() = runBlocking {
        val richTitle = title("42").copy(
            description = "Fixture description",
            posterUrl = "https://images.test/poster.jpg",
        )

        val details = SourceTestKit.assertDetailsMetadataContract(
            source = FakeCatalogSource(listOf(richTitle)),
            id = "42",
            requireDescription = true,
            requirePoster = true,
        )

        assertEquals("Fixture description", details.description)
    }

    @Test
    fun `playable stream contract resolves a valid episode stream`() = runBlocking {
        val title = title("42")
        val source = FakePlaybackSource(title, listOf(PlayerLink(
            url = "https://video.test/episode.m3u8",
            type = PlayerType.DIRECT_HLS,
            quality = "720p",
        )))
        val resolver = PlaybackResolver(
            extractors = listOf<StreamExtractor>(DirectHlsExtractor()),
            validator = object : StreamValidator {
                override suspend fun validate(stream: VideoStream) = StreamValidationResult(
                    success = true,
                    streamType = StreamType.HLS,
                    quality = stream.quality,
                    finalUrl = stream.url,
                    statusCode = 200,
                    message = "OK",
                )
            },
        )

        val snapshot = SourceTestKit.assertPlayableStreamContract(source, title, resolver)

        assertEquals("https://video.test/episode.m3u8", snapshot.resolved.validation.finalUrl)
    }

    @Test
    fun `failure contract preserves the expected typed playback error`() = runBlocking {
        val resolver = PlaybackResolver(emptyList(), object : StreamValidator {
            override suspend fun validate(stream: VideoStream): StreamValidationResult = error("Not called")
        })

        SourceTestKit.assertFailureContract(NoPlayerLinksException::class) {
            resolver.resolve(emptyList())
        }
    }

    private class FakeCatalogSource(
        private val titles: List<AnimeTitle>,
        override val catalogCapabilities: CatalogCapabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = emptySet(),
        ),
        private val filterCatalog: AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
            sortOptions = options("relevance"),
            capabilities = catalogCapabilities,
        ),
    ) : AnimeSource, LatestSource {
        override val info = SourceInfo(
            id = SourceId("fixture-source"),
            name = "Fixture",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
            capabilities = setOf(SourceCapability.LATEST_RELEASES),
        )

        override suspend fun search(query: String): List<AnimeTitle> = titles

        override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> =
            titles.drop(request.offset.coerceAtLeast(0)).take(request.limit.coerceAtLeast(0))

        override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = filterCatalog

        override suspend fun getById(id: String): AnimeTitle = titles.first { it.id == id }

        override suspend fun latest(limit: Int): List<AnimeTitle> = titles.take(limit)
    }

    private class FakePlaybackSource(
        private val title: AnimeTitle,
        private val links: List<PlayerLink>,
    ) : AnimeSource by FakeCatalogSource(listOf(title)), PlaybackSource {
        override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = listOf(
            PlaybackGroup("group", "Fixture voiceover", listOf(Episode("episode", 1.0, null))),
        )

        override suspend fun getPlayerLinks(
            title: AnimeTitle,
            group: PlaybackGroup,
            episode: Episode,
        ): List<PlayerLink> = links
    }

    private fun title(id: String) = AnimeTitle(
        id = id,
        russianName = null,
        englishName = "Test",
        originalName = "Test",
        japaneseName = null,
        synonyms = emptyList(),
        year = 2026,
        type = "tv",
        episodeCount = 1,
        posterUrl = null,
        status = null,
        description = null,
    )

    private companion object {
        fun options(vararg ids: String): List<SearchFilterOption> =
            ids.map { SearchFilterOption(it, it) }
    }
}
