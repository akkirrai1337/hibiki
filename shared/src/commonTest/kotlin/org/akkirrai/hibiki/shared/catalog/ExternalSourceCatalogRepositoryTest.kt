package org.akkirrai.hibiki.shared.catalog

import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.ExternalSourceRegistration
import org.akkirrai.beakokit.api.ExternalSourceRuntime
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourceRuntime
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.hibiki.shared.source.ExternalAnimeStatusLabels

class ExternalSourceCatalogRepositoryTest {
    @Test
    fun searchAndDetailsUseTheExternalRegistryAndKeepSourceScopedIds() = runTest {
        val sourceId = SourceId("external-source")
        val repository = ExternalSourceCatalogRepository(
            registryProvider = { registry(sourceId) },
            contextProvider = { DefaultSourceContext(HttpClient(), listOf(SourceLanguage.ENGLISH)) },
            statusLabels = ExternalAnimeStatusLabels("Unknown", "Ongoing", "Released", "Announcement"),
            initialSourceId = sourceId,
        )

        val search = repository.search(AnimeCatalogQuery(text = "query"))
        val details = repository.getDetails(search.items.single().id, search.items.single())

        assertEquals("source:external-source:native-1", search.items.single().id)
        assertEquals("source:external-source:native-1", details.id)
        assertEquals("Details", details.description)
    }

    private fun registry(sourceId: SourceId): ExternalSourceRegistry =
        ExternalSourceRegistry(
            org.akkirrai.beakokit.api.SourceCatalog(
                listOf(
                    org.akkirrai.beakokit.api.SourceCatalogEntry(
                        info = SourceInfo(
                            id = sourceId,
                            name = "External source",
                            languages = setOf(SourceLanguage.ENGLISH),
                            primaryLanguage = SourceLanguage.ENGLISH,
                        ),
                        factory = org.akkirrai.beakokit.api.SourceFactory { _ ->
                            org.akkirrai.beakokit.api.RuntimeBackedAnimeSource(
                                info = SourceInfo(
                                    id = sourceId,
                                    name = "External source",
                                    languages = setOf(SourceLanguage.ENGLISH),
                                    primaryLanguage = SourceLanguage.ENGLISH,
                                ),
                                catalogCapabilities = CatalogCapabilities.FULL,
                                runtime = object : ExternalSourceRuntime {
                                    override suspend fun search(request: AnimeSearchRequest) =
                                        listOf(title("native-1", "Search"))

                                    override suspend fun details(id: String) =
                                        title(id, "Details")
                                },
                            )
                        },
                    ),
                ),
            ),
        )

    private fun title(id: String, description: String) = AnimeTitle(
        id = id,
        russianName = null,
        englishName = "External title",
        originalName = "External title",
        japaneseName = null,
        synonyms = emptyList(),
        year = 2024,
        type = "TV",
        episodeCount = 12,
        posterUrl = null,
        status = "released",
        description = description,
    )
}
