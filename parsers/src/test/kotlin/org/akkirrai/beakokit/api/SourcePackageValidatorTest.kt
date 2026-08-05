package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SourcePackageValidatorTest {
    @Test
    fun `custom runtime policy can accept a platform specific runtime`() {
        val manifest = manifest().copy(runtime = SourceRuntime("custom-runtime", "custom-abi"))
        val validator = SourcePackageValidator(
            clientVersion = 3,
            runtimeSupportPolicy = SourceRuntimeSupportPolicy { runtime ->
                runtime == manifest.runtime
            },
        )

        validator.requireValid(
            manifest,
            SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
        )
    }

    @Test
    fun `unsupported runtime is rejected before package activation`() {
        val manifest = manifest().copy(runtime = SourceRuntime("unsupported-runtime", "unknown-abi"))

        assertContains(
            SourcePackageValidator(clientVersion = 3).violations(
                manifest,
                SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
            ),
            "Unsupported source runtime: unsupported-runtime/unknown-abi",
        )
    }

    @Test
    fun `matching artifact metadata is accepted`() {
        val manifest = manifest()
        SourcePackageValidator(clientVersion = 3).requireValid(
            manifest = manifest,
            artifact = SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
        )
    }

    @Test
    fun `mismatched artifact metadata is rejected before installation`() {
        val manifest = manifest()
        val artifact = SourcePackageArtifact(
            sizeBytes = manifest.artifactSizeBytes + 1,
            sha256 = "b".repeat(64),
        )

        val violations = SourcePackageValidator(clientVersion = 3).violations(manifest, artifact)

        assertContains(violations, "Downloaded artifact size does not match the manifest")
        assertContains(violations, "Downloaded artifact SHA-256 does not match the manifest")
        assertFailsWith<SourcePackageValidationException> {
            SourcePackageValidator(clientVersion = 3).requireValid(manifest, artifact)
        }
    }

    @Test
    fun `oversized artifact is rejected`() {
        val manifest = manifest(sizeBytes = 128)
        val validator = SourcePackageValidator(clientVersion = 3, maxArtifactSizeBytes = 64)

        assertContains(
            validator.violations(
                manifest,
                SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
            ),
            "Downloaded artifact exceeds the maximum allowed size",
        )
    }

    @Test
    fun `untrusted repository requires an accepted signature`() {
        val manifest = manifest(signature = "signature")
        val artifact = SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256)

        assertContains(
            SourcePackageValidator(clientVersion = 3).violations(manifest, artifact),
            "Package signature is not trusted",
        )
        SourcePackageValidator(
            clientVersion = 3,
            trustPolicy = SourcePackageTrustPolicy.untrusted(SourcePackageSignatureVerifier { _, _ -> true }),
        ).requireValid(manifest, artifact)
        assertContains(
            SourcePackageValidator(
                clientVersion = 3,
                trustPolicy = SourcePackageTrustPolicy.untrusted(SourcePackageSignatureVerifier { _, _ -> true }),
            ).violations(manifest(signature = null), artifact),
            "Package signature is required for this repository",
        )
    }

    @Test
    fun `signature verifier failure is treated as an untrusted package`() {
        val manifest = manifest(signature = "signature")
        val validator = SourcePackageValidator(
            clientVersion = 3,
            trustPolicy = SourcePackageTrustPolicy.untrusted(
                SourcePackageSignatureVerifier { _, _ -> error("verifier unavailable") },
            ),
        )

        assertContains(
            validator.violations(
                manifest,
                SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
            ),
            "Package signature is not trusted",
        )
    }

    private fun manifest(
        sizeBytes: Long = 1024,
        signature: String? = null,
    ) = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime(id = "wasm", abi = "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = sizeBytes,
        minClientVersion = 1,
        signature = signature,
    )
}
