package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class SourcePackageInstallationCoordinatorTest {
    @Test
    fun `coordinator activates only after verified extraction and initialization`() = runBlocking {
        val manifest = manifest()
        val candidate = InstalledSourcePackage(
            sourceId = manifest.sourceId,
            packageVersion = manifest.packageVersion,
            packagePath = "staging/external-source/1.0.0",
        )
        val store = RecordingStore()
        var initialized = false
        val coordinator = SourcePackageInstallationCoordinator(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(byteArrayOf(1, 2, 3)) },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { manifest.sha256 },
                ),
            ),
            extractor = SourcePackageExtractor { _, stagingPath, extractedRepositoryManifest ->
                assertEquals(candidate.packagePath, stagingPath)
                assertEquals(manifest, extractedRepositoryManifest)
                ExtractedSourcePackage(
                    manifest = manifest,
                    entries = listOf(
                        SourcePackageEntry("manifest.json", 1),
                        SourcePackageEntry(manifest.entrypoint, 1),
                    ),
                )
            },
            installer = SourcePackageInstaller(
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationRepository = SourcePackageActivationRepository(manifest.sourceId, store),
            ),
        )

        val state = coordinator.install(manifest, candidate, candidate.packagePath) { initialized = true }

        assertEquals(true, initialized)
        assertEquals(candidate, state.active)
        assertEquals(state, store.state)
    }

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "External source",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
        ),
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 3,
        minClientVersion = 1,
    )

    private class RecordingStore : SourcePackageActivationStore {
        var state = SourcePackageActivationState()

        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) {
            this.state = state
        }
    }
}
