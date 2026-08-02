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
        private val endpoints: List<SourceRepositoryEndpoint>,
    ) : SourceRepositoryStore {
        override fun load(): List<SourceRepositoryEndpoint> = endpoints

        override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) = Unit
    }
}
