package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException

class SourcePackageDownloaderTest {
    @Test
    fun `Ktor transport downloads successful package bytes`() = runBlocking {
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine { respond(byteArrayOf(1, 2, 3)) }),
        )

        val packageFile = transport.download(
            url = "https://example.com/source.zip",
            limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 10),
        )

        assertContentEquals(byteArrayOf(1, 2, 3), packageFile.bytes)
        assertEquals(3, packageFile.sizeBytes)
    }

    @Test
    fun `Ktor transport rejects non successful responses`() = runBlocking {
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine { respond("missing", HttpStatusCode.NotFound) }),
        )

        val error = assertFailsWith<SourcePackageDownloadException> {
            transport.download(
                url = "https://example.com/source.zip",
                limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 10),
            )
        }

        assertEquals(404, error.statusCode)
        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `Ktor transport rejects insecure urls before making a request`() = runBlocking {
        var called = false
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine {
                called = true
                respond("unexpected")
            }),
        )

        val error = assertFailsWith<SourcePackageDownloadException> {
            transport.download(
                url = "http://example.com/source.zip",
                limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 10),
            )
        }

        assertEquals(SourceErrorCode.INVALID_REQUEST, error.code)
        assertEquals(false, called)
    }

    @Test
    fun `Ktor transport rejects malformed https urls before making a request`() = runBlocking {
        var called = false
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine {
                called = true
                respond("unexpected")
            }),
        )

        val error = assertFailsWith<SourcePackageDownloadException> {
            transport.download(
                url = "https:///source.zip",
                limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 10),
            )
        }

        assertEquals(SourceErrorCode.INVALID_REQUEST, error.code)
        assertEquals(false, called)
    }

    @Test
    fun `Ktor transport rejects an oversized package`() = runBlocking {
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine { respond(byteArrayOf(1, 2, 3, 4, 5)) }),
        )

        val error = assertFailsWith<SourcePackageDownloadException> {
            transport.download(
                url = "https://example.com/source.zip",
                limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 4),
            )
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `Ktor transport maps network failures`() = runBlocking {
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine { throw IOException("offline") }),
        )

        val error = assertFailsWith<SourcePackageDownloadException> {
            transport.download(
                url = "https://example.com/source.zip",
                limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 10),
            )
        }

        assertEquals(SourceErrorKind.NETWORK, error.kind)
        assertEquals("offline", error.cause?.message)
    }

    @Test
    fun `Ktor transport enforces timeout`() = runBlocking {
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine {
                delay(100)
                respond(byteArrayOf(1, 2, 3))
            }),
        )

        val error = assertFailsWith<SourcePackageDownloadException> {
            transport.download(
                url = "https://example.com/source.zip",
                limits = SourcePackageDownloadLimits(timeoutMillis = 1, maxArtifactSizeBytes = 10),
            )
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `Ktor transport preserves cancellation`() = runBlocking {
        val cancellation = CancellationException("caller stopped downloading")
        val transport = KtorSourcePackageTransport(
            HttpClient(MockEngine { throw cancellation }),
        )

        val error = assertFailsWith<CancellationException> {
            transport.download(
                url = "https://example.com/source.zip",
                limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 10),
            )
        }

        assertEquals(cancellation.message, error.message)
    }
}
