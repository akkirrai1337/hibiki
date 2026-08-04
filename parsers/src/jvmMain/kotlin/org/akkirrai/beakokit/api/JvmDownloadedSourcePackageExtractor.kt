package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipFile

/** Adapts downloaded bytes to the JVM/Android ZIP extractor contract. */
class JvmDownloadedSourcePackageExtractor(
    private val extractor: JvmSourcePackageExtractor = JvmSourcePackageExtractor(),
    private val manifestReader: JvmSourcePackageManifestReader = JvmSourcePackageManifestReader(),
) : SourcePackageExtractor {
    override suspend fun extract(
        downloaded: DownloadedSourcePackage,
        stagingPath: String,
        repositoryManifest: SourceManifest,
    ): ExtractedSourcePackage {
        val stagingDirectory = Path.of(stagingPath)
        val parentDirectory = stagingDirectory.parent ?: Path.of(".")
        requireNoSymbolicLinkInParents(parentDirectory)
        Files.createDirectories(parentDirectory)
        val archive = Files.createTempFile(parentDirectory, "source-package-", ".zip")
        try {
            Files.write(archive, downloaded.bytes)
            extractor.extract(
                archive = archive,
                stagingDirectory = stagingDirectory,
                repositoryManifest = repositoryManifest,
            )
            return ExtractedSourcePackage(
                manifest = manifestReader.read(stagingPath),
                entries = entries(stagingDirectory),
                discard = { deleteRecursively(stagingDirectory) },
            )
        } catch (error: Throwable) {
            deleteRecursively(stagingDirectory)
            throw error
        } finally {
            Files.deleteIfExists(archive)
        }
    }

    private fun entries(stagingDirectory: Path): List<SourcePackageEntry> =
        Files.walk(stagingDirectory).use { paths ->
            paths
                .filter { it != stagingDirectory }
                .map { path ->
                    val relative = stagingDirectory.relativize(path).toString().replace('\\', '/')
                    val directory = Files.isDirectory(path)
                    SourcePackageEntry(
                        path = if (directory) "$relative/" else relative,
                        sizeBytes = if (directory) 0 else Files.size(path),
                        directory = directory,
                        symbolicLink = Files.isSymbolicLink(path),
                    )
                }
                .sorted(Comparator.comparing(SourcePackageEntry::path))
                .toList()
        }

    private fun requireNoSymbolicLinkInParents(directory: Path) {
        var current: Path? = directory
        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                throw SourcePackageStateException(
                    "Source package staging parent must not be a symbolic link: $current",
                )
            }
            current = current.parent
        }
    }

    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
