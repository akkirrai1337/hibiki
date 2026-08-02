package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Calculates package metadata from the actual archive bytes. */
class JvmSourcePackageArtifactReader {
    fun read(archive: Path): SourcePackageArtifact {
        require(Files.isRegularFile(archive)) { "Source package archive does not exist: $archive" }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(archive).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return SourcePackageArtifact(
            sizeBytes = Files.size(archive),
            sha256 = digest.digest().toHexString(),
        )
    }

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        this@toHexString.forEach { byte ->
            append((byte.toInt() and 0xff).toString(16).padStart(2, '0'))
        }
    }
}
