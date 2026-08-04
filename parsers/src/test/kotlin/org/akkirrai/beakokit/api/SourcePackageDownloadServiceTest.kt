package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class SourcePackageDownloadServiceTest {
    @Test
    fun `service verifies downloaded bytes before returning them`() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)
        val manifest = manifest(size = bytes.size.toLong(), sha256 = "a".repeat(64))
        val service = SourcePackageDownloadService(
            transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(bytes) },
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = SourcePackageValidator(clientVersion = 3),
                sha256 = SourcePackageSha256 { "a".repeat(64) },
            ),
        )

        val verified = service.download(manifest)

        assertEquals(bytes.size.toLong(), verified.artifact.sizeBytes)
        assertEquals("a".repeat(64), verified.artifact.sha256)
    }

    @Test
    fun `service never returns a package with an invalid checksum`() = runBlocking {
        val service = SourcePackageDownloadService(
            transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(byteArrayOf(1, 2, 3)) },
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = SourcePackageValidator(clientVersion = 3),
                sha256 = SourcePackageSha256 { "b".repeat(64) },
            ),
        )

        assertFailsWith<SourcePackageValidationException> {
            service.download(manifest(size = 3, sha256 = "a".repeat(64)))
        }
    }

    @Test
    fun `service rejects transport output above the configured limit`() = runBlocking {
        val manifest = manifest(size = 4, sha256 = "a".repeat(64))
        val service = SourcePackageDownloadService(
            transport = SourcePackageTransport { _, _ ->
                DownloadedSourcePackage(byteArrayOf(1, 2, 3, 4))
            },
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = SourcePackageValidator(clientVersion = 3, maxArtifactSizeBytes = 4),
                sha256 = SourcePackageSha256 { manifest.sha256 },
            ),
            limits = SourcePackageDownloadLimits(maxArtifactSizeBytes = 3),
        )

        assertFailsWith<IllegalArgumentException> { service.download(manifest) }
    }

    @Test
    fun `service rejects an incompatible manifest before transport`() = runBlocking {
        var transportCalled = false
        val service = SourcePackageDownloadService(
            transport = SourcePackageTransport { _, _ ->
                transportCalled = true
                error("Transport must not be called")
            },
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = SourcePackageValidator(clientVersion = 3),
                sha256 = SourcePackageSha256 { "a".repeat(64) },
            ),
        )

        assertFailsWith<SourcePackageValidationException> {
            service.download(manifest(size = 3, sha256 = "a".repeat(64)).copy(minClientVersion = 4))
        }
        assertEquals(false, transportCalled)
    }

    private fun manifest(size: Long, sha256: String) = SourceManifest(
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
        sha256 = sha256,
        artifactSizeBytes = size,
        minClientVersion = 1,
    )
}
