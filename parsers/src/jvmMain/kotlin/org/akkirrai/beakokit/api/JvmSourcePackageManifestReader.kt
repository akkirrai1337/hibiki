package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reads the manifest from an installed package directory on JVM/Android. */
class JvmSourcePackageManifestReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxManifestBytes: Long = SourcePackageManifestReader.DEFAULT_MAX_MANIFEST_BYTES,
) : SourcePackageManifestReader {
    init {
        require(maxManifestBytes > 0) { "Maximum manifest size must be positive" }
        require(maxManifestBytes < Int.MAX_VALUE) {
            "Maximum manifest size must fit in a platform byte array"
        }
    }

    override fun read(packagePath: String): SourceManifest {
        val directory = Path.of(packagePath)
        val manifestFile = directory.resolve(MANIFEST_FILE_NAME)
        if (Files.isSymbolicLink(manifestFile)) {
            throw SourcePackageStateException(
                "Installed source package manifest must not be a symbolic link: $manifestFile",
            )
        }
        if (!Files.isRegularFile(manifestFile)) {
            throw SourcePackageStateException(
                "Installed source package manifest is missing: $manifestFile",
            )
        }
        if (Files.size(manifestFile) > maxManifestBytes) {
            throw SourcePackageStateException(
                "Installed source package manifest exceeds $maxManifestBytes bytes: $manifestFile",
            )
        }
        return try {
            val bytes = Files.newInputStream(manifestFile).use { input ->
                input.readNBytes((maxManifestBytes + 1).toInt())
            }
            if (bytes.size.toLong() > maxManifestBytes) {
                throw SourcePackageStateException(
                    "Installed source package manifest exceeds $maxManifestBytes bytes: $manifestFile",
                )
            }
            json.decodeFromString(bytes.decodeToString())
        } catch (error: Exception) {
            if (error is SourcePackageStateException) throw error
            throw SourcePackageStateException(
                "Installed source package manifest is invalid: $manifestFile",
                error,
            )
        }
    }

    private companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
    }
}
