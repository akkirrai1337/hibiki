package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities

class ExternalSourceRegistrationTest {
    @Test
    fun registrationCreatesRuntimeBackedSourceThroughCatalog() = runBlocking {
        val runtime = object : ExternalSourceRuntime {
            override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

            override suspend fun details(id: String): AnimeTitle = title(id)
        }
        var receivedContext: SourceContext? = null
        val registration = ExternalSourceRegistration(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtimeFactory = { context ->
                receivedContext = context
                runtime
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        val source = externalSourceCatalog(listOf(registration))
            .create(SourceId("external-test"), context)

        assertSame(context, receivedContext)
        assertEquals("title-1", source.getById("title-1").id)
    }

    @Test
    fun manifestMetadataFeedsRegistrationInfo() {
        val registration = manifest().toExternalSourceRegistration(
            catalogCapabilities = CatalogCapabilities.FULL,
            runtimeFactory = { error("Runtime is not expected in this test") },
        )

        assertEquals("External manifest source", registration.info.name)
        assertEquals(SourceId("external-test"), registration.info.id)
    }

    @Test
    fun registrationsBuildAnExternalRegistry() {
        val registry = externalSourceRegistry(
            listOf(
                ExternalSourceRegistration(
                    info = sourceInfo(),
                    catalogCapabilities = CatalogCapabilities.FULL,
                    runtimeFactory = { error("Runtime is not expected in this test") },
                ),
            ),
        )

        assertEquals(listOf(SourceId("external-test")), registry.sources.map(SourceInfo::id))
    }

    @Test
    fun activePackagePassesItsPathToRuntimeFactory() {
        var receivedPath: String? = null
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest(),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )

        activePackage.toExternalSourceRegistration(
            catalogCapabilities = CatalogCapabilities.FULL,
            runtimeFactory = { packagePath, _ ->
                receivedPath = packagePath
                object : ExternalSourceRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                    override suspend fun details(id: String): AnimeTitle = title(id)
                }
            },
        ).catalogEntry().factory.create(
            DefaultSourceContext(
                httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
            ),
        )

        assertEquals("sources/external-test/1.0.0", receivedPath)
    }

    @Test
    fun activePackageRejectsMismatchedVersion() {
        assertFailsWith<IllegalArgumentException> {
            ActiveExternalSourcePackage(
                manifest = manifest(),
                installed = InstalledSourcePackage(
                    sourceId = SourceId("external-test"),
                    packageVersion = "2.0.0",
                    packagePath = "sources/external-test/2.0.0",
                ),
            )
        }
    }

    private fun sourceInfo() = SourceInfo(
        id = SourceId("external-test"),
        name = "External test source",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-test"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "External manifest source",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
        ),
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1,
        minClientVersion = 1,
    )

    private fun title(id: String) = AnimeTitle(
        id = id,
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
}
