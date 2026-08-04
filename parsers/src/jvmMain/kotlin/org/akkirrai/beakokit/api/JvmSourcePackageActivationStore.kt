package org.akkirrai.beakokit.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption

/** JVM/Android-compatible file store for source activation state. */
class JvmSourcePackageActivationStore(
    private val rootDirectory: Path,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
    private val maxStateBytes: Long = DEFAULT_MAX_STATE_BYTES,
) : SourcePackageActivationStore {
    init {
        require(maxStateBytes > 0) { "Maximum activation state size must be positive" }
        require(maxStateBytes < Int.MAX_VALUE) {
            "Maximum activation state size must fit in a platform byte array"
        }
    }

    override fun load(sourceId: SourceId): SourcePackageActivationState {
        val stateFile = stateFile(sourceId)
        readState(stateFile)?.let { return it }
        readState(backupFile(sourceId))?.let { backup ->
            writeStateAtomically(stateFile, backup)
            return backup
        }
        if (!Files.exists(stateFile)) return SourcePackageActivationState()
        throw SourcePackageStateException("Source package activation state is corrupted: $stateFile")
    }

    override fun persistAtomically(
        sourceId: SourceId,
        state: SourcePackageActivationState,
    ) {
        Files.createDirectories(rootDirectory)
        val stateFile = stateFile(sourceId)
        val previous = readState(stateFile)
        if (previous != null) {
            writeStateAtomically(backupFile(sourceId), previous)
        }
        writeStateAtomically(stateFile, state)
    }

    private fun stateFile(sourceId: SourceId): Path = rootDirectory.resolve("${sourceId.value}.json")

    private fun backupFile(sourceId: SourceId): Path = rootDirectory.resolve("${sourceId.value}.json.bak")

    private fun readState(path: Path): SourcePackageActivationState? = try {
        if (Files.isSymbolicLink(path)) {
            throw SourcePackageStateException("Source package activation state must not be a symbolic link: $path")
        }
        if (Files.exists(path)) {
            if (Files.size(path) > maxStateBytes) {
                throw SourcePackageStateException(
                    "Source package activation state exceeds $maxStateBytes bytes: $path",
                )
            }
            val bytes = Files.newInputStream(path).use { input ->
                input.readNBytes((maxStateBytes + 1).toInt())
            }
            if (bytes.size.toLong() > maxStateBytes) {
                throw SourcePackageStateException(
                    "Source package activation state exceeds $maxStateBytes bytes: $path",
                )
            }
            json.decodeFromString(bytes.decodeToString())
        } else {
            null
        }
    } catch (error: SourcePackageStateException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun writeStateAtomically(path: Path, state: SourcePackageActivationState) {
        val temporaryFile = Files.createTempFile(rootDirectory, path.fileName.toString(), ".tmp")
        try {
            val bytes = json.encodeToString(state).encodeToByteArray()
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

    private companion object {
        const val DEFAULT_MAX_STATE_BYTES: Long = 2L * 1024L * 1024L
    }
}
