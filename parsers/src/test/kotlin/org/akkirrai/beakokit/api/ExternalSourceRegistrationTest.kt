package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType

class ExternalSourceRegistrationTest {
    @Test
    fun activePackageAllowsArchiveChecksumToDifferFromPackageManifestMetadata() {
        ActiveExternalSourcePackage(
            manifest = manifest(),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
                artifactSha256 = "b".repeat(64),
            ),
        )
    }

    @Test
    fun activePackageRejectsUnsafeEntrypoint() {
        assertFailsWith<IllegalArgumentException> {
            ActiveExternalSourcePackage(
                manifest = manifest().copy(entrypoint = "../source.wasm"),
                installed = InstalledSourcePackage(
                    sourceId = SourceId("external-test"),
                    packageVersion = "1.0.0",
                    packagePath = "sources/external-test/1.0.0",
                ),
            )
        }
    }

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
    fun external_config_is_validated_before_runtime_creation() {
        var runtimeCreated = false
        val registration = ExternalSourceRegistration(
            info = sourceInfo().copy(
                configSchema = SourceConfigSchema(
                    listOf(
                        SourceConfigField("base_url", SourceConfigValueKind.HTTPS_URL, required = true),
                    ),
                ),
            ),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtimeFactory = {
                runtimeCreated = true
                error("Invalid config must prevent runtime creation")
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            config = MapSourceConfig(values = mapOf("base_url" to "http://example.com")),
        )

        assertFailsWith<SourceConfigException> {
            externalSourceCatalog(listOf(registration)).create(SourceId("external-test"), context)
        }
        assertEquals(false, runtimeCreated)
    }

    @Test
    fun playbackCapabilityRequiresPlaybackRuntimeAtSourceCreation() {
        val registration = ExternalSourceRegistration(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtimeFactory = {
                object : ExternalSourceRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                    override suspend fun details(id: String): AnimeTitle = title(id)
                }
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        assertFailsWith<IllegalArgumentException> {
            externalSourceCatalog(listOf(registration)).create(SourceId("external-test"), context)
        }
    }

    @Test
    fun latestCapabilityCreatesLatestSourceThroughTheNewPipeline() = runBlocking {
        val registration = ExternalSourceRegistration(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.LATEST_RELEASES)),
            catalogCapabilities = CatalogCapabilities.FULL.copy(
                features = setOf(CatalogFeature.LATEST_RELEASES),
            ),
            runtimeFactory = {
                object : ExternalSourceLatestRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                    override suspend fun details(id: String): AnimeTitle = title(id)

                    override suspend fun latest(limit: Int): List<AnimeTitle> =
                        listOf(title("latest-result")).take(limit)
                }
            },
        )
        val client = HttpClient(MockEngine { error("Network is not expected in this test") })
        try {
            val source = externalSourceCatalog(listOf(registration)).create(
                SourceId("external-test"),
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                ),
            )

            val latest = source as? LatestSource ?: error("Latest capability was not registered")
            assertEquals(listOf("latest-result"), latest.latest(10).map(AnimeTitle::id))
        } finally {
            client.close()
        }
    }

    @Test
    fun latestCapabilityRequiresLatestRuntimeAtSourceCreation() {
        val registration = ExternalSourceRegistration(
            info = sourceInfo().copy(capabilities = setOf(SourceCapability.LATEST_RELEASES)),
            catalogCapabilities = CatalogCapabilities.FULL.copy(
                features = setOf(CatalogFeature.LATEST_RELEASES),
            ),
            runtimeFactory = {
                object : ExternalSourceRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                    override suspend fun details(id: String): AnimeTitle = title(id)
                }
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        assertFailsWith<IllegalArgumentException> {
            externalSourceCatalog(listOf(registration)).create(SourceId("external-test"), context)
        }
    }

    @Test
    fun `catalog cannot advertise latest without source capability`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRegistration(
                info = sourceInfo(),
                catalogCapabilities = CatalogCapabilities.FULL.copy(
                    features = setOf(CatalogFeature.LATEST_RELEASES),
                ),
                runtimeFactory = { error("Runtime must not be created") },
            )
        }
    }

    @Test
    fun `catalog cannot advertise unsupported schedule feature`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRegistration(
                info = sourceInfo(),
                catalogCapabilities = CatalogCapabilities.FULL.copy(
                    features = setOf(CatalogFeature.SCHEDULE),
                ),
                runtimeFactory = { error("Runtime must not be created") },
            )
        }
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
    fun activePackagesBuildARegistryWithoutStartingRuntime() {
        var runtimeCreated = false
        val registry = activeExternalSourceRegistry(
            packages = listOf(
                ActiveExternalSourcePackage(
                    manifest = manifest(),
                    installed = InstalledSourcePackage(
                        sourceId = SourceId("external-test"),
                        packageVersion = "1.0.0",
                        packagePath = "sources/external-test/1.0.0",
                    ),
                ),
            ),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                runtimeCreated = true
                error("Runtime must be lazy")
            },
        )

        assertEquals(listOf(SourceId("external-test")), registry.sources.map(SourceInfo::id))
        assertEquals(false, runtimeCreated)
    }

    @Test
    fun activePackageRunsSearchAndDetailsThroughTheNewPipeline() = runBlocking {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest(),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val registry = activeExternalSourceRegistry(
            packages = listOf(activePackage),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                ProtocolBackedExternalSourceRuntime(
                    transport = ExternalSourceRuntimeTransport { request, _ ->
                        val payload = when (request.operation) {
                            ExternalSourceRuntimeOperation.SEARCH ->
                                AnimeTitleRuntimePayloadCodec.encodeSearch(listOf(title("search-result")))
                            ExternalSourceRuntimeOperation.FILTER_CATALOG ->
                                AnimeTitleRuntimePayloadCodec.encodeFilterCatalog(AnimeSearchFilterCatalog())
                            ExternalSourceRuntimeOperation.DETAILS ->
                                AnimeTitleRuntimePayloadCodec.encodeDetails(title("details-result"))
                            ExternalSourceRuntimeOperation.LATEST ->
                                AnimeTitleRuntimePayloadCodec.encodeSearch(emptyList())
                            else -> error("Playback operation is not part of this catalog-only fixture")
                        }
                        ExternalSourceRuntimeResponse(
                            requestId = request.requestId,
                            payload = payload,
                        )
                    },
                    payloadCodec = AnimeTitleRuntimePayloadCodec,
                    requestIdFactory = { "pipeline-request" },
                )
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )
        val source = registry.create(SourceId("external-test"), context)

        assertEquals("search-result", source.search("frieren").single().id)
        assertEquals("details-result", source.getById("title-1").id)
    }

    @Test
    fun activePlaybackPackageCreatesPlaybackSourceThroughTheNewPipeline() = runBlocking {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val group = PlaybackGroup(
            id = "group-1",
            title = "Dub",
            episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
        )
        val link = PlayerLink("https://example.com/video.mp4", PlayerType.DIRECT_MP4, "720p")
        val registry = activeExternalSourceRegistry(
            packages = listOf(activePackage),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                ProtocolBackedExternalSourcePlaybackRuntime(
                    transport = ExternalSourceRuntimeTransport { request, _ ->
                        val payload = when (request.operation) {
                            ExternalSourceRuntimeOperation.DETAILS ->
                                AnimeTitleRuntimePayloadCodec.encodeDetails(title("details-result"))
                            ExternalSourceRuntimeOperation.LATEST ->
                                AnimeTitleRuntimePayloadCodec.encodeSearch(emptyList())
                            ExternalSourceRuntimeOperation.PLAYBACK_GROUPS ->
                                AnimeTitleRuntimePayloadCodec.encodePlaybackGroups(listOf(group))
                            ExternalSourceRuntimeOperation.PLAYER_LINKS ->
                                AnimeTitleRuntimePayloadCodec.encodePlayerLinks(listOf(link))
                            else -> error("Catalog operation is not part of this playback fixture")
                        }
                        ExternalSourceRuntimeResponse(requestId = request.requestId, payload = payload)
                    },
                    payloadCodec = AnimeTitleRuntimePayloadCodec,
                    requestIdFactory = { "playback-pipeline-request" },
                )
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )
        val source = registry.create(SourceId("external-test"), context)
        val playback = source as? PlaybackSource ?: error("Playback capability was not registered")
        val title = source.getById("title-1")

        assertEquals(listOf(group), playback.getPlaybackGroups(title))
        assertEquals(listOf(link), playback.getPlayerLinks(title, group, group.episodes.single()))
    }

    @Test
    fun playbackAdapterRejectsOperationsWhenManifestOmitsPlaybackCapability() = runBlocking {
        var runtimeCalled = false
        val source = RuntimeBackedPlaybackAnimeSource(
            info = sourceInfo().copy(capabilities = emptySet()),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtime = object : ExternalSourcePlaybackRuntime {
                override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                override suspend fun details(id: String): AnimeTitle = title(id)

                override suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup> {
                    runtimeCalled = true
                    return emptyList()
                }

                override suspend fun playerLinks(
                    title: AnimeTitle,
                    group: PlaybackGroup,
                    episode: Episode,
                ): List<PlayerLink> {
                    runtimeCalled = true
                    return emptyList()
                }
            },
        )

        val error = assertFailsWith<SourceException> {
            source.getPlaybackGroups(title("title-1"))
        }

        assertEquals(SourceErrorCode.UNSUPPORTED_OPERATION, error.code)
        assertEquals(false, runtimeCalled)
    }

    @Test
    fun playbackCapabilityRejectsRuntimeWithoutPlaybackContract() = runBlocking {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val registry = activeExternalSourceRegistry(
            packages = listOf(activePackage),
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = ExternalSourceRuntimeFactory { _, _ ->
                object : ExternalSourceRuntime {
                    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

                    override suspend fun details(id: String): AnimeTitle = title(id)
                }
            },
        )
        val client = HttpClient(MockEngine { error("Network is not expected in this test") })
        try {
            val error = assertFailsWith<IllegalArgumentException> {
                registry.create(
                    id = SourceId("external-test"),
                    context = DefaultSourceContext(
                        httpClient = client,
                        preferredLanguages = listOf(SourceLanguage.ENGLISH),
                    ),
                )
            }
            assertTrue(error.message.orEmpty().contains("does not implement playback"))
        } finally {
            client.close()
        }
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
            runtimeFactory = ExternalSourceRuntimeFactory { sourcePackage, _ ->
                receivedPath = sourcePackage.installed.packagePath
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
    fun nativeBridgeFactoryConnectsAnActivePackageToTheCommonRuntime() = runBlocking {
        var receivedPath: String? = null
        var receivedModule: ByteArray? = null
        var receivedRequirements: SourceHostRequirements? = null
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest(),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val runtimeFactory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { sourcePackage, _, module, hostRequirements ->
                receivedPath = sourcePackage.installed.packagePath
                receivedModule = module
                receivedRequirements = hostRequirements
                ExternalSourceRuntimeNativeBridge { request, _ ->
                    val decoded = ExternalSourceRuntimeProtocolCodec.decodeRequest(request)
                    ExternalSourceRuntimeProtocolCodec.encodeResponse(
                        ExternalSourceRuntimeResponse(
                            requestId = decoded.requestId,
                            payload = AnimeTitleRuntimePayloadCodec.encodeDetails(title("native-factory")),
                        ),
                    )
                }
            },
            moduleReader = SourcePackageModuleReader { _, entrypoint ->
                assertEquals("source.wasm", entrypoint)
                byteArrayOf(1, 2, 3)
            },
            requestIdFactory = { "native-factory-request" },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        val runtime = runtimeFactory.create(activePackage, context)

        assertEquals("native-factory", runtime.details("title-1").id)
        assertEquals("sources/external-test/1.0.0", receivedPath)
        assertContentEquals(byteArrayOf(1, 2, 3), receivedModule)
        assertEquals(activePackage.manifest.hostRequirements(), receivedRequirements)
    }

    @Test
    fun nativeBridgeFactoryPreservesLatestCapabilityInRuntimeType() = runBlocking {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(capabilities = setOf(SourceCapability.LATEST_RELEASES)),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val factory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, _, _, _ ->
                ExternalSourceRuntimeNativeBridge { request, _ ->
                    val decoded = ExternalSourceRuntimeProtocolCodec.decodeRequest(request)
                    ExternalSourceRuntimeProtocolCodec.encodeResponse(
                        ExternalSourceRuntimeResponse(
                            requestId = decoded.requestId,
                            payload = AnimeTitleRuntimePayloadCodec.encodeSearch(listOf(title("latest"))),
                        ),
                    )
                }
            },
            moduleReader = SourcePackageModuleReader { _, _ -> byteArrayOf(1) },
            requestIdFactory = { "latest-runtime" },
        )

        val runtime = factory.create(
            activePackage,
            DefaultSourceContext(
                httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
            ),
        )

        assertTrue(runtime is ExternalSourceLatestRuntime)
        assertEquals(listOf("latest"), runtime.latest(1).map(AnimeTitle::id))
    }

    @Test
    fun nativeBridgeFactoryPreservesCombinedLatestAndPlaybackCapabilities() {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(
                capabilities = setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
            ),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val factory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, _, _, _ ->
                ExternalSourceRuntimeNativeBridge { _, _ -> error("Runtime call is not expected") }
            },
            moduleReader = SourcePackageModuleReader { _, _ -> byteArrayOf(1) },
            requestIdFactory = { "combined-runtime" },
        )

        val runtime = factory.create(
            activePackage,
            DefaultSourceContext(
                httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
            ),
        )

        assertTrue(runtime is ExternalSourceLatestPlaybackRuntime)
    }

    @Test
    fun nativeBridgeFactoryRejectsPlaybackCapabilityWithCatalogOnlyCodec() {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(capabilities = setOf(SourceCapability.PLAYBACK)),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val catalogOnlyCodec = object : ExternalSourceRuntimePayloadCodec {
            override fun decodeSearch(payload: JsonObject): List<AnimeTitle> = emptyList()

            override fun decodeDetails(payload: JsonObject): AnimeTitle = title("details")
        }
        var bridgeCreated = false
        var moduleRead = false
        val factory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, _, _, _ ->
                bridgeCreated = true
                ExternalSourceRuntimeNativeBridge { _, _ -> error("Runtime call is not expected") }
            },
            moduleReader = SourcePackageModuleReader { _, _ ->
                moduleRead = true
                byteArrayOf(1)
            },
            requestIdFactory = { "catalog-only-runtime" },
            payloadCodec = catalogOnlyCodec,
        )

        assertFailsWith<SourcePackageValidationException> {
            factory.create(
                activePackage,
                DefaultSourceContext(
                    httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                ),
            )
        }
        assertFalse(bridgeCreated)
        assertFalse(moduleRead)
    }

    @Test
    fun nativeBridgeFactoryRejectsUnsupportedRuntimeBeforeLoadingModule() {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(runtime = SourceRuntime("other-runtime", "abi-1")),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        val factory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, _, _, _ ->
                error("Unsupported runtime must not create a bridge")
            },
            moduleReader = SourcePackageModuleReader { _, _ ->
                error("Unsupported runtime must not load a module")
            },
            requestIdFactory = { "unsupported-runtime" },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        assertFailsWith<SourcePackageValidationException> {
            factory.create(activePackage, context)
        }
    }

    @Test
    fun nativeBridgeFactoryAcceptsAPlatformSuppliedRuntimePolicy() {
        val activePackage = ActiveExternalSourcePackage(
            manifest = manifest().copy(runtime = SourceRuntime("custom", "abi-1")),
            installed = InstalledSourcePackage(
                sourceId = SourceId("external-test"),
                packageVersion = "1.0.0",
                packagePath = "sources/external-test/1.0.0",
            ),
        )
        var bridgeCreated = false
        val factory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, _, _, _ ->
                bridgeCreated = true
                ExternalSourceRuntimeNativeBridge { _, _ -> error("Runtime call is not expected") }
            },
            moduleReader = SourcePackageModuleReader { _, _ -> byteArrayOf(1) },
            requestIdFactory = { "custom-runtime" },
            runtimeSupportPolicy = SourceRuntimeSupportPolicy { runtime ->
                runtime == SourceRuntime("custom", "abi-1")
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        factory.create(activePackage, context)

        assertTrue(bridgeCreated)
    }

    @Test
    fun activePackageLoaderRestoresManifestFromTheInstalledPath() {
        val installed = InstalledSourcePackage(
            sourceId = SourceId("external-test"),
            packageVersion = "1.0.0",
            packagePath = "sources/external-test/1.0.0",
        )
        val loader = ActiveExternalSourcePackageLoader(
            activationRepository = SourcePackageActivationRepository(
                sourceId = installed.sourceId,
                store = InMemoryActivationStore(SourcePackageActivationState(active = installed)),
            ),
            manifestReader = SourcePackageManifestReader { path ->
                assertEquals(installed.packagePath, path)
                manifest()
            },
        )

        val active = loader.load()

        assertEquals(installed, active?.installed)
        assertEquals(manifest(), active?.manifest)
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

    @Test
    fun activePackageLoaderAllowsArchiveChecksumToDifferFromPackageManifestMetadata() {
        val installed = InstalledSourcePackage(
            sourceId = SourceId("external-test"),
            packageVersion = "1.0.0",
            packagePath = "sources/external-test/1.0.0",
            artifactSha256 = "b".repeat(64),
        )
        val loader = ActiveExternalSourcePackageLoader(
            activationRepository = SourcePackageActivationRepository(
                sourceId = installed.sourceId,
                store = InMemoryActivationStore(SourcePackageActivationState(active = installed)),
            ),
            manifestReader = SourcePackageManifestReader { manifest() },
        )

        assertEquals(installed, loader.load()?.installed)
    }

    @Test
    fun activePackageLoaderRejectsUnsafeManifestEntrypoint() {
        val installed = InstalledSourcePackage(
            sourceId = SourceId("external-test"),
            packageVersion = "1.0.0",
            packagePath = "sources/external-test/1.0.0",
        )
        val loader = ActiveExternalSourcePackageLoader(
            activationRepository = SourcePackageActivationRepository(
                sourceId = installed.sourceId,
                store = InMemoryActivationStore(SourcePackageActivationState(active = installed)),
            ),
            manifestReader = SourcePackageManifestReader {
                manifest().copy(entrypoint = "../source.wasm")
            },
        )

        assertFailsWith<SourcePackageStateException> { loader.load() }
    }

    @Test
    fun activePackageLoaderNormalizesManifestReaderFailures() {
        val installed = InstalledSourcePackage(
            sourceId = SourceId("external-test"),
            packageVersion = "1.0.0",
            packagePath = "sources/external-test/1.0.0",
        )
        val loader = ActiveExternalSourcePackageLoader(
            activationRepository = SourcePackageActivationRepository(
                sourceId = installed.sourceId,
                store = InMemoryActivationStore(SourcePackageActivationState(active = installed)),
            ),
            manifestReader = SourcePackageManifestReader {
                throw IllegalStateException("manifest reader failed")
            },
        )

        assertFailsWith<SourcePackageStateException> { loader.load() }
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

    private class InMemoryActivationStore(
        private var state: SourcePackageActivationState,
    ) : SourcePackageActivationStore {
        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) {
            this.state = state
        }
    }
}
