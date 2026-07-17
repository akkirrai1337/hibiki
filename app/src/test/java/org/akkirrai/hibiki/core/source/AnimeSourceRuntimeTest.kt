package org.akkirrai.hibiki.core.source

import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.core.MetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilter
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.animeresolver.model.SearchFilterOption
import org.akkirrai.animeresolver.model.RelatedAnimeTitle
import org.akkirrai.hibiki.app.settings.AnimeSourceId
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

        assertEquals("source:ANI_LIBERTY:7", searchResult.id)
        assertEquals("source:ANI_LIBERTY:8", searchResult.relatedAnime.single().id)
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

    private fun runtime(metadata: MetadataSource): AnimeSourceRuntime = AnimeSourceRuntime(
            descriptor = AnimeSourceDescriptor(
                id = AnimeSourceId.ANI_LIBERTY,
                name = "Test",
                language = "RU",
                iconRes = 0,
                supportsPlayback = false,
            ),
            metadata = metadata,
            watchDiscovery = null,
            localizeFilters = { catalog, _ -> catalog },
            normalizeTitleId = { it },
        )

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
