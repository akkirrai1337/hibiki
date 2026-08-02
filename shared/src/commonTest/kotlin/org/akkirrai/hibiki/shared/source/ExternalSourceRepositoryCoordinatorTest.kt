package org.akkirrai.hibiki.shared.source

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryIndexCodec
import org.akkirrai.beakokit.api.SourceRepositoryResponse
import org.akkirrai.beakokit.api.SourceRepositoryStore
import org.akkirrai.beakokit.api.SourceRepositoryTransport
import org.akkirrai.beakokit.api.SourceRepositoryLoader
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourceRuntime
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceLanguage

class ExternalSourceRepositoryCoordinatorTest {
    @Test
    fun refreshReplacesTheBackgroundSnapshot() = runTest {
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
        val coordinator = ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(FakeStore(listOf(endpoint))),
                loader = SourceRepositoryLoader(
                    SourceRepositoryTransport { _, _ ->
                        SourceRepositoryResponse(200, SourceRepositoryIndexCodec.encode(index()))
                    },
                ),
            ),
        )

        assertEquals(emptyList(), coordinator.snapshot.value.loaded)
        val refreshed = coordinator.refresh(clientVersion = 1)

        assertEquals(refreshed, coordinator.snapshot.value)
        assertEquals(listOf(endpoint), refreshed.loaded.map { it.endpoint })
        assertEquals(listOf(SourceId("external-source")), coordinator.availableSourceIds())
        assertEquals(index().sources.single(), coordinator.availableSourceManifest(SourceId("external-source")))
    }

    @Test
    fun availableManifestsUseTheFirstRepositoryForDuplicateSourceIds() = runTest {
        val first = SourceRepositoryEndpoint("https://first.example/index.json")
        val second = SourceRepositoryEndpoint("https://second.example/index.json")
        val firstManifest = index().sources.single()
        val secondManifest = firstManifest.copy(packageVersion = "2.0.0")
        val coordinator = ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(FakeStore(listOf(first, second))),
                loader = SourceRepositoryLoader(
                    SourceRepositoryTransport { url, _ ->
                        SourceRepositoryResponse(
                            200,
                            SourceRepositoryIndexCodec.encode(
                                index().copy(sources = listOf(if (url == first.url) firstManifest else secondManifest)),
                            ),
                        )
                    },
                ),
            ),
        )

        coordinator.refresh(clientVersion = 1)

        assertEquals(listOf(firstManifest), coordinator.availableSourceManifests())
    }

    @Test
    fun concurrentRefreshesDoNotOverlap() = runTest {
        var activeLoads = 0
        var overlapped = false
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")
        val coordinator = ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(FakeStore(listOf(endpoint))),
                loader = SourceRepositoryLoader(
                    SourceRepositoryTransport { _, _ ->
                        activeLoads++
                        overlapped = overlapped || activeLoads > 1
                        delay(1)
                        activeLoads--
                        SourceRepositoryResponse(200, SourceRepositoryIndexCodec.encode(index()))
                    },
                ),
            ),
        )

        listOf(
            async { coordinator.refresh(clientVersion = 1) },
            async { coordinator.refresh(clientVersion = 1) },
        ).awaitAll()

        assertFalse(overlapped)
    }

    @Test
    fun repositoryManagementIsSeparateFromTheLoadedSnapshot() = runTest {
        val store = FakeStore()
        val coordinator = ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(store),
                loader = SourceRepositoryLoader(
                    SourceRepositoryTransport { _, _ ->
                        error("Repository loading is not part of this test")
                    },
                ),
            ),
        )
        val endpoint = SourceRepositoryEndpoint("https://example.test/index.json")

        assertEquals(emptyList(), coordinator.repositories())
        assertEquals(listOf(endpoint), coordinator.addRepository(endpoint))
        assertEquals(listOf(endpoint), coordinator.addRepository(endpoint))
        assertEquals(emptyList(), coordinator.removeRepository(endpoint.url))
        assertEquals(emptyList(), coordinator.snapshot.value.loaded)
    }

    @Test
    fun removingRepositoryInvalidatesOnlyItsLoadedSnapshotEntries() = runTest {
        val first = SourceRepositoryEndpoint("https://first.example/index.json")
        val second = SourceRepositoryEndpoint("https://second.example/index.json")
        val store = FakeStore(listOf(first, second))
        val coordinator = ExternalSourceRepositoryCoordinator(
            SourceRepositoryCatalogLoader(
                catalog = SourceRepositoryCatalog(store),
                loader = SourceRepositoryLoader(
                    SourceRepositoryTransport { url, _ ->
                        SourceRepositoryResponse(
                            200,
                            SourceRepositoryIndexCodec.encode(index().copy(
                                sources = listOf(index().sources.single().copy(
                                    sourceId = SourceId(if (url == first.url) "first-source" else "second-source"),
                                )),
                            )),
                        )
                    },
                ),
            ),
        )

        coordinator.refresh(clientVersion = 1)
        coordinator.removeRepository(first.url)

        assertEquals(listOf(SourceId("second-source")), coordinator.availableSourceIds())
        assertEquals(listOf(second), coordinator.repositories())
    }

    private fun index() = org.akkirrai.beakokit.api.SourceRepositoryIndex(
        apiVersion = org.akkirrai.beakokit.api.SourceRepositoryIndex.CURRENT_API_VERSION,
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

    private class FakeStore(
        private var endpoints: List<SourceRepositoryEndpoint> = emptyList(),
    ) : SourceRepositoryStore {
        override fun load(): List<SourceRepositoryEndpoint> = endpoints

        override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
            endpoints = repositories
        }
    }
}
