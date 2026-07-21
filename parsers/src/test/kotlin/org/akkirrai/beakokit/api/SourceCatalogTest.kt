package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceCatalogTest {
    @Test
    fun `catalog resolves source by stable id`() {
        val info = sourceInfo("ani-liberty")
        val catalog = SourceCatalog(listOf(sourceEntry(info)))

        assertEquals(info, catalog.require(SourceId("ani-liberty")))
    }

    @Test
    fun `catalog rejects duplicate source ids`() {
        assertFailsWith<IllegalArgumentException> {
            SourceCatalog(
                listOf(
                    sourceEntry(sourceInfo("ani-liberty")),
                    sourceEntry(sourceInfo("ani-liberty")),
                ),
            )
        }
    }

    @Test
    fun `catalog creates and validates source from context`() {
        val info = sourceInfo("ani-liberty")
        val catalog = SourceCatalog(
            listOf(SourceCatalogEntry(info, SourceFactory { FakeSource(info) })),
        )
        val client = HttpClient(MockEngine { error("Network must not be called") })

        try {
            val source = catalog.create(
                SourceId("ani-liberty"),
                DefaultSourceContext(client, listOf(SourceLanguage.RUSSIAN)),
            )

            assertEquals(info, source.info)
        } finally {
            client.close()
        }
    }

    @Test
    fun `catalog rejects factory with mismatched identity`() {
        val catalogInfo = sourceInfo("ani-liberty")
        val catalog = SourceCatalog(
            listOf(
                SourceCatalogEntry(
                    catalogInfo,
                    SourceFactory { FakeSource(sourceInfo("different-source")) },
                ),
            ),
        )
        val client = HttpClient(MockEngine { error("Network must not be called") })

        try {
            assertFailsWith<IllegalStateException> {
                catalog.create(
                    catalogInfo.id,
                    DefaultSourceContext(client, listOf(SourceLanguage.RUSSIAN)),
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `catalog validates configurable source settings before exposing it`() {
        val info = sourceInfo("configured-source")
        val catalog = SourceCatalog(
            listOf(SourceCatalogEntry(info, SourceFactory { ConfigurableFakeSource(info) })),
        )
        val client = HttpClient(MockEngine { error("Network must not be called") })

        try {
            val error = assertFailsWith<SourceConfigException> {
                catalog.create(
                    info.id,
                    DefaultSourceContext(
                        httpClient = client,
                        preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                        config = MapSourceConfig(values = mapOf("base_url" to "http://example.com")),
                    ),
                )
            }

            assertEquals(listOf("Config base_url must use an HTTPS URL"), error.violations)
        } finally {
            client.close()
        }
    }

    private fun sourceEntry(info: SourceInfo) = SourceCatalogEntry(
        info = info,
        factory = SourceFactory { error("Factory must not be called") },
    )

    private fun sourceInfo(id: String) = SourceInfo(
        id = SourceId(id),
        name = "Test",
        languages = setOf(SourceLanguage.RUSSIAN),
        primaryLanguage = SourceLanguage.RUSSIAN,
        website = "https://example.com",
    )

    private open class FakeSource(
        override val info: SourceInfo,
    ) : AnimeSource {
        override val catalogCapabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = emptySet(),
        )

        override suspend fun search(query: String): List<AnimeTitle> = emptyList()

        override suspend fun getById(id: String): AnimeTitle = error("Not used")
    }

    private class ConfigurableFakeSource(
        info: SourceInfo,
    ) : FakeSource(info), ConfigurableSource {
        override val configSchema = SourceConfigSchema(
            listOf(SourceConfigField("base_url", SourceConfigValueKind.HTTPS_URL, required = true)),
        )
    }
}
