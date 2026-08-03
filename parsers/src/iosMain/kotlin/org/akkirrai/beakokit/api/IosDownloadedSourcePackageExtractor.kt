package org.akkirrai.beakokit.api

import no.synth.kmpzip.zip.ZipInputStream
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.open
import platform.posix.write

/** Adapts downloaded bytes to the iOS ZIP extraction contract. */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadedSourcePackageExtractor(
    private val manifestReader: IosSourcePackageManifestReader = IosSourcePackageManifestReader(),
    private val layoutValidator: SourcePackageLayoutValidator = SourcePackageLayoutValidator(),
) : SourcePackageExtractor {
    override suspend fun extract(
        downloaded: DownloadedSourcePackage,
        stagingPath: String,
        repositoryManifest: SourceManifest,
    ): ExtractedSourcePackage {
        val files = try {
            readEntries(downloaded.bytes)
        } catch (error: Throwable) {
            throw SourcePackageStateException("Unable to read source package archive", error)
        }
        val entries = files.map { file ->
            SourcePackageEntry(
                path = file.path,
                sizeBytes = file.bytes.size.toLong(),
                directory = file.directory,
            )
        }
        layoutValidator.requireValid(repositoryManifest, entries)

        val fileManager = NSFileManager.defaultManager
        check(!fileManager.fileExistsAtPath(stagingPath)) {
            "Source package staging directory must not already exist: $stagingPath"
        }
        try {
            ensureDirectory(fileManager, stagingPath, "source package staging directory")
            files.filterNot(StoredEntry::directory).forEach { file ->
                val path = "$stagingPath/${file.path}"
                val parent = path.substringBeforeLast('/', stagingPath)
                ensureDirectory(fileManager, parent, "source package entry directory")
                writeFile(path, file.bytes)
            }
            return ExtractedSourcePackage(
                manifest = manifestReader.read(stagingPath),
                entries = entries,
                discard = { fileManager.removeItemAtPath(stagingPath, error = null) },
            )
        } catch (error: Throwable) {
            fileManager.removeItemAtPath(stagingPath, error = null)
            throw error
        }
    }

    private fun writeFile(path: String, bytes: ByteArray) {
        val descriptor = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1A4)
        require(descriptor >= 0) { "Unable to open source package entry: $path" }
        try {
            var offset = 0
            while (offset < bytes.size) {
                val written = bytes.usePinned { pinned ->
                    write(descriptor, pinned.addressOf(offset), (bytes.size - offset).toULong())
                }
                require(written > 0) { "Unable to write source package entry: $path" }
                offset += written.toInt()
            }
        } finally {
            close(descriptor)
        }
    }

    private fun ensureDirectory(fileManager: NSFileManager, path: String, label: String) {
        if (fileManager.fileExistsAtPath(path)) return
        require(fileManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )) { "Unable to create $label" }
    }

    private fun readEntries(bytes: ByteArray): List<StoredEntry> {
        val stored = mutableListOf<StoredEntry>()
        var unpackedSize = 0L
        ZipInputStream(bytes).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(stored.size < MAX_ENTRY_COUNT) { "Package contains too many entries" }
                val directory = entry.name.endsWith('/')
                val entryBytes = if (directory) {
                    ByteArray(0)
                } else {
                    readEntry(zip) { bytesRead ->
                        unpackedSize += bytesRead.toLong()
                        require(unpackedSize <= MAX_UNPACKED_SIZE_BYTES) {
                            "Package unpacked size exceeds the maximum allowed size"
                        }
                    }
                }
                stored += StoredEntry(entry.name, entryBytes, directory)
                zip.closeEntry()
            }
        }
        return stored
    }

    private fun readEntry(
        zip: ZipInputStream,
        onChunkRead: (Int) -> Unit,
    ): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = zip.read(buffer, 0, buffer.size)
            if (read <= 0) break
            onChunkRead(read)
            chunks += buffer.copyOf(read)
            total += read
        }
        return ByteArray(total).also { result ->
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(result, destinationOffset = offset)
                offset += chunk.size
            }
        }
    }

    private data class StoredEntry(
        val path: String,
        val bytes: ByteArray,
        val directory: Boolean,
    )

    private companion object {
        const val MAX_ENTRY_COUNT = SourcePackageLayoutValidator.DEFAULT_MAX_ENTRY_COUNT
        const val MAX_UNPACKED_SIZE_BYTES = SourcePackageLayoutValidator.DEFAULT_MAX_UNPACKED_SIZE_BYTES
    }
}
