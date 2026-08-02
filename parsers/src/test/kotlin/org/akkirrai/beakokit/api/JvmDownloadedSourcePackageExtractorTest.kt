package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking

class JvmDownloadedSourcePackageExtractorTest {
    @Test
    fun `adapter extracts downloaded archive with metadata`() = runBlocking {
        val manifest = manifest()
        val archive = zipOf(
            "manifest.json" to Json.encodeToString(manifest).encodeToByteArray(),
            "source.wasm" to byteArrayOf(0, 1, 2),
        )
        val staging = Files.createTempDirectory("hibiki-source-staging-").resolve("package")

        val extracted = JvmDownloadedSourcePackageExtractor().extract(
            downloaded = DownloadedSourcePackage(archive),
            stagingPath = staging.toString(),
            repositoryManifest = manifest,
        )

        assertEquals(manifest, extracted.manifest)
        assertEquals(listOf("manifest.json", "source.wasm"), extracted.entries.map { it.path })
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
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
        artifactSizeBytes = 1,
        minClientVersion = 1,
    )
}
