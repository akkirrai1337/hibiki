package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SourceRepositoryCatalogLoaderTest {
    @Test
    fun keepsSuccessfulRepositoriesWhenAnotherOneFails() = runBlocking {
        val first = SourceRepositoryEndpoint("https://one.test/index.json")
        val second = SourceRepositoryEndpoint("https://two.test/index.json")
        val catalog = SourceRepositoryCatalog(
            FakeStore(mutableListOf(first, second)),
        )
        val loader = SourceRepositoryCatalogLoader(
            catalog = catalog,
            loader = SourceRepositoryLoader(
                transport = SourceRepositoryTransport { url, _ ->
                    if (url == second.url) {
                        SourceRepositoryResponse(503, "offline")
                    } else {
                        SourceRepositoryResponse(200, SourceRepositoryIndexCodec.encode(index()))
                    }
                },
            ),
        )

        val snapshot = loader.loadAll(clientVersion = 1)

        assertEquals(listOf(first), snapshot.loaded.map(LoadedSourceRepository::endpoint))
        assertEquals(listOf(second), snapshot.failures.map(SourceRepositoryLoadFailure::endpoint))
        assertIs<SourceRepositoryLoadException>(snapshot.failures.single().error)
    }

    @Test
    fun emptyCatalogProducesAnEmptySnapshot() = runBlocking {
        val snapshot = SourceRepositoryCatalogLoader(
            catalog = SourceRepositoryCatalog(FakeStore()),
            loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ -> error("not called") }),
        ).loadAll(clientVersion = 1)

        assertEquals(emptyList(), snapshot.loaded)
        assertEquals(emptyList(), snapshot.failures)
    }

    private fun index() = SourceRepositoryIndex(
        apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
        sources = listOf(
            SourceManifest(
                manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
                sourceId = SourceId("external-source"),
                packageVersion = "1.0.0",
                minClientVersion = 0,
                apiVersion = SourceApi.VERSION,
                hostApiVersion = SourceHostApi.VERSION,
                runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
                entrypoint = "source.wasm",
                packageUrl = "https://example.test/source.zip",
                sha256 = "a".repeat(64),
                artifactSizeBytes = 1,
                sourceInfo = SourceManifestInfo(
                    displayName = "External source",
                    languages = setOf(SourceLanguage.ENGLISH),
                    primaryLanguage = SourceLanguage.ENGLISH,
                ),
            ),
        ),
    )

    private class FakeStore(
        private val repositories: MutableList<SourceRepositoryEndpoint> = mutableListOf(),
    ) : SourceRepositoryStore {
        override fun load(): List<SourceRepositoryEndpoint> = repositories.toList()

        override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
            error("Not needed for this test")
        }
    }
}
