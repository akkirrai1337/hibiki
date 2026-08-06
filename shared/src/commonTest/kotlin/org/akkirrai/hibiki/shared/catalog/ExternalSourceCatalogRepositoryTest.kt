package org.akkirrai.hibiki.shared.catalog

import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.ExternalSourceRegistration
import org.akkirrai.beakokit.api.ExternalSourceRuntime
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourceRuntime
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.hibiki.shared.catalog.model.Anime
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

    @Test
    fun searchForwardsAndAdaptsTheSharedFilters() = runTest {
        val sourceId = SourceId("external-source")
        var request: AnimeSearchRequest? = null
        val repository = ExternalSourceCatalogRepository(
            registryProvider = { registry(sourceId, onSearch = { request = it }) },
            contextProvider = { DefaultSourceContext(HttpClient(), listOf(SourceLanguage.ENGLISH)) },
            statusLabels = ExternalAnimeStatusLabels("Unknown", "Ongoing", "Released", "Announcement"),
            initialSourceId = sourceId,
        )

        repository.search(
            AnimeCatalogQuery(
                text = "query",
                page = 3,
                pageSize = 7,
                filters = org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters(
                    sortAlias = "popular",
                    typeAlias = "tv",
                    statusAlias = "released",
                    includedGenreAliases = setOf("Drama", "Action"),
                    excludedGenreAliases = setOf("Horror"),
                    yearFrom = 2010,
                    yearTo = 2020,
                ),
            ),
        )

        val actual = requireNotNull(request)
        assertEquals("query", actual.query)
        assertEquals(7, actual.limit)
        assertEquals(14, actual.offset)
        assertEquals(AnimeSearchSort.RATING, actual.sort)
        assertContentEquals(listOf("tv"), actual.typeAliases)
        assertContentEquals(listOf("released"), actual.statusAliases)
        assertContentEquals(listOf("Action", "Drama"), actual.includedGenreAliases)
        assertContentEquals(listOf("Horror"), actual.excludedGenreAliases)
        assertEquals(2010, actual.yearFrom)
        assertEquals(2020, actual.yearTo)
    }

    @Test
    fun catalogOperationsCreateExternalSourcesWithTheSourceScopedContext() = runTest {
        val sourceId = SourceId("external-source")
        var receivedConfig: SourceConfig? = null
        val repository = ExternalSourceCatalogRepository(
            registryProvider = { registry(sourceId, onContext = { receivedConfig = it.config }) },
            contextProvider = {
                DefaultSourceContext(
                    httpClient = HttpClient(),
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                    config = MapSourceConfig(
                        values = mapOf("region" to "jp"),
                        secrets = mapOf("token" to "secret"),
                    ),
                )
            },
            statusLabels = ExternalAnimeStatusLabels("Unknown", "Ongoing", "Released", "Announcement"),
            initialSourceId = sourceId,
        )

        val search = repository.search(AnimeCatalogQuery(text = "query"))
        repository.getDetails(search.items.single().id, search.items.single())

        assertEquals(
            "jp",
            receivedConfig?.value("region"),
        )
        assertEquals("secret", receivedConfig?.secret("token"))
    }

    @Test
    fun catalogOperationsReuseTheCreatedExternalSource() = runTest {
        val sourceId = SourceId("external-source")
        var creations = 0
        val repository = ExternalSourceCatalogRepository(
            registryProvider = { registry(sourceId, onCreate = { creations++ }) },
            contextProvider = { DefaultSourceContext(HttpClient(), listOf(SourceLanguage.ENGLISH)) },
            statusLabels = ExternalAnimeStatusLabels("Unknown", "Ongoing", "Released", "Announcement"),
            initialSourceId = sourceId,
        )

        val search = repository.search(AnimeCatalogQuery(text = "query"))
        repository.getDetails(search.items.single().id, search.items.single())

        assertEquals(1, creations)
    }

    @Test
    fun transitionalRepositoryKeepsBuiltInDefaultAndRoutesExternalIds() = runTest {
        val sourceId = SourceId("external-source")
        val external = ExternalSourceCatalogRepository(
            registryProvider = { registry(sourceId) },
            contextProvider = { DefaultSourceContext(HttpClient(), listOf(SourceLanguage.ENGLISH)) },
            statusLabels = ExternalAnimeStatusLabels("Unknown", "Ongoing", "Released", "Announcement"),
            initialSourceId = sourceId,
        )
        val builtIn = object : AnimeCatalogRepository {
            override val initialItems = listOf(
                Anime(
                    id = "built-in",
                    title = "Built-in",
                    subtitle = "",
                    episodesLabel = "",
                    status = "",
                ),
            )
            override suspend fun search(query: AnimeCatalogQuery) = AnimeCatalogPage(initialItems, 1, false)
            override suspend fun getDetails(id: String, fallback: Anime) = fallback
        }
        val transitional = TransitionalAnimeCatalogRepository(builtIn, external)

        assertSame(builtIn.initialItems, transitional.initialItems)
        assertEquals("Built-in", transitional.search(AnimeCatalogQuery()).items.single().title)
        transitional.selectSource(sourceId.value)
        assertEquals(
            "External title",
            transitional.search(AnimeCatalogQuery(text = "query")).items.single().title,
        )
        assertEquals(
            "External title",
            transitional.searchSource(sourceId.value, AnimeCatalogQuery(text = "query")).items.single().title,
        )
    }

    @Test
    fun transitionalRepositoryFallsBackWhenExternalRegistryDisappears() = runTest {
        val sourceId = SourceId("external-source")
        var activeRegistry: ExternalSourceRegistry? = registry(sourceId)
        val external = ExternalSourceCatalogRepository(
            registryProvider = { activeRegistry },
            contextProvider = { DefaultSourceContext(HttpClient(), listOf(SourceLanguage.ENGLISH)) },
            statusLabels = ExternalAnimeStatusLabels("Unknown", "Ongoing", "Released", "Announcement"),
        )
        val builtInAnime = Anime(
            id = "built-in",
            title = "Built-in",
            subtitle = "",
            episodesLabel = "",
            status = "",
        )
        val builtIn = object : AnimeCatalogRepository {
            override val initialItems = listOf(builtInAnime)
            override suspend fun search(query: AnimeCatalogQuery) = AnimeCatalogPage(initialItems, 1, false)
        }
        val transitional = TransitionalAnimeCatalogRepository(builtIn, external)

        transitional.selectSource(sourceId.value)
        assertEquals("External title", transitional.search(AnimeCatalogQuery()).items.single().title)

        activeRegistry = null

        assertEquals("Built-in", transitional.search(AnimeCatalogQuery()).items.single().title)

        activeRegistry = registry(sourceId)

        assertEquals("External title", transitional.search(AnimeCatalogQuery()).items.single().title)
    }

    private fun registry(
        sourceId: SourceId,
        onSearch: (AnimeSearchRequest) -> Unit = {},
        onContext: (org.akkirrai.beakokit.api.SourceContext) -> Unit = {},
        onCreate: () -> Unit = {},
    ): ExternalSourceRegistry =
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
                        factory = org.akkirrai.beakokit.api.SourceFactory { context ->
                            onCreate()
                            onContext(context)
                            org.akkirrai.beakokit.api.RuntimeBackedAnimeSource(
                                info = SourceInfo(
                                    id = sourceId,
                                    name = "External source",
                                    languages = setOf(SourceLanguage.ENGLISH),
                                    primaryLanguage = SourceLanguage.ENGLISH,
                                ),
                                catalogCapabilities = CatalogCapabilities.FULL,
                                runtime = object : ExternalSourceRuntime {
                                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
                                        onSearch(request)
                                        return listOf(title("native-1", "Search"))
                                    }

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
