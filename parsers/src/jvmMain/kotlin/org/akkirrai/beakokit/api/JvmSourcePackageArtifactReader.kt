package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path

/** Calculates package metadata from the actual archive bytes. */
class JvmSourcePackageArtifactReader {
    fun read(archive: Path): SourcePackageArtifact {
        require(Files.isRegularFile(archive)) { "Source package archive does not exist: $archive" }
        val sha256 = Files.newInputStream(archive).use(JvmSourcePackageSha256::digest)
        return SourcePackageArtifact(
            sizeBytes = Files.size(archive),
            sha256 = sha256,
        )
    }
}
