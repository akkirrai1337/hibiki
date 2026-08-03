package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.DownloadedSourcePackage
import org.akkirrai.beakokit.api.ExtractedSourcePackage
import org.akkirrai.beakokit.api.ExternalSourceRuntime
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
import org.akkirrai.beakokit.api.SourcePackageArtifactVerifier
import org.akkirrai.beakokit.api.SourcePackageEntry
import org.akkirrai.beakokit.api.SourcePackageExtractor
import org.akkirrai.beakokit.api.SourcePackageInstallationCoordinatorFactory
import org.akkirrai.beakokit.api.SourcePackageLayoutValidator
import org.akkirrai.beakokit.api.SourcePackageManifestReader
import org.akkirrai.beakokit.api.SourcePackageTransport
import org.akkirrai.beakokit.api.SourcePackageValidator
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
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle

class ExternalSourceRuntimeCoordinatorTest {
    @Test
    fun refreshBuildsExternalRegistryWithoutChangingTheBuiltInPath() = runTest {
        val sourceId = SourceId("external-source")
        val platform = platformFor(sourceId)
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        coordinator.refresh()

        val snapshot = coordinator.snapshot.value
        assertEquals(listOf(sourceId), snapshot.registry?.sources?.map { it.id })
        assertEquals(emptyList<Throwable>(), listOfNotNull(snapshot.error))
    }

    @Test
    fun repositoryChangesRefreshTheInactiveRegistryWithoutChangingTheBuiltInPath() = runTest {
        val sourceId = SourceId("external-source")
        var repositoryLoads = 0
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platformFor(
                sourceId = sourceId,
                beforeRepositoryLoad = { repositoryLoads++ },
            ),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )
        val secondEndpoint = SourceRepositoryEndpoint("https://second.example.test/index.json")

        assertEquals(
            listOf("https://example.test/index.json"),
            coordinator.repositories().map { it.url },
        )
        assertEquals(
            listOf("https://example.test/index.json"),
            coordinator.snapshot.value.configuredRepositories.map { it.url },
        )
        assertEquals(
            "1.0.0",
            coordinator.activePackage(SourceId("external-source"))?.installed?.packageVersion,
        )
        assertEquals(null, coordinator.activePackage(SourceId("missing-source")))

        coordinator.refresh()
        assertEquals(1, repositoryLoads)
        coordinator.addRepository(SourceRepositoryEndpoint("https://example.test/index.json"))
        coordinator.removeRepository("https://missing.example/index.json")
        assertEquals(1, repositoryLoads)

        coordinator.addRepository(secondEndpoint)
        assertEquals(3, repositoryLoads)

        assertEquals(
            listOf("https://example.test/index.json", secondEndpoint.url),
            coordinator.repositories().map { it.url },
        )
        assertEquals(
            listOf("https://example.test/index.json", secondEndpoint.url),
            coordinator.snapshot.value.configuredRepositories.map { it.url },
        )
        assertEquals(
            listOf(
                "https://example.test/index.json",
                secondEndpoint.url,
            ),
            coordinator.snapshot.value.repository.loaded.map { it.endpoint.url },
        )
        assertEquals(listOf(sourceId), coordinator.snapshot.value.registry?.sources?.map { it.id })

        coordinator.removeRepository("https://example.test/index.json")

        assertEquals(listOf(secondEndpoint.url), coordinator.repositories().map { it.url })
        assertEquals(
            listOf(secondEndpoint.url),
            coordinator.snapshot.value.configuredRepositories.map { it.url },
        )
        assertEquals(
            listOf(secondEndpoint.url),
            coordinator.snapshot.value.repository.loaded.map { it.endpoint.url },
        )
        assertEquals(listOf(sourceId), coordinator.snapshot.value.registry?.sources?.map { it.id })
    }

    @Test
    fun activePackageReadDoesNotMaskUnexpectedStorageErrors() = runTest {
        val sourceId = SourceId("external-source")
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platformFor(
                sourceId = sourceId,
                beforeLoad = { error("Package storage is corrupted") },
            ),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        assertFailsWith<IllegalStateException> {
            coordinator.activePackage(sourceId)
        }
    }

    @Test
    fun failedRegistryBuildKeepsTheLastSuccessfulRegistry() = runTest {
        val sourceId = SourceId("external-source")
        var packageLoadFails = false
        val platform = platformFor(
            sourceId = sourceId,
            beforeLoad = {
                if (packageLoadFails) {
                    error("Active package is temporarily unreadable")
                }
            },
        )
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        coordinator.refresh()
        val previous = coordinator.snapshot.value
        val previousRegistry = assertNotNull(previous.registry)

        packageLoadFails = true
        val error = assertFailsWith<IllegalStateException> { coordinator.refresh() }

        val failed = coordinator.snapshot.value
        assertSame(previousRegistry, failed.registry)
        assertEquals(previous.repository, failed.repository)
        assertEquals(previous.configuredRepositories, failed.configuredRepositories)
        assertSame(error, failed.error)

        packageLoadFails = false
        coordinator.refresh()
        assertEquals(null, coordinator.snapshot.value.error)
    }

    @Test
    fun rollbackRebuildsOnlyTheInactiveExternalRegistry() = runTest {
        val sourceId = SourceId("external-source")
        val first = InstalledSourcePackage(sourceId, "1.0.0", "package/1")
        val second = InstalledSourcePackage(sourceId, "2.0.0", "package/2")
        val store = InMemoryStore(SourcePackageActivationState(active = second, previous = first))
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
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
                                sources = listOf(manifest(sourceId)),
                            ),
                        ),
                    )
                }),
            ),
        )
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = coordinator,
            activePackageLoaderFactory = { requestedId ->
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(requestedId, store),
                    manifestReader = SourcePackageManifestReader { packagePath ->
                        manifest(sourceId).copy(
                            packageVersion = if (packagePath.endsWith("/2")) "2.0.0" else "1.0.0",
                        )
                    },
                )
            },
            activationRepositoryFactory = { requestedId ->
                SourcePackageActivationRepository(requestedId, store)
            },
            closeResources = {},
        )
        val createdVersions = mutableListOf<String>()
        val runtimeCoordinator = ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { sourcePackage, _ ->
                createdVersions += sourcePackage.installed.packageVersion
                object : ExternalSourceRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                    override suspend fun details(id: String): AnimeTitle = title(id)
                }
            },
        )
        val client = HttpClient()
        runtimeCoordinator.refresh()
        runtimeCoordinator.snapshot.value.registry!!.create(
            sourceId,
            DefaultSourceContext(
                httpClient = client,
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
            ),
        )
        assertEquals(listOf("2.0.0"), createdVersions)

        val rollback = runtimeCoordinator.rollbackActivePackage(sourceId)
        runtimeCoordinator.snapshot.value.registry!!.create(
            sourceId,
            DefaultSourceContext(
                httpClient = client,
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
            ),
        )

        assertEquals("1.0.0", rollback.active?.packageVersion)
        assertEquals(listOf("2.0.0", "1.0.0"), createdVersions)
    }

    @Test
    fun availablePackageUpdatesReportVersionMismatchesWithoutGuessingOrdering() = runTest {
        val sourceId = SourceId("external-source")
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platformFor(
                sourceId = sourceId,
                repositoryPackageVersion = "10",
                installedPackageVersion = "2",
            ),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        coordinator.refresh()

        val status = coordinator.packageStatuses().single()
        assertEquals(sourceId, status.sourceId)
        assertEquals("2", status.activePackage?.installed?.packageVersion)
        assertEquals("10", status.availableManifest.packageVersion)
        assertEquals(true, status.updateAvailable)

        val update = coordinator.availablePackageUpdates().single()
        assertEquals(sourceId, update.sourceId)
        assertEquals("2", update.installedVersion)
        assertEquals("a".repeat(64), update.installedSha256)
        assertEquals("10", update.availableManifest.packageVersion)
    }

    @Test
    fun availablePackageUpdatesReportRebuiltArtifactsWithTheSameVersion() = runTest {
        val sourceId = SourceId("external-source")
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platformFor(
                sourceId = sourceId,
                repositoryPackageSha256 = "b".repeat(64),
                installedPackageSha256 = "a".repeat(64),
            ),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        coordinator.refresh()

        val update = coordinator.availablePackageUpdates().single()
        assertEquals(sourceId, update.sourceId)
        assertEquals("a".repeat(64), update.installedSha256)
    }

    @Test
    fun installedPackage_is_available_through_the_external_registry() = runTest {
        val sourceId = SourceId("external-source")
        val repositoryManifest = manifest(sourceId).copy(packageVersion = "2.0.0")
        val store = InMemoryStore(
            SourcePackageActivationState(
                active = InstalledSourcePackage(sourceId, "1.0.0", "package/old"),
            ),
        )
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
        val repositoryCoordinator = ExternalSourceRepositoryCoordinator(
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
                                sources = listOf(repositoryManifest),
                            ),
                        ),
                    )
                }),
            ),
        )
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = repositoryCoordinator,
            activePackageLoaderFactory = { requestedId ->
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(requestedId, store),
                    manifestReader = SourcePackageManifestReader { packagePath ->
                        repositoryManifest.copy(
                            packageVersion = if (packagePath == "package/old") "1.0.0" else "2.0.0",
                        )
                    },
                )
            },
            packageInstallationFactory = SourcePackageInstallationCoordinatorFactory(
                downloadService = org.akkirrai.beakokit.api.SourcePackageDownloadService(
                    transport = SourcePackageTransport { _, _ ->
                        DownloadedSourcePackage(byteArrayOf(1))
                    },
                    artifactVerifier = SourcePackageArtifactVerifier(
                        validator = SourcePackageValidator(clientVersion = 1),
                        sha256 = { "a".repeat(64) },
                    ),
                ),
                extractor = SourcePackageExtractor { _, _, manifest ->
                    ExtractedSourcePackage(
                        manifest = manifest,
                        entries = listOf(
                            SourcePackageEntry("manifest.json", 0),
                            SourcePackageEntry(manifest.entrypoint, 1),
                        ),
                    )
                },
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationStoreFactory = { store },
            ),
            stagingPathFactory = { "package/new" },
            activationRepositoryFactory = { requestedId ->
                SourcePackageActivationRepository(requestedId, store)
            },
            closeResources = {},
        )
        val runtimeCoordinator = ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                object : ExternalSourceRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> =
                        listOf(title("search-result"))

                    override suspend fun details(id: String): AnimeTitle = title("details-result")
                }
            },
        )

        runtimeCoordinator.refresh()
        val previousRegistry = assertNotNull(runtimeCoordinator.snapshot.value.registry)
        val initializationStarted = CompletableDeferred<Unit>()
        val continueInitialization = CompletableDeferred<Unit>()
        val installation = async {
            runtimeCoordinator.installAvailablePackage(sourceId) {
                initializationStarted.complete(Unit)
                continueInitialization.await()
            }
        }
        initializationStarted.await()
        assertSame(previousRegistry, runtimeCoordinator.snapshot.value.registry)
        continueInitialization.complete(Unit)
        installation.await()

        val client = HttpClient()
        try {
            val source = runtimeCoordinator.snapshot.value.registry!!.create(
                sourceId,
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                ),
            )

            assertEquals("2.0.0", store.state.active?.packageVersion)
            assertEquals("1.0.0", store.state.previous?.packageVersion)
            assertEquals("search-result", source.search("frieren").single().id)
            assertEquals("details-result", source.getById("title-1").id)
        } finally {
            client.close()
        }
    }

    @Test
    fun failedRegistryRebuildRollsBackAnAlreadyActiveUpdate() = runTest {
        val sourceId = SourceId("external-source")
        val oldPackage = InstalledSourcePackage(sourceId, "1.0.0", "package/old")
        val store = InMemoryStore(SourcePackageActivationState(active = oldPackage))
        val repositoryManifest = manifest(sourceId).copy(packageVersion = "2.0.0")
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
        val repositoryCoordinator = ExternalSourceRepositoryCoordinator(
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
                                sources = listOf(repositoryManifest),
                            ),
                        ),
                    )
                }),
            ),
        )
        val platform = ExternalSourceRepositoryPlatform(
            coordinator = repositoryCoordinator,
            activePackageLoaderFactory = { requestedId ->
                val active = store.load(requestedId).active
                    ?: error("An active package is required")
                if (active.packageVersion == repositoryManifest.packageVersion) {
                    error("The newly activated package is intentionally unreadable")
                }
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(requestedId, store),
                    manifestReader = SourcePackageManifestReader {
                        manifest(sourceId).copy(packageVersion = active.packageVersion)
                    },
                )
            },
            packageInstallationFactory = SourcePackageInstallationCoordinatorFactory(
                downloadService = org.akkirrai.beakokit.api.SourcePackageDownloadService(
                    transport = SourcePackageTransport { _, _ ->
                        DownloadedSourcePackage(byteArrayOf(1))
                    },
                    artifactVerifier = SourcePackageArtifactVerifier(
                        validator = SourcePackageValidator(clientVersion = 1),
                        sha256 = { "a".repeat(64) },
                    ),
                ),
                extractor = SourcePackageExtractor { _, _, repositoryManifest ->
                    ExtractedSourcePackage(
                        manifest = repositoryManifest,
                        entries = listOf(
                            SourcePackageEntry("manifest.json", 0),
                            SourcePackageEntry(repositoryManifest.entrypoint, 1),
                        ),
                    )
                },
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationStoreFactory = { store },
            ),
            stagingPathFactory = { "package/new" },
            activationRepositoryFactory = { requestedId ->
                SourcePackageActivationRepository(requestedId, store)
            },
            closeResources = {},
        )
        val runtimeCoordinator = ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        runtimeCoordinator.refresh()
        val previousRegistry = assertNotNull(runtimeCoordinator.snapshot.value.registry)

        assertFailsWith<IllegalStateException> {
            runtimeCoordinator.installAvailablePackage(sourceId) {}
        }
        assertSame(previousRegistry, runtimeCoordinator.snapshot.value.registry)
        assertEquals(oldPackage, store.state.active)
        assertEquals(null, store.state.previous)
    }

    @Test
    fun cancelledRefreshDoesNotBecomeSnapshotError() = runTest {
        val sourceId = SourceId("external-source")
        val repositoryRequestStarted = CompletableDeferred<Unit>()
        val platform = platformFor(
            sourceId = sourceId,
            beforeRepositoryLoad = {
                repositoryRequestStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
        )
        val coordinator = ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                error("Runtime creation must remain lazy")
            },
        )

        val refresh = launch { coordinator.refresh() }
        repositoryRequestStarted.await()
        runCurrent()
        refresh.cancelAndJoin()

        assertNull(coordinator.snapshot.value.error)
    }

    private fun platformFor(
        sourceId: SourceId,
        beforeLoad: () -> Unit = {},
        beforeRepositoryLoad: suspend () -> Unit = {},
        repositoryPackageVersion: String = "1.0.0",
        installedPackageVersion: String = "1.0.0",
        repositoryPackageSha256: String = "a".repeat(64),
        installedPackageSha256: String = repositoryPackageSha256,
    ): ExternalSourceRepositoryPlatform {
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
        val repositoryEndpoints = mutableListOf(endpoint)
        val manifest = manifest(sourceId).copy(
            packageVersion = repositoryPackageVersion,
            sha256 = repositoryPackageSha256,
        )
        val coordinator = ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                    override fun load() = repositoryEndpoints.toList()

                    override fun persistAtomically(
                        repositories: List<SourceRepositoryEndpoint>,
                    ) {
                        repositoryEndpoints.clear()
                        repositoryEndpoints.addAll(repositories)
                    }
                }),
                loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                    beforeRepositoryLoad()
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
        val installed = InstalledSourcePackage(sourceId, installedPackageVersion, "package/path")
        return ExternalSourceRepositoryPlatform(
            coordinator = coordinator,
            activePackageLoaderFactory = { requestedId ->
                beforeLoad()
                ActiveExternalSourcePackageLoader(
                    activationRepository = SourcePackageActivationRepository(
                        sourceId = requestedId,
                        store = InMemoryStore(
                            SourcePackageActivationState(active = installed),
                        ),
                    ),
                    manifestReader = SourcePackageManifestReader {
                        manifest(sourceId).copy(
                            packageVersion = installedPackageVersion,
                            sha256 = installedPackageSha256,
                        )
                    },
                )
            },
            closeResources = {},
        )
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

    private fun title(id: String) = AnimeTitle(
        id = id,
        russianName = null,
        englishName = id,
        originalName = id,
        japaneseName = null,
        synonyms = emptyList(),
        year = null,
        type = null,
        episodeCount = null,
        posterUrl = null,
        status = null,
        description = null,
    )

    private class InMemoryStore(
        var state: SourcePackageActivationState,
    ) : SourcePackageActivationStore {
        override fun load(sourceId: SourceId): SourcePackageActivationState {
            if (state.active?.sourceId != null && state.active?.sourceId != sourceId) {
                return SourcePackageActivationState()
            }
            return state
        }

        override fun persistAtomically(
            sourceId: SourceId,
            state: SourcePackageActivationState,
        ) {
            this.state = state
        }
    }
}
