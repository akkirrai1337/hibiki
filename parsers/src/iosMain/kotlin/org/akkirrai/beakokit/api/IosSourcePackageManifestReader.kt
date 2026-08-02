package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/** Reads the manifest from an installed package directory on iOS. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosSourcePackageManifestReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SourcePackageManifestReader {
    override fun read(packagePath: String): SourceManifest {
        val manifestPath = packagePath.trimEnd('/') + "/manifest.json"
        if (!NSFileManager.defaultManager.fileExistsAtPath(manifestPath)) {
            throw SourcePackageStateException(
                "Installed source package manifest is missing: $manifestPath",
            )
        }
        val data = NSFileManager.defaultManager.contentsAtPath(manifestPath)
            ?: throw SourcePackageStateException(
                "Installed source package manifest is unreadable: $manifestPath",
            )
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return try {
            json.decodeFromString(bytes.decodeToString())
        } catch (error: Exception) {
            throw SourcePackageStateException(
                "Installed source package manifest is invalid: $manifestPath",
                error,
            )
        }
    }
}
