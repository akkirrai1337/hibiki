package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Extracts a validated source archive into a new staging directory. */
class JvmSourcePackageExtractor(
    private val layoutValidator: SourcePackageLayoutValidator = SourcePackageLayoutValidator(),
    private val maxUnpackedSizeBytes: Long = SourcePackageLayoutValidator.DEFAULT_MAX_UNPACKED_SIZE_BYTES,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    init {
        require(maxUnpackedSizeBytes > 0) { "Maximum unpacked size must be positive" }
    }

    fun extract(
        archive: Path,
        stagingDirectory: Path,
        repositoryManifest: SourceManifest,
    ): Path {
        require(Files.isRegularFile(archive)) { "Source package archive does not exist: $archive" }
        require(!Files.exists(stagingDirectory)) {
            "Staging directory must not already exist: $stagingDirectory"
        }

        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries().asSequence().map { entry ->
                SourcePackageEntry(
                    path = entry.name,
                    sizeBytes = entry.size,
                    directory = entry.isDirectory,
                )
            }.toList()
            if (entries.any { it.sizeBytes < 0 }) {
                throw SourcePackageExtractionException("ZIP entry size is unavailable")
            }
            layoutValidator.requireValid(repositoryManifest, entries)
            val packageManifest = readPackageManifest(zip)
            require(repositoryManifest.matchesPackageManifest(packageManifest)) {
                "Package manifest does not match repository manifest"
            }

            Files.createDirectory(stagingDirectory)
            var extractedSizeBytes = 0L
            try {
                zip.entries().asSequence().forEach { entry ->
                    val relativePath = normalizedPath(entry.name, entry.isDirectory)
                    val target = stagingDirectory.resolve(relativePath).normalize()
                    require(target.startsWith(stagingDirectory)) {
                        "Unsafe resolved package path: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        zip.getInputStream(entry).use { input ->
                            val copiedSizeBytes = Files.newOutputStream(target).use { output ->
                                copyEntry(
                                    input = input,
                                    output = output,
                                    expectedSizeBytes = entry.size,
                                    remainingPackageBytes = maxUnpackedSizeBytes - extractedSizeBytes,
                                )
                            }
                            extractedSizeBytes += copiedSizeBytes
                        }
                    }
                }
            } catch (error: Throwable) {
                deleteRecursively(stagingDirectory)
                throw error
            }
        }
        return stagingDirectory
    }

    private fun normalizedPath(path: String, directory: Boolean): String =
        if (directory && path.endsWith('/')) path.dropLast(1) else path

    private fun readPackageManifest(zip: ZipFile): SourceManifest {
        val entry = zip.getEntry("manifest.json")
            ?: throw SourcePackageExtractionException("Package must contain manifest.json")
        val maxManifestSizeBytes = SourcePackageManifestReader.DEFAULT_MAX_MANIFEST_BYTES
        if (entry.size > maxManifestSizeBytes) {
            throw SourcePackageExtractionException("Package manifest exceeds the maximum allowed size")
        }
        val bytes = zip.getInputStream(entry).use { input ->
            input.readNBytes((maxManifestSizeBytes + 1).toInt())
        }
        if (bytes.size.toLong() > maxManifestSizeBytes) {
            throw SourcePackageExtractionException("Package manifest exceeds the maximum allowed size")
        }
        return try {
            json.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (error: Exception) {
            throw SourcePackageExtractionException("Package manifest is invalid: ${error.message}")
        }
    }

    private fun copyEntry(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        expectedSizeBytes: Long,
        remainingPackageBytes: Long,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copiedSizeBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count.toLong() > expectedSizeBytes - copiedSizeBytes) {
                throw SourcePackageExtractionException("ZIP entry size changed while extracting")
            }
            if (count.toLong() > remainingPackageBytes - copiedSizeBytes) {
                throw SourcePackageExtractionException("Extracted package exceeds the maximum allowed size")
            }
            output.write(buffer, 0, count)
            copiedSizeBytes += count
        }
        if (copiedSizeBytes != expectedSizeBytes) {
            throw SourcePackageExtractionException("ZIP entry size changed while extracting")
        }
        return copiedSizeBytes
    }

    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

}

class SourcePackageExtractionException(message: String) : IllegalArgumentException(message)
