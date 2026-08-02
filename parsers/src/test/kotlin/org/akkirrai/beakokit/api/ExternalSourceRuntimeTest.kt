package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities

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

    private fun sourceInfo() = SourceInfo(
        id = SourceId("external-test"),
        name = "External test source",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    private class FakeRuntime : ExternalSourceRuntime {
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
}
