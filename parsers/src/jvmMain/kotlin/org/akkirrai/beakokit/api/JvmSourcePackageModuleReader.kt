package org.akkirrai.beakokit.api

import java.nio.file.Files
import java.nio.file.Path

/** Reads a validated runtime module from an installed JVM/Android package directory. */
class JvmSourcePackageModuleReader(
    private val maxModuleBytes: Long = SourcePackageModuleReader.DEFAULT_MAX_MODULE_BYTES,
) : SourcePackageModuleReader {
    init {
        require(maxModuleBytes > 0) { "Maximum module size must be positive" }
    }

    override fun read(packagePath: String, entrypoint: String): ByteArray {
        require(SourcePackageLayoutValidator.isSafeRelativePath(entrypoint)) {
            "Unsafe source package entrypoint: $entrypoint"
        }
        val root = Path.of(packagePath).toAbsolutePath().normalize()
        val module = root.resolve(entrypoint).normalize()
        require(module.startsWith(root)) { "Source package entrypoint escapes package directory" }
        val realRoot = try {
            root.toRealPath()
        } catch (error: Exception) {
            throw SourcePackageStateException("Installed source package directory is unreadable", error)
        }
        if (Files.isSymbolicLink(module)) {
            throw SourcePackageStateException(
                "Source package entrypoint must not be a symbolic link: $entrypoint",
            )
        }
        if (!Files.isRegularFile(module)) {
            throw SourcePackageStateException("Source package entrypoint does not exist: $entrypoint")
        }
        val realModule = try {
            module.toRealPath()
        } catch (error: Exception) {
            throw SourcePackageStateException("Source package entrypoint is unreadable: $entrypoint", error)
        }
        if (!realModule.startsWith(realRoot)) {
            throw SourcePackageStateException("Source package entrypoint escapes package directory")
        }
        val size = Files.size(realModule)
        if (size > maxModuleBytes) {
            throw SourcePackageStateException("Source package module exceeds the maximum allowed size")
        }
        return try {
            Files.readAllBytes(realModule).also { bytes ->
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
        const val DEFAULT_MAX_MODULE_BYTES: Long = SourcePackageModuleReader.DEFAULT_MAX_MODULE_BYTES
    }
}
