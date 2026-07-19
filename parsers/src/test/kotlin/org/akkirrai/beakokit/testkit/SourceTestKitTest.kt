package org.akkirrai.beakokit.testkit

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
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

    private class FakeCatalogSource(
        private val titles: List<AnimeTitle>,
    ) : AnimeSource, LatestSource {
        override val info = SourceInfo(
            id = SourceId("fixture-source"),
            name = "Fixture",
            languages = setOf(SourceLanguage.ENGLISH),
            capabilities = setOf(SourceCapability.LATEST_RELEASES),
        )
        override val catalogCapabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = emptySet(),
        )

        override suspend fun search(query: String): List<AnimeTitle> = titles

        override suspend fun getById(id: String): AnimeTitle = titles.first { it.id == id }

        override suspend fun latest(limit: Int): List<AnimeTitle> = titles.take(limit)
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
}
