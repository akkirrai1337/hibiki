package org.akkirrai.beakokit.api

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SourcePackageEndToEndTest {
    @Test
    fun `verified zip is extracted and activated only after initialization`() = runBlocking {
        val packageManifest = manifest()
        val archive = zipOf(
            "manifest.json" to Json.encodeToString(packageManifest).encodeToByteArray(),
            packageManifest.entrypoint to byteArrayOf(0, 97, 115, 109),
        )
        val repositoryManifest = packageManifest.copy(
            sha256 = JvmSourcePackageSha256.digest(archive),
            artifactSizeBytes = archive.size.toLong(),
        )
        val root = Files.createTempDirectory("beakokit-source-package-")
        val staging = root.resolve("staging").toString()
        val store = RecordingStore()
        val coordinator = coordinator(archive, repositoryManifest.sourceId, store)

        try {
            val active = coordinator.install(repositoryManifest, staging) {
                assertTrue(Files.exists(Path.of(staging, "manifest.json")))
            }

            assertEquals(
                InstalledSourcePackage(repositoryManifest.sourceId, repositoryManifest.packageVersion, staging),
                active.active,
            )
            assertEquals(active, store.state)
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `corrupted zip is rejected without activation or staging residue`() = runBlocking {
        val archive = byteArrayOf(1, 2, 3, 4)
        val repositoryManifest = manifest().copy(
            sha256 = JvmSourcePackageSha256.digest(archive),
            artifactSizeBytes = archive.size.toLong(),
        )
        val root = Files.createTempDirectory("beakokit-corrupt-package-")
        val staging = root.resolve("staging").toString()
        val store = RecordingStore()
        val coordinator = coordinator(archive, repositoryManifest.sourceId, store)

        try {
            assertFailsWith<Throwable> {
                coordinator.install(repositoryManifest, staging) {}
            }
            assertEquals(SourcePackageActivationState(), store.state)
            assertTrue(!Files.exists(Path.of(staging)))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `checksum mismatch is rejected before extraction`() = runBlocking {
        val archive = byteArrayOf(1, 2, 3, 4)
        val repositoryManifest = manifest().copy(
            sha256 = "f".repeat(64),
            artifactSizeBytes = archive.size.toLong(),
        )
        val root = Files.createTempDirectory("beakokit-mismatched-package-")
        val staging = root.resolve("staging").toString()
        val store = RecordingStore()
        val coordinator = coordinator(archive, repositoryManifest.sourceId, store)

        try {
            assertFailsWith<SourcePackageValidationException> {
                coordinator.install(repositoryManifest, staging) {}
            }
            assertEquals(SourcePackageActivationState(), store.state)
            assertTrue(!Files.exists(Path.of(staging)))
        } finally {
            deleteRecursively(root)
        }
    }

    private fun coordinator(
        archive: ByteArray,
        sourceId: SourceId,
        store: RecordingStore,
    ) = SourcePackageInstallationCoordinator(
        downloadService = SourcePackageDownloadService(
            transport = SourcePackageTransport { _, _ -> DownloadedSourcePackage(archive) },
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = SourcePackageValidator(clientVersion = 1),
                sha256 = JvmSourcePackageSha256,
            ),
        ),
        extractor = JvmDownloadedSourcePackageExtractor(),
        installer = SourcePackageInstaller(
            packageValidator = SourcePackageValidator(clientVersion = 1),
            layoutValidator = SourcePackageLayoutValidator(),
            activationRepository = SourcePackageActivationRepository(sourceId, store),
        ),
    )

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("fixture-source"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "Fixture source",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
        ),
        apiVersion = SourceApi.VERSION,
        hostApiVersion = SourceHostApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.test/fixture-source.zip",
        sha256 = "0".repeat(64),
        artifactSizeBytes = 1,
        minClientVersion = 1,
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private class RecordingStore : SourcePackageActivationStore {
        var state = SourcePackageActivationState()

        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) {
            this.state = state
        }
    }
}
