package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourcePackageStateException
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryIndex
import org.akkirrai.beakokit.api.SourceRepositoryIndexCodec
import org.akkirrai.beakokit.api.SourceRepositoryLoader
import org.akkirrai.beakokit.api.SourceRepositoryResponse
import org.akkirrai.beakokit.api.SourceRepositoryStore
import org.akkirrai.beakokit.api.SourceRepositoryTransport
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourceRuntime

class RepositoryManagementActionsTest {
    @Test
    fun repositoryOnlyAdapterDoesNotExposePackageOperations() = runTest {
        val actions = RepositoryManagementActions(emptyCoordinator())

        assertEquals(emptyList(), actions.packageStatusesForUi())
        assertFailsWith<SourcePackageStateException> {
            actions.installAvailablePackageFromUi(SourceId("external-source")) {}
        }
        assertFailsWith<SourcePackageStateException> {
            actions.rollbackPackageFromUi(SourceId("external-source"))
        }
    }

    @Test
    fun addingRepositoryRefreshesItsIndex() = runTest {
        val coordinator = coordinatorWithTransport()
        val actions = RepositoryManagementActions(coordinator)
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")

        actions.addRepositoryFromUi(endpoint)

        assertEquals(listOf(endpoint), coordinator.repositories())
        assertEquals(listOf(SourceId("external-source")), coordinator.availableSourceIds())
    }

    @Test
    fun addingTheSameRepositoryDoesNotRefreshAgain() = runTest {
        var loads = 0
        val coordinator = coordinatorWithTransport { loads++ }
        val actions = RepositoryManagementActions(coordinator)
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")

        actions.addRepositoryFromUi(endpoint)
        actions.addRepositoryFromUi(endpoint)

        assertEquals(1, loads)
    }

    @Test
    fun removingMissingRepositoryDoesNotRefresh() = runTest {
        var loads = 0
        val coordinator = coordinatorWithTransport { loads++ }
        val actions = RepositoryManagementActions(coordinator)

        actions.removeRepositoryFromUi("https://missing.example/index.json")

        assertEquals(0, loads)
    }

    private fun emptyCoordinator() = ExternalSourceRepositoryCoordinator(
        SourceRepositoryCatalogLoader(
            catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                override fun load() = emptyList<org.akkirrai.beakokit.api.SourceRepositoryEndpoint>()

                override fun persistAtomically(
                    repositories: List<org.akkirrai.beakokit.api.SourceRepositoryEndpoint>,
                ) = Unit
            }),
            loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                error("Repository loading is not used by this test")
            }),
        ),
    )

    private fun coordinatorWithTransport(onLoad: () -> Unit = {}) = ExternalSourceRepositoryCoordinator(
        SourceRepositoryCatalogLoader(
            catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                private var repositories = emptyList<SourceRepositoryEndpoint>()

                override fun load() = repositories

                override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
                    this.repositories = repositories
                }
            }),
            loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                onLoad()
                SourceRepositoryResponse(200, SourceRepositoryIndexCodec.encode(index()))
            }),
        ),
    )

    private fun index() = SourceRepositoryIndex(
        apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
        sources = listOf(
            SourceManifest(
                manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
                sourceId = SourceId("external-source"),
                packageVersion = "1.0.0",
                sourceInfo = SourceManifestInfo(
                    displayName = "External source",
                    languages = setOf(SourceLanguage.ENGLISH),
                    primaryLanguage = SourceLanguage.ENGLISH,
                ),
                apiVersion = SourceApi.VERSION,
                hostApiVersion = SourceHostApi.VERSION,
                runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
                entrypoint = "source.wasm",
                packageUrl = "https://example.test/source.zip",
                sha256 = "a".repeat(64),
                artifactSizeBytes = 1,
                minClientVersion = 0,
            ),
        ),
    )
}
