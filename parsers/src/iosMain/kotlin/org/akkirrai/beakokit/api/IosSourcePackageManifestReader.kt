package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.posix.memcpy

/** Reads the manifest from an installed package directory on iOS. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosSourcePackageManifestReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxManifestBytes: Long = SourcePackageManifestReader.DEFAULT_MAX_MANIFEST_BYTES,
) : SourcePackageManifestReader {
    init {
        require(maxManifestBytes > 0) { "Maximum manifest size must be positive" }
    }

    override fun read(packagePath: String): SourceManifest {
        val manifestPath = packagePath.trimEnd('/') + "/manifest.json"
        if (!NSFileManager.defaultManager.fileExistsAtPath(manifestPath)) {
            throw SourcePackageStateException(
                "Installed source package manifest is missing: $manifestPath",
            )
        }
        val fileSize = (NSFileManager.defaultManager
            .attributesOfItemAtPath(manifestPath, error = null)
            ?.get(NSFileSize) as? NSNumber)?.longLongValue
        if (fileSize != null && fileSize > maxManifestBytes) {
            throw SourcePackageStateException(
                "Installed source package manifest exceeds $maxManifestBytes bytes: $manifestPath",
            )
        }
        val data = NSFileManager.defaultManager.contentsAtPath(manifestPath)
            ?: throw SourcePackageStateException(
                "Installed source package manifest is unreadable: $manifestPath",
            )
        if (data.length > maxManifestBytes.toULong()) {
            throw SourcePackageStateException(
                "Installed source package manifest exceeds $maxManifestBytes bytes: $manifestPath",
            )
        }
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
