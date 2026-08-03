package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SourcePackageInstallationCoordinatorTest {
    @Test
    fun `coordinator factory creates a source-specific activation boundary`() {
        var receivedSourceId: SourceId? = null
        val factory = SourcePackageInstallationCoordinatorFactory(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> error("Download must not start") },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { "a".repeat(64) },
                ),
            ),
            extractor = SourcePackageExtractor { _, _, _ -> error("Extraction must not start") },
            packageValidator = SourcePackageValidator(clientVersion = 1),
            activationStoreFactory = SourcePackageActivationStoreFactory { sourceId ->
                receivedSourceId = sourceId
                RecordingStore()
            },
        )

        factory.create(SourceId("external-source"))

        assertEquals(SourceId("external-source"), receivedSourceId)
    }

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

        var initializedCandidate: InstalledSourcePackage? = null
        val state = coordinator.install(
            repositoryManifest = manifest,
            candidate = candidate,
            stagingPath = candidate.packagePath,
            initializeCandidate = { initializedCandidate = it },
        ) { initialized = true }

        val installedCandidate = candidate.copy(artifactSha256 = manifest.sha256)
        assertEquals(false, initialized)
        assertEquals(installedCandidate, initializedCandidate)
        assertEquals(installedCandidate, state.active)
        assertEquals(state, store.state)
    }

    @Test
    fun `manifest overload creates a matching installation candidate`() = runBlocking {
        val manifest = manifest()
        val store = RecordingStore()
        val stagingPath = "staging/source"
        val coordinator = SourcePackageInstallationCoordinator(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(byteArrayOf(1, 2, 3)) },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { manifest.sha256 },
                ),
            ),
            extractor = SourcePackageExtractor { _, path, repositoryManifest ->
                assertEquals(stagingPath, path)
                ExtractedSourcePackage(
                    manifest = repositoryManifest,
                    entries = listOf(
                        SourcePackageEntry("manifest.json", 1),
                        SourcePackageEntry(repositoryManifest.entrypoint, 1),
                    ),
                )
            },
            installer = SourcePackageInstaller(
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationRepository = SourcePackageActivationRepository(manifest.sourceId, store),
            ),
        )

        coordinator.install(manifest, stagingPath) {}

        assertEquals(
            InstalledSourcePackage(
                manifest.sourceId,
                manifest.packageVersion,
                stagingPath,
                artifactSha256 = manifest.sha256,
            ),
            store.state.active,
        )
    }

    @Test
    fun `coordinator rejects an activation path different from staging`() = runBlocking {
        val manifest = manifest()
        val candidate = InstalledSourcePackage(
            sourceId = manifest.sourceId,
            packageVersion = manifest.packageVersion,
            packagePath = "final/package",
        )
        val coordinator = SourcePackageInstallationCoordinator(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> error("Download must not start") },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { manifest.sha256 },
                ),
            ),
            extractor = SourcePackageExtractor { _, _, _ -> error("Extraction must not start") },
            installer = SourcePackageInstaller(
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationRepository = SourcePackageActivationRepository(
                    manifest.sourceId,
                    RecordingStore(),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            coordinator.install(manifest, candidate, "staging/package") {}
        }
    }

    @Test
    fun `coordinator discards extraction when initialization fails`() = runBlocking {
        val manifest = manifest()
        val candidate = InstalledSourcePackage(
            sourceId = manifest.sourceId,
            packageVersion = manifest.packageVersion,
            packagePath = "staging/package",
        )
        var discarded = false
        val coordinator = SourcePackageInstallationCoordinator(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(byteArrayOf(1, 2, 3)) },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { manifest.sha256 },
                ),
            ),
            extractor = SourcePackageExtractor { _, _, _ ->
                ExtractedSourcePackage(
                    manifest = manifest,
                    entries = listOf(
                        SourcePackageEntry("manifest.json", 1),
                        SourcePackageEntry(manifest.entrypoint, 1),
                    ),
                    discard = { discarded = true },
                )
            },
            installer = SourcePackageInstaller(
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationRepository = SourcePackageActivationRepository(manifest.sourceId, RecordingStore()),
            ),
        )

        assertFailsWith<IllegalStateException> {
            coordinator.install(manifest, candidate, candidate.packagePath) {
                error("initialization failed")
            }
        }
        assertTrue(discarded)
    }

    @Test
    fun `cleanup failure does not replace the installation failure`() = runBlocking {
        val manifest = manifest()
        val candidate = InstalledSourcePackage(
            sourceId = manifest.sourceId,
            packageVersion = manifest.packageVersion,
            packagePath = "staging/package",
        )
        val coordinator = SourcePackageInstallationCoordinator(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(byteArrayOf(1, 2, 3)) },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { manifest.sha256 },
                ),
            ),
            extractor = SourcePackageExtractor { _, _, _ ->
                ExtractedSourcePackage(
                    manifest = manifest,
                    entries = listOf(
                        SourcePackageEntry("manifest.json", 1),
                        SourcePackageEntry(manifest.entrypoint, 1),
                    ),
                    discard = { error("cleanup failed") },
                )
            },
            installer = SourcePackageInstaller(
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationRepository = SourcePackageActivationRepository(manifest.sourceId, RecordingStore()),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            coordinator.install(manifest, candidate, candidate.packagePath) {
                error("initialization failed")
            }
        }

        assertEquals("initialization failed", error.message)
    }

    @Test
    fun `coordinator rejects a candidate mismatch before downloading`() = runBlocking {
        val manifest = manifest()
        val candidate = InstalledSourcePackage(
            sourceId = SourceId("another-source"),
            packageVersion = manifest.packageVersion,
            packagePath = "staging/package",
        )
        val coordinator = SourcePackageInstallationCoordinator(
            downloadService = SourcePackageDownloadService(
                transport = SourcePackageTransport { _, _ -> error("Download must not start") },
                artifactVerifier = SourcePackageArtifactVerifier(
                    validator = SourcePackageValidator(clientVersion = 1),
                    sha256 = SourcePackageSha256 { manifest.sha256 },
                ),
            ),
            extractor = SourcePackageExtractor { _, _, _ -> error("Extraction must not start") },
            installer = SourcePackageInstaller(
                packageValidator = SourcePackageValidator(clientVersion = 1),
                layoutValidator = SourcePackageLayoutValidator(),
                activationRepository = SourcePackageActivationRepository(manifest.sourceId, RecordingStore()),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            coordinator.install(manifest, candidate, candidate.packagePath) {}
        }
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
