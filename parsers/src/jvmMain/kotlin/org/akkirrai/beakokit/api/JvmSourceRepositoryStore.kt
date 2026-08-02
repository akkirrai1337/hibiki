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
) : SourceRepositoryStore {
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
        if (Files.exists(path)) json.decodeFromString(Files.readString(path)) else null
    } catch (_: Exception) {
        null
    }

    private fun writeAtomically(path: Path, repositories: List<SourceRepositoryEndpoint>) {
        val parent = path.parent ?: error("Repository store file must have a parent directory")
        Files.createDirectories(parent)
        val temporaryFile = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
        try {
            val bytes = json.encodeToString(repositories).encodeToByteArray()
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
}
