package org.akkirrai.hibiki.core.source

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.animeresolver.core.MetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilter
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.animeresolver.model.SearchFilterOption
import org.akkirrai.animeresolver.model.RelatedAnimeTitle
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSourceRuntimeTest {
    @Test
    fun `runtime scopes ids and only sends native ids to source`() = runBlocking {
        val metadata = FakeMetadataSource(AnimeSearchFilterCatalog())
        val runtime = runtime(metadata)

        val searchResult = runtime.search(AnimeSearchRequest(query = "test")).single()
        val details = runtime.details(searchResult.id)

        assertEquals("source:ani-liberty:7", searchResult.id)
        assertEquals("source:ani-liberty:8", searchResult.relatedAnime.single().id)
        assertEquals("7", metadata.requestedDetailsId)
        assertEquals(searchResult.id, details.id)
    }

    @Test
    fun `filter contract hides unsupported and technical options`() = runBlocking {
        val metadata = FakeMetadataSource(
            AnimeSearchFilterCatalog(
                typeOptions = listOf(SearchFilterOption("tv", "TV")),
                statusOptions = listOf(SearchFilterOption("1", "IS_ONGOING")),
                genreOptions = listOf(
                    SearchFilterOption("15", "Action"),
                    SearchFilterOption("16", "16"),
                ),
                capabilities = CAPABILITIES,
            ),
        )
        val runtime = runtime(metadata)

        val catalog = runtime.filterCatalog(preferEnglish = false)

        assertTrue(catalog.typeOptions.isEmpty())
        assertEquals(listOf(SearchFilterOption("1", "Онгоинг")), catalog.statusOptions)
        assertEquals(listOf(SearchFilterOption("15", "Action")), catalog.genreOptions)
    }

    @Test
    fun `playback source receives native title id`() = runBlocking {
        val metadata = FakeMetadataSource(AnimeSearchFilterCatalog())
        val playback = FakePlaybackSource()
        val runtime = runtime(metadata, playback)
        val title = TITLE.copy(id = "source:ani-liberty:7")

        val groups = runtime.getPlaybackGroups(title)

        assertEquals("7", playback.requestedTitleId)
        assertEquals("default", groups.single().id)
    }

    @Test
    fun `latest operation is provided by optional capability`() = runBlocking {
        val runtime = runtime(
            metadata = FakeMetadataSource(AnimeSearchFilterCatalog()),
            latestSource = LatestSource { listOf(TITLE) },
        )

        val latest = runtime.latest(10)

        assertEquals(listOf("source:ani-liberty:7"), latest.map { it.id })
    }

    private fun runtime(
        metadata: MetadataSource,
        playbackSource: PlaybackSource? = null,
        latestSource: LatestSource? = null,
    ): AnimeSourceRuntime = AnimeSourceRuntime(
            descriptor = AnimeSourceDescriptor(
                info = SourceInfo(
                    id = SourceId("ani-liberty"),
                    name = "Test",
                    languages = setOf(SourceLanguage.RUSSIAN),
                ),
                iconRes = 0,
            ),
            metadata = metadata,
            latestSource = latestSource,
            playbackSource = playbackSource,
            localizeFilters = { catalog, _ -> catalog },
            normalizeTitleId = { it },
        )

    private class FakePlaybackSource : PlaybackSource {
        var requestedTitleId: String? = null

        override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
            requestedTitleId = title.id
            return listOf(
                PlaybackGroup(
                    id = "default",
                    title = "Test",
                    episodes = listOf(Episode(id = "1", number = 1.0, title = null)),
                ),
            )
        }

        override suspend fun getPlayerLinks(
            title: AnimeTitle,
            group: PlaybackGroup,
            episode: Episode,
        ): List<PlayerLink> = emptyList()
    }

    private class FakeMetadataSource(
        private val catalog: AnimeSearchFilterCatalog,
    ) : MetadataSource {
        var requestedDetailsId: String? = null
        override val name = "Test"
        override val capabilities = CAPABILITIES
        override suspend fun search(query: String): List<AnimeTitle> = listOf(TITLE)
        override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = catalog
        override suspend fun getById(id: String): AnimeTitle {
            requestedDetailsId = id
            return TITLE
        }
    }

    @Test
    fun `runtime reads legacy scoped ids and emits canonical ids`() = runBlocking {
        val metadata = FakeMetadataSource(AnimeSearchFilterCatalog())
        val runtime = runtime(metadata)

        val details = runtime.details("source:ANI_LIBERTY:7")

        assertEquals("7", metadata.requestedDetailsId)
        assertEquals("source:ani-liberty:7", details.id)
    }

    private companion object {
        val CAPABILITIES = MetadataSourceCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = setOf(
                AnimeSearchFilter.STATUS,
                AnimeSearchFilter.INCLUDED_GENRES,
            ),
        )
        val TITLE = AnimeTitle(
            id = "7",
            russianName = "Тест",
            englishName = "Test",
            originalName = "Test",
            japaneseName = null,
            synonyms = emptyList(),
            year = 2026,
            type = "TV",
            episodeCount = 2,
            posterUrl = null,
            status = "ongoing",
            description = null,
            relatedAnime = listOf(RelatedAnimeTitle(id = "8", title = "Related")),
        )
    }
}
