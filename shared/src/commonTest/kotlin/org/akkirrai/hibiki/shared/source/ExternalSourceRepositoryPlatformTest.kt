package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.InstalledSourcePackage
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourcePackageActivationRepository
import org.akkirrai.beakokit.api.SourcePackageActivationState
import org.akkirrai.beakokit.api.SourcePackageActivationStore
import org.akkirrai.beakokit.api.SourcePackageManifestReader
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
import org.akkirrai.beakokit.api.SourceRuntime
import org.akkirrai.beakokit.model.CatalogCapabilities

class ExternalSourceRepositoryPlatformTest {
    @Test
    fun activeRegistryUsesOnlyPersistedPackagesAndKeepsRuntimeLazy() = runTest {
        val packageId = SourceId("external-source")
        val installed = InstalledSourcePackage(packageId, "1.0.0", "package/path")
        val manifest = manifest(packageId)
        var runtimeCreated = false
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = ExternalSourceRepositoryCoordinator(
                SourceRepositoryCatalogLoader(
                    catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                        override fun load() = emptyList<org.akkirrai.beakokit.api.SourceRepositoryEndpoint>()

                        override fun persistAtomically(
                            repositories: List<org.akkirrai.beakokit.api.SourceRepositoryEndpoint>,
                        ) = Unit
                    }),
                    loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                        error("Repository transport is not used by this test")
                    }),
                ),
            ),
            activePackageLoaderFactory = { requestedId ->
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(
                        sourceId = requestedId,
                        store = InMemoryStore(
                            if (requestedId == packageId) {
                                SourcePackageActivationState(active = installed)
                            } else {
                                SourcePackageActivationState()
                            },
                        ),
                    ),
                    manifestReader = SourcePackageManifestReader { manifest },
                )
            },
            closeResources = {},
        )

        val registry = platform.loadActiveRegistry(
            sourceIds = listOf(packageId, packageId, SourceId("not-installed")),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                runtimeCreated = true
                error("Runtime must remain lazy")
            },
        )

        assertEquals(listOf(packageId), registry.sources.map { it.id })
        assertTrue(!runtimeCreated)

        assertFailsWith<SourcePackageStateException> {
            platform.installAvailablePackage(packageId, "staging/source") {}
        }
    }

    @Test
    fun availableRegistryUsesTheCoordinatorSnapshot() = runTest {
        val packageId = SourceId("external-source")
        val installed = InstalledSourcePackage(packageId, "1.0.0", "package/path")
        val coordinator = coordinatorWith(packageId)
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = coordinator,
            activePackageLoaderFactory = { requestedId ->
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(
                        sourceId = requestedId,
                        store = InMemoryStore(SourcePackageActivationState(active = installed)),
                    ),
                    manifestReader = SourcePackageManifestReader { manifest(packageId) },
                )
            },
            closeResources = {},
        )

        // The coordinator snapshot is populated by the platform-independent test helper below.
        val registry = platform.loadAvailableActiveRegistry(
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ -> error("Runtime must remain lazy") },
        )

        assertEquals(listOf(packageId), registry.sources.map { it.id })
    }

    private suspend fun coordinatorWith(sourceId: SourceId): ExternalSourceRepositoryCoordinator {
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
        return ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                    override fun load() = listOf(endpoint)

                    override fun persistAtomically(
                        repositories: List<SourceRepositoryEndpoint>,
                    ) = Unit
                }),
                loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                    SourceRepositoryResponse(
                        statusCode = 200,
                        body = SourceRepositoryIndexCodec.encode(
                            SourceRepositoryIndex(
                                apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
                                sources = listOf(manifest(sourceId)),
                            ),
                        ),
                    )
                }),
            ),
        ).also { it.refresh(clientVersion = 1) }
    }

    private fun manifest(sourceId: SourceId) = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = sourceId,
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
    )

    private class InMemoryStore(
        private val state: SourcePackageActivationState,
    ) : SourcePackageActivationStore {
        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) = Unit
    }
}
