package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class SourcePackageInstallerTest {
    @Test
    fun `successful initialization callback activates the package`() = runBlocking {
        val store = RecordingStore()
        val manifest = manifest()
        var initialized = false

        val state = installer(store).installAfterInitialization(
            repositoryManifest = manifest,
            packageManifest = manifest,
            artifact = SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
            entries = entries(manifest),
            candidate = candidate(),
            initialize = suspend { initialized = true },
        )

        assertEquals(true, initialized)
        assertEquals(SourcePackageActivationState(active = candidate()), state)
        assertEquals(1, store.persistCount)
    }

    @Test
    fun `initialization exception prevents activation`() = runBlocking {
        val store = RecordingStore().apply { state = SourcePackageActivationState(active = oldCandidate()) }
        val manifest = manifest()
        val failure = IllegalStateException("runtime failed")

        val thrown = assertFailsWith<IllegalStateException> {
            installer(store).installAfterInitialization(
                repositoryManifest = manifest,
                packageManifest = manifest,
                artifact = SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
                entries = entries(manifest),
                candidate = candidate(),
                initialize = suspend { throw failure },
            )
        }

        assertEquals(failure, thrown)
        assertEquals(SourcePackageActivationState(active = oldCandidate()), store.state)
        assertEquals(0, store.persistCount)
    }

    @Test
    fun `validated package is activated after successful initialization`() {
        val store = RecordingStore()
        val manifest = manifest()
        val candidate = candidate()
        val installer = installer(store)

        val state = installer.install(
            repositoryManifest = manifest,
            packageManifest = manifest,
            artifact = SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
            entries = entries(manifest),
            candidate = candidate,
            initializationSucceeded = true,
        )

        assertEquals(SourcePackageActivationState(active = candidate), state)
        assertEquals(state, store.state)
    }

    @Test
    fun `invalid artifact never reaches activation`() {
        val store = RecordingStore()
        val manifest = manifest()

        assertFailsWith<SourcePackageValidationException> {
            installer(store).install(
                repositoryManifest = manifest,
                packageManifest = manifest,
                artifact = SourcePackageArtifact(manifest.artifactSizeBytes + 1, manifest.sha256),
                entries = entries(manifest),
                candidate = candidate(),
                initializationSucceeded = true,
            )
        }

        assertEquals(0, store.persistCount)
    }

    @Test
    fun `package manifest mismatch never reaches activation`() {
        val store = RecordingStore()
        val repositoryManifest = manifest()
        val packageManifest = repositoryManifest.copy(packageVersion = "3.0.0")

        assertFailsWith<IllegalArgumentException> {
            installer(store).install(
                repositoryManifest = repositoryManifest,
                packageManifest = packageManifest,
                artifact = SourcePackageArtifact(
                    repositoryManifest.artifactSizeBytes,
                    repositoryManifest.sha256,
                ),
                entries = entries(repositoryManifest),
                candidate = candidate(),
                initializationSucceeded = true,
            )
        }

        assertEquals(0, store.persistCount)
    }

    @Test
    fun `failed initialization leaves previous package active`() {
        val store = RecordingStore().apply { state = SourcePackageActivationState(active = oldCandidate()) }
        val manifest = manifest()

        installer(store).install(
            repositoryManifest = manifest,
            packageManifest = manifest,
            artifact = SourcePackageArtifact(manifest.artifactSizeBytes, manifest.sha256),
            entries = entries(manifest),
            candidate = candidate(),
            initializationSucceeded = false,
        )

        assertEquals(SourcePackageActivationState(active = oldCandidate()), store.state)
        assertEquals(0, store.persistCount)
    }

    private fun installer(store: RecordingStore) = SourcePackageInstaller(
        packageValidator = SourcePackageValidator(clientVersion = 3),
        layoutValidator = SourcePackageLayoutValidator(),
        activationRepository = SourcePackageActivationRepository(SourceId("external-source"), store),
    )

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "2.0.0",
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1024,
        minClientVersion = 1,
    )

    private fun candidate() = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = "2.0.0",
        packagePath = "packages/external-source/2.0.0",
    )

    private fun oldCandidate() = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        packagePath = "packages/external-source/1.0.0",
    )

    private fun entries(manifest: SourceManifest) = listOf(
        SourcePackageEntry("manifest.json", 100),
        SourcePackageEntry(manifest.entrypoint, 924),
    )

    private class RecordingStore : SourcePackageActivationStore {
        var state = SourcePackageActivationState()
        var persistCount = 0

        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) {
            this.state = state
            persistCount++
        }
    }

    @Test
    fun `package manifest may carry its own archive metadata`() {
        val repositoryManifest = manifest()
        val packageManifest = repositoryManifest.copy(
            sha256 = "b".repeat(64),
            artifactSizeBytes = repositoryManifest.artifactSizeBytes + 1,
        )
        val store = RecordingStore()

        installer(store).install(
            repositoryManifest = repositoryManifest,
            packageManifest = packageManifest,
            artifact = SourcePackageArtifact(repositoryManifest.artifactSizeBytes, repositoryManifest.sha256),
            entries = entries(repositoryManifest),
            candidate = candidate(),
            initializationSucceeded = true,
        )

        assertEquals(candidate(), store.state.active)
    }
}
