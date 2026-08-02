package org.akkirrai.beakokit.api

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.posix.memcpy

/** Reads a validated runtime module from an installed iOS package directory. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosSourcePackageModuleReader(
    private val maxModuleBytes: Long = DEFAULT_MAX_MODULE_BYTES,
) {
    init {
        require(maxModuleBytes > 0) { "Maximum module size must be positive" }
    }

    fun read(packagePath: String, entrypoint: String): ByteArray {
        require(SourcePackageLayoutValidator.isSafeRelativePath(entrypoint)) {
            "Unsafe source package entrypoint: $entrypoint"
        }
        val modulePath = packagePath.trimEnd('/') + "/" + entrypoint
        if (!NSFileManager.defaultManager.fileExistsAtPath(modulePath)) {
            throw SourcePackageStateException("Source package entrypoint does not exist: $entrypoint")
        }
        val fileSize = (NSFileManager.defaultManager
            .attributesOfItemAtPath(modulePath, error = null)
            ?.get(NSFileSize) as? NSNumber)?.longLongValue
        if (fileSize != null && fileSize > maxModuleBytes) {
            throw SourcePackageStateException("Source package module exceeds the maximum allowed size")
        }
        val data = NSFileManager.defaultManager.contentsAtPath(modulePath)
            ?: throw SourcePackageStateException("Source package entrypoint is unreadable: $entrypoint")
        if (data.length.toLong() > maxModuleBytes) {
            throw SourcePackageStateException("Source package module exceeds the maximum allowed size")
        }
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return bytes
    }

    companion object {
        const val DEFAULT_MAX_MODULE_BYTES: Long = 16L * 1024L * 1024L
    }
}
