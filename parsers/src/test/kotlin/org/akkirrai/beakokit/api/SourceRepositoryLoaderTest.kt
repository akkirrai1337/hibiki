package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class SourceRepositoryLoaderTest {
    @Test
    fun repository_response_rejects_invalid_status_codes() {
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryResponse(statusCode = 99, body = "")
        }
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryResponse(statusCode = 600, body = "")
        }
    }

    @Test
    fun `loader fetches and validates repository index`() = runBlocking {
        var requestedUrl: String? = null
        val expected = index()
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { url, _ ->
                requestedUrl = url
                SourceRepositoryResponse(200, SourceRepositoryIndexCodec.encode(expected))
            },
        )

        assertEquals(expected, loader.load("https://example.com/repository.json", clientVersion = 3))
        assertEquals("https://example.com/repository.json", requestedUrl)
    }

    @Test
    fun `loader accepts a UTF-8 BOM before the repository index`() = runBlocking {
        val expected = index()
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ ->
                SourceRepositoryResponse(
                    statusCode = 200,
                    body = "\uFEFF${SourceRepositoryIndexCodec.encode(expected)}",
                )
            },
        )

        assertEquals(expected, loader.load("https://example.com/repository.json", clientVersion = 3))
    }

    @Test
    fun `loader rejects insecure urls without calling transport`() = runBlocking {
        var called = false
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ ->
                called = true
                error("Transport must not be called")
            },
        )

        val error = assertFailsWith<SourceRepositoryLoadException> {
            loader.load("http://example.com/repository.json", clientVersion = 3)
        }

        assertEquals(SourceErrorCode.INVALID_REQUEST, error.code)
        assertEquals(false, called)
    }

    @Test
    fun `loader rejects malformed https urls without calling transport`() = runBlocking {
        var called = false
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ ->
                called = true
                error("Transport must not be called")
            },
        )

        val error = assertFailsWith<SourceRepositoryLoadException> {
            loader.load("https://", clientVersion = 3)
        }

        assertEquals(SourceErrorCode.INVALID_REQUEST, error.code)
        assertEquals(false, called)
    }

    @Test
    fun `loader maps bad status and invalid json`() = runBlocking {
        val statusError = assertFailsWith<SourceRepositoryLoadException> {
            SourceRepositoryLoader(
                transport = SourceRepositoryTransport { _, _ -> SourceRepositoryResponse(503, "busy") },
            ).load("https://example.com/repository.json", clientVersion = 3)
        }
        assertEquals(503, statusError.statusCode)
        assertEquals(SourceErrorKind.UNAVAILABLE, statusError.kind)

        val jsonError = assertFailsWith<SourceRepositoryLoadException> {
            SourceRepositoryLoader(
                transport = SourceRepositoryTransport { _, _ -> SourceRepositoryResponse(200, "{}") },
            ).load("https://example.com/repository.json", clientVersion = 3)
        }
        assertEquals(SourceErrorCode.INVALID_RESPONSE, jsonError.code)
    }

    @Test
    fun `loader preserves internal errors`() = runBlocking {
        val internalError = AssertionError("internal failure")
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ -> throw internalError },
        )

        val error = assertFailsWith<AssertionError> {
            loader.load("https://example.com/repository.json", clientVersion = 3)
        }

        assertEquals(internalError.message, error.message)
    }

    @Test
    fun `loader preserves cancellation`() = runBlocking {
        val cancellation = CancellationException("caller stopped loading")
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ -> throw cancellation },
        )

        val error = assertFailsWith<CancellationException> {
            loader.load("https://example.com/repository.json", clientVersion = 3)
        }

        assertEquals(cancellation.message, error.message)
    }

    @Test
    fun `loader enforces response size`() = runBlocking {
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ -> SourceRepositoryResponse(200, "12345") },
            limits = SourceRepositoryLoadLimits(maxResponseBytes = 4),
        )

        val error = assertFailsWith<SourceRepositoryLoadException> {
            loader.load("https://example.com/repository.json", clientVersion = 3)
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `loader enforces timeout`() = runBlocking {
        val loader = SourceRepositoryLoader(
            transport = SourceRepositoryTransport { _, _ ->
                delay(100)
                SourceRepositoryResponse(200, SourceRepositoryIndexCodec.encode(index()))
            },
            limits = SourceRepositoryLoadLimits(timeoutMillis = 1),
        )

        val error = assertFailsWith<SourceRepositoryLoadException> {
            loader.load("https://example.com/repository.json", clientVersion = 3)
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

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
                runtime = SourceRuntime(id = "wasm", abi = "wasm32-wasi-preview1"),
                entrypoint = "source.wasm",
                packageUrl = "https://example.com/source.zip",
                sha256 = "a".repeat(64),
                artifactSizeBytes = 1024,
                minClientVersion = 1,
            ),
        ),
    )
}
