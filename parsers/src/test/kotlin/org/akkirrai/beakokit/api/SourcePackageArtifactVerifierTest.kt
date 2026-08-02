package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourcePackageArtifactVerifierTest {
    @Test
    fun `verifier derives metadata and validates the downloaded package`() {
        val bytes = byteArrayOf(1, 2, 3)
        val manifest = manifest(artifactSizeBytes = bytes.size.toLong(), sha256 = "a".repeat(64))
        val verifier = SourcePackageArtifactVerifier(
            validator = SourcePackageValidator(clientVersion = 3),
            sha256 = SourcePackageSha256 { payload ->
                assertEquals(bytes.toList(), payload.toList())
                "a".repeat(64)
            },
        )

        val artifact = verifier.verify(manifest, DownloadedSourcePackage(bytes))

        assertEquals(bytes.size.toLong(), artifact.sizeBytes)
        assertEquals("a".repeat(64), artifact.sha256)
    }

    @Test
    fun `verifier rejects a digest mismatch before installation`() {
        val manifest = manifest(artifactSizeBytes = 3, sha256 = "e".repeat(64))
        val verifier = SourcePackageArtifactVerifier(
            validator = SourcePackageValidator(clientVersion = 3),
            sha256 = SourcePackageSha256 { "a".repeat(64) },
        )

        val error = assertFailsWith<SourcePackageValidationException> {
            verifier.verify(manifest, DownloadedSourcePackage(byteArrayOf(1, 2, 3)))
        }

        assertEquals(
            listOf("Downloaded artifact SHA-256 does not match the manifest"),
            error.violations,
        )
    }

    @Test
    fun `verifier rejects a downloaded size mismatch`() {
        val manifest = manifest(artifactSizeBytes = 4, sha256 = "a".repeat(64))
        val verifier = SourcePackageArtifactVerifier(
            validator = SourcePackageValidator(clientVersion = 3),
            sha256 = SourcePackageSha256 { "a".repeat(64) },
        )

        val error = assertFailsWith<SourcePackageValidationException> {
            verifier.verify(manifest, DownloadedSourcePackage(byteArrayOf(1, 2, 3)))
        }

        assertEquals(
            listOf("Downloaded artifact size does not match the manifest"),
            error.violations,
        )
    }

    private fun manifest(
        artifactSizeBytes: Long,
        sha256: String,
    ) = SourceManifest(
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
        artifactSizeBytes = artifactSizeBytes,
        minClientVersion = 1,
    )
}
