package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reads the manifest from an installed package directory on JVM/Android. */
class JvmSourcePackageManifestReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SourcePackageManifestReader {
    override fun read(packagePath: String): SourceManifest {
        val directory = Path.of(packagePath)
        val manifestFile = directory.resolve(MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestFile)) {
            throw SourcePackageStateException(
                "Installed source package manifest is missing: $manifestFile",
            )
        }
        return try {
            json.decodeFromString(Files.readString(manifestFile))
        } catch (error: Exception) {
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
