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
import org.akkirrai.beakokit.api.SourceConfigState
import org.akkirrai.beakokit.api.SourceConfigStore
import org.akkirrai.beakokit.api.SourceConfigField
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceConfigValueKind
import org.akkirrai.beakokit.api.SourceHostCapability
import org.akkirrai.beakokit.model.CatalogCapabilities

class ExternalSourceRepositoryPlatformTest {
    @Test
    fun rollbackActivePackageUsesThePlatformActivationStore() {
        val sourceId = SourceId("external-source")
        val active = InstalledSourcePackage(sourceId, "2.0.0", "package/2")
        val previous = InstalledSourcePackage(sourceId, "1.0.0", "package/1")
        val store = InMemoryStore(SourcePackageActivationState(active = active, previous = previous))
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = emptyCoordinator(),
            activePackageLoaderFactory = { error("Active package loading is not used by this test") },
            activationRepositoryFactory = { requestedId ->
                SourcePackageActivationRepository(requestedId, store)
            },
            closeResources = {},
        )

        val state = platform.rollbackActivePackage(sourceId)

        assertEquals(previous, state.active)
        assertEquals(state, store.state)
    }

    @Test
    fun sourceConfigUsesThePlatformConfigStore() {
        val sourceId = SourceId("external-source")
        val store = InMemoryConfigStore()
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = emptyCoordinator(),
            activePackageLoaderFactory = { error("Package loading is not used by this test") },
            sourceConfigStore = store,
            closeResources = {},
        )
        val state = SourceConfigState(
            values = mapOf("base_url" to "https://source.test"),
            secrets = mapOf("token" to "secret"),
        )

        platform.persistSourceConfig(sourceId, state)

        assertEquals(state, platform.loadSourceConfig(sourceId))
        platform.removeSourceConfig(sourceId)
        assertEquals(SourceConfigState(), platform.loadSourceConfig(sourceId))
    }

    @Test
    fun missingSourceConfigRemainsMissingThroughThePlatformBoundary() {
        val sourceId = SourceId("external-source-missing-config")
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = emptyCoordinator(),
            activePackageLoaderFactory = { error("Package loading is not used by this test") },
            sourceConfigStore = MissingConfigStore,
            closeResources = {},
        )

        assertEquals(null, platform.loadSourceConfigOrNull(sourceId))
    }

    @Test
    fun sourceConfigIsValidatedAgainstTheActiveManifestSchema() {
        val sourceId = SourceId("configured-source")
        val installed = InstalledSourcePackage(sourceId, "1.0.0", "package/path")
        val manifest = manifest(sourceId).copy(
            hostCapabilities = setOf(SourceHostCapability.CONFIG),
            sourceInfo = manifest(sourceId).sourceInfo!!.copy(
                configSchema = SourceConfigSchema(
                    listOf(
                        SourceConfigField(
                            key = "base_url",
                            kind = SourceConfigValueKind.HTTPS_URL,
                            required = true,
                        ),
                    ),
                ),
            ),
        )
        val packageStore = InMemoryStore(SourcePackageActivationState(active = installed))
        val configStore = InMemoryConfigStore()
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = emptyCoordinator(),
            activePackageLoaderFactory = { requestedId ->
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(requestedId, packageStore),
                    manifestReader = SourcePackageManifestReader { manifest },
                )
            },
            sourceConfigStore = configStore,
            closeResources = {},
        )

        assertFailsWith<IllegalArgumentException> {
            platform.persistSourceConfig(
                sourceId,
                SourceConfigState(values = mapOf("base_url" to "http://insecure.test")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            platform.persistSourceConfig(sourceId, SourceConfigState())
        }
        val valid = SourceConfigState(values = mapOf("base_url" to "https://secure.test"))
        platform.persistSourceConfig(sourceId, valid)
        assertEquals(valid, platform.loadSourceConfig(sourceId))
    }

    @Test
    fun sourceConfigUsesTheAvailableManifestSchemaBeforeActivation() = runTest {
        val sourceId = SourceId("available-config-source")
        val manifest = manifest(sourceId).copy(
            hostCapabilities = setOf(SourceHostCapability.CONFIG),
            sourceInfo = manifest(sourceId).sourceInfo!!.copy(
                configSchema = SourceConfigSchema(
                    listOf(
                        SourceConfigField(
                            key = "base_url",
                            kind = SourceConfigValueKind.HTTPS_URL,
                            required = true,
                        ),
                    ),
                ),
            ),
        )
        val endpoint = SourceRepositoryEndpoint("https://available.example/index.json")
        val coordinator = ExternalSourceRepositoryCoordinator(
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
                                sources = listOf(manifest),
                            ),
                        ),
                    )
                }),
            ),
        )
        val configStore = InMemoryConfigStore()
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = coordinator,
            activePackageLoaderFactory = { error("The source is not active yet") },
            sourceConfigStore = configStore,
            closeResources = {},
        )
        coordinator.refresh(clientVersion = 1)

        assertFailsWith<IllegalArgumentException> {
            platform.persistSourceConfig(
                sourceId,
                SourceConfigState(values = mapOf("base_url" to "http://insecure.test")),
            )
        }
        val valid = SourceConfigState(values = mapOf("base_url" to "https://secure.test"))
        platform.persistSourceConfig(sourceId, valid)
        assertEquals(valid, platform.loadSourceConfig(sourceId))
    }

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
    fun activeRegistrySkipsCorruptedPackagesWithoutHidingHealthyOnes() {
        val healthyId = SourceId("healthy-source")
        val corruptedId = SourceId("corrupted-source")
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = emptyCoordinator(),
            activePackageLoaderFactory = { requestedId ->
                if (requestedId == corruptedId) {
                    throw SourcePackageStateException("Corrupted package")
                }
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(
                        sourceId = requestedId,
                        store = InMemoryStore(
                            SourcePackageActivationState(
                                active = InstalledSourcePackage(requestedId, "1.0.0", "package/path"),
                            ),
                        ),
                    ),
                    manifestReader = SourcePackageManifestReader { manifest(healthyId) },
                )
            },
            closeResources = {},
        )

        val registry = platform.loadActiveRegistry(
            sourceIds = listOf(corruptedId, healthyId),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ -> error("Runtime must remain lazy") },
        )

        assertEquals(listOf(healthyId), registry.sources.map { it.id })
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

    private fun emptyCoordinator() = ExternalSourceRepositoryCoordinator(
        SourceRepositoryCatalogLoader(
            catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                override fun load() = emptyList<SourceRepositoryEndpoint>()

                override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) = Unit
            }),
            loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                error("Repository transport is not used by this test")
            }),
        ),
    )

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
        var state: SourcePackageActivationState,
    ) : SourcePackageActivationStore {
        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) {
            this.state = state
        }
    }

    private class InMemoryConfigStore : SourceConfigStore {
        private val states = mutableMapOf<SourceId, SourceConfigState>()

        override fun load(sourceId: SourceId): SourceConfigState = states[sourceId] ?: SourceConfigState()

        override fun persistAtomically(sourceId: SourceId, state: SourceConfigState) {
            states[sourceId] = state
        }

        override fun remove(sourceId: SourceId) {
            states.remove(sourceId)
        }
    }

    private object MissingConfigStore : SourceConfigStore {
        override fun load(sourceId: SourceId): SourceConfigState = SourceConfigState()

        override fun loadOrNull(sourceId: SourceId): SourceConfigState? = null

        override fun persistAtomically(sourceId: SourceId, state: SourceConfigState) = Unit

        override fun remove(sourceId: SourceId) = Unit
    }
}
