package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmSourcePackageExtractorTest {
    @Test
    fun `valid archive is extracted to staging`() {
        val root = Files.createTempDirectory("hibiki-source-extract-")
        try {
            val manifest = manifest()
            val archive = writeArchive(root.resolve("source.zip"), mapOf(
                "manifest.json" to Json.encodeToString(manifest),
                "source.wasm" to "payload",
                "assets/" to "",
            ))
            val staging = root.resolve("staging")

            JvmSourcePackageExtractor().extract(archive, staging, manifest)

            assertTrue(Files.exists(staging.resolve("manifest.json")))
            assertEquals("payload", Files.readString(staging.resolve("source.wasm")))
            assertTrue(Files.isDirectory(staging.resolve("assets")))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `unsafe archive is rejected before staging is created`() {
        val root = Files.createTempDirectory("hibiki-source-extract-")
        try {
            val archive = writeArchive(root.resolve("source.zip"), mapOf(
                "manifest.json" to "{}",
                "../outside.txt" to "bad",
                "source.wasm" to "payload",
            ))
            val staging = root.resolve("staging")

            assertFailsWith<SourcePackageLayoutException> {
                JvmSourcePackageExtractor().extract(archive, staging, manifest())
            }
            assertFalse(Files.exists(staging))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `archive manifest must match the repository manifest`() {
        val root = Files.createTempDirectory("hibiki-source-extract-")
        try {
            val repositoryManifest = manifest()
            val archiveManifest = repositoryManifest.copy(packageVersion = "2.0.0")
            val archive = writeArchive(root.resolve("source.zip"), mapOf(
                "manifest.json" to Json.encodeToString(archiveManifest),
                "source.wasm" to "payload",
            ))
            val staging = root.resolve("staging")

            assertFailsWith<IllegalArgumentException> {
                JvmSourcePackageExtractor().extract(archive, staging, repositoryManifest)
            }
            assertFalse(Files.exists(staging))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `staging parent symbolic links are rejected before extraction`() {
        val root = Files.createTempDirectory("hibiki-source-extract-")
        val target = Files.createTempDirectory("hibiki-source-target-")
        val link = root.resolve("link")
        val created = runCatching { Files.createSymbolicLink(link, target) }.getOrNull() ?: return
        try {
            val manifest = manifest()
            val archive = writeArchive(root.resolve("source.zip"), mapOf(
                "manifest.json" to Json.encodeToString(manifest),
                "source.wasm" to "payload",
            ))

            assertFailsWith<SourcePackageStateException> {
                JvmSourcePackageExtractor().extract(archive, link.resolve("staging"), manifest)
            }
            assertFalse(Files.exists(target.resolve("staging")))
        } finally {
            Files.deleteIfExists(created)
            deleteRecursively(target)
            deleteRecursively(root)
        }
    }

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1024,
        minClientVersion = 1,
    )

    private fun writeArchive(path: java.nio.file.Path, files: Map<String, String>): java.nio.file.Path {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith('/')) zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return path
    }

    private fun deleteRecursively(path: java.nio.file.Path) {
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
