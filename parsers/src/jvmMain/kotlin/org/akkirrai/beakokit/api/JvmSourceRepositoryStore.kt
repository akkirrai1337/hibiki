package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** JVM/Android-compatible atomic file store for configured repository endpoints. */
class JvmSourceRepositoryStore(
    private val file: Path,
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val maxRepositoryBytes: Long = DEFAULT_MAX_REPOSITORY_BYTES,
) : SourceRepositoryStore {
    init {
        require(maxRepositoryBytes > 0) { "Maximum repository state size must be positive" }
        require(maxRepositoryBytes < Int.MAX_VALUE) {
            "Maximum repository state size must fit in a platform byte array"
        }
    }

    override fun load(): List<SourceRepositoryEndpoint> {
        read(file)?.let { return checked(it) }
        read(backupFile())?.let { backup ->
            persistAtomically(backup)
            return checked(backup)
        }
        if (!Files.exists(file)) return emptyList()
        throw SourceRepositoryStateException("Source repository list is corrupted: $file")
    }

    override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
        val checkedRepositories = checked(repositories)
        file.parent?.let(Files::createDirectories)
        read(file)?.let { writeAtomically(backupFile(), it) }
        writeAtomically(file, checkedRepositories)
    }

    private fun backupFile(): Path = file.resolveSibling("${file.fileName}.bak")

    private fun read(path: Path): List<SourceRepositoryEndpoint>? = try {
        if (Files.isSymbolicLink(path)) {
            throw SourceRepositoryStateException("Source repository state must not be a symbolic link: $path")
        }
        if (Files.exists(path)) {
            if (Files.size(path) > maxRepositoryBytes) {
                throw SourceRepositoryStateException(
                    "Source repository state exceeds $maxRepositoryBytes bytes: $path",
                )
            }
            val bytes = Files.newInputStream(path).use { input ->
                input.readNBytes((maxRepositoryBytes + 1).toInt())
            }
            if (bytes.size.toLong() > maxRepositoryBytes) {
                throw SourceRepositoryStateException(
                    "Source repository state exceeds $maxRepositoryBytes bytes: $path",
                )
            }
            json.decodeFromString(bytes.decodeToString())
        } else {
            null
        }
    } catch (error: SourceRepositoryStateException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun writeAtomically(path: Path, repositories: List<SourceRepositoryEndpoint>) {
        val parent = path.parent ?: error("Repository store file must have a parent directory")
        val bytes = json.encodeToString(repositories).encodeToByteArray()
        require(bytes.size.toLong() <= maxRepositoryBytes) {
            "Source repository state exceeds $maxRepositoryBytes bytes"
        }
        Files.createDirectories(parent)
        val temporaryFile = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
        try {
            FileChannel.open(temporaryFile, StandardOpenOption.WRITE).use { channel ->
                channel.write(ByteBuffer.wrap(bytes))
                channel.force(true)
            }
            try {
                Files.move(
                    temporaryFile,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    private fun checked(repositories: List<SourceRepositoryEndpoint>): List<SourceRepositoryEndpoint> {
        val urls = mutableSetOf<String>()
        repositories.forEach { repository ->
            require(urls.add(repository.url)) {
                "Duplicate repository URL: ${repository.url}"
            }
        }
        return repositories.toList()
    }

    private companion object {
        const val DEFAULT_MAX_REPOSITORY_BYTES: Long = 2L * 1024L * 1024L
    }
}
