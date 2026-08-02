package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path

/** Reads a validated runtime module from an installed JVM/Android package directory. */
class JvmSourcePackageModuleReader(
    private val maxModuleBytes: Long = DEFAULT_MAX_MODULE_BYTES,
) {
    init {
        require(maxModuleBytes > 0) { "Maximum module size must be positive" }
    }

    fun read(packagePath: String, entrypoint: String): ByteArray {
        require(SourcePackageLayoutValidator.isSafeRelativePath(entrypoint)) {
            "Unsafe source package entrypoint: $entrypoint"
        }
        val root = Path.of(packagePath).toAbsolutePath().normalize()
        val module = root.resolve(entrypoint).normalize()
        require(module.startsWith(root)) { "Source package entrypoint escapes package directory" }
        if (!Files.isRegularFile(module)) {
            throw SourcePackageStateException("Source package entrypoint does not exist: $entrypoint")
        }
        val size = Files.size(module)
        if (size > maxModuleBytes) {
            throw SourcePackageStateException("Source package module exceeds the maximum allowed size")
        }
        return try {
            Files.readAllBytes(module).also { bytes ->
                if (bytes.size.toLong() > maxModuleBytes) {
                    throw SourcePackageStateException("Source package module exceeds the maximum allowed size")
                }
            }
        } catch (error: SourcePackageStateException) {
            throw error
        } catch (error: Exception) {
            throw SourcePackageStateException("Unable to read source package entrypoint", error)
        }
    }

    companion object {
        const val DEFAULT_MAX_MODULE_BYTES: Long = 16L * 1024L * 1024L
    }
}
