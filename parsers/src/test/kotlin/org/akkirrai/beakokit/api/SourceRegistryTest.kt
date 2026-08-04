package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRegistryTest {
    @Test
    fun `registry reports unknown source as not found`() {
        val registry = CatalogSourceRegistry(SourceCatalog(emptyList()))

        val error = assertFailsWith<SourceNotRegisteredException> {
            registry.requireInfo(SourceId("missing"))
        }

        assertEquals(SourceErrorCode.NOT_FOUND, error.code)
    }

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
    fun `combined registry keeps built in ownership for duplicate stable source ids`() {
        val builtIn = sourceInfo("same-id").copy(name = "Built-in")
        val external = sourceInfo("same-id").copy(name = "External")

        val registry = CombinedSourceRegistry(
            BuiltInSourceRegistry(SourceCatalog(listOf(entry(builtIn)))),
            ExternalSourceRegistry(SourceCatalog(listOf(entry(external)))),
        )

        assertEquals(listOf(builtIn), registry.sources)
        assertEquals(builtIn, registry.requireInfo(SourceId("same-id")))
    }

    @Test
    fun `combined registry does not initialize shadowed external source`() {
        val info = sourceInfo("same-id")
        var externalCreated = false
        val externalEntry = SourceCatalogEntry(
            info = info,
            factory = SourceFactory {
                externalCreated = true
                FakeSource(info)
            },
        )
        val registry = CombinedSourceRegistry(
            BuiltInSourceRegistry(SourceCatalog(listOf(entry(info)))),
            ExternalSourceRegistry(SourceCatalog(listOf(externalEntry))),
        )

        val client = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine {
            error("Network is not expected in this test")
        })
        try {
            registry.create(
                SourceId("same-id"),
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                ),
            )
        } finally {
            client.close()
        }

        assertEquals(false, externalCreated)
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
