package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRegistryTest {
    @Test
    fun `combined registry exposes both source families`() {
        val builtInInfo = sourceInfo("built-in")
        val externalInfo = sourceInfo("external")
        val registry = CombinedSourceRegistry(
            BuiltInSourceRegistry(SourceCatalog(listOf(entry(builtInInfo)))),
            ExternalSourceRegistry(SourceCatalog(listOf(entry(externalInfo)))),
        )

        assertEquals(listOf(builtInInfo, externalInfo), registry.sources)
        assertEquals(externalInfo, registry.requireInfo(externalInfo.id))
    }

    @Test
    fun `combined registry rejects duplicate stable source ids`() {
        val info = sourceInfo("same-id")

        assertFailsWith<IllegalArgumentException> {
            CombinedSourceRegistry(
                BuiltInSourceRegistry(SourceCatalog(listOf(entry(info)))),
                ExternalSourceRegistry(SourceCatalog(listOf(entry(info)))),
            )
        }
    }

    private fun entry(info: SourceInfo) = SourceCatalogEntry(
        info = info,
        factory = SourceFactory { FakeSource(info) },
    )

    private fun sourceInfo(id: String) = SourceInfo(
        id = SourceId(id),
        name = id,
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    private class FakeSource(
        override val info: SourceInfo,
    ) : AnimeSource {
        override val catalogCapabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = emptySet(),
        )
        override suspend fun search(query: String): List<AnimeTitle> = emptyList()
        override suspend fun getById(id: String): AnimeTitle = error("Not used")
    }
}
