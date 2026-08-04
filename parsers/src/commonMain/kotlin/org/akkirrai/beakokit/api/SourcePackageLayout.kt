package org.akkirrai.beakokit.api

/** Entry metadata read from an archive without extracting it. */
data class SourcePackageEntry(
    val path: String,
    val sizeBytes: Long,
    val directory: Boolean = false,
    val symbolicLink: Boolean = false,
)

/** Validates archive layout and resource limits before any entry is written to disk. */
class SourcePackageLayoutValidator(
    private val maxEntryCount: Int = DEFAULT_MAX_ENTRY_COUNT,
    private val maxUnpackedSizeBytes: Long = DEFAULT_MAX_UNPACKED_SIZE_BYTES,
) {
    init {
        require(maxEntryCount > 0) { "Maximum entry count must be positive" }
        require(maxUnpackedSizeBytes > 0) { "Maximum unpacked size must be positive" }
    }

    fun violations(
        manifest: SourceManifest,
        entries: List<SourcePackageEntry>,
    ): List<String> = buildList {
        if (entries.size > maxEntryCount) add("Package contains too many entries")

        var unpackedSizeBytes = 0L
        entries.filterNot(SourcePackageEntry::directory).forEach { entry ->
            if (entry.sizeBytes < 0 || entry.sizeBytes > maxUnpackedSizeBytes - unpackedSizeBytes) {
                add("Package unpacked size exceeds the maximum allowed size")
            } else {
                unpackedSizeBytes += entry.sizeBytes
            }
        }

        val paths = mutableSetOf<String>()
        val caseInsensitivePaths = mutableSetOf<String>()
        entries.forEach { entry ->
            val path = normalizedPath(entry.path, entry.directory)
            if (!isSafeRelativePath(entry.path, entry.directory)) {
                add("Unsafe package entry path: ${entry.path}")
            }
            if (!paths.add(path)) add("Duplicate package entry path: ${entry.path}")
            if (!caseInsensitivePaths.add(path.lowercase())) {
                add("Package entry path collides case-insensitively: ${entry.path}")
            }
            if (entry.sizeBytes < 0) add("Package entry size must not be negative: ${entry.path}")
            if (entry.symbolicLink) add("Symbolic links are not allowed: ${entry.path}")
        }

        val filePaths = entries
            .filterNot(SourcePackageEntry::directory)
            .mapTo(mutableSetOf()) { normalizedPath(it.path, directory = false) }
        filePaths.forEach { filePath ->
            if (paths.any { it.startsWith("$filePath/") }) {
                add("Package file path conflicts with a child entry: $filePath")
            }
        }

        if ("manifest.json" !in paths) add("Package must contain manifest.json")
        if (entries.any { it.directory && normalizedPath(it.path, directory = true) == "manifest.json" }) {
            add("Package manifest must be a file: manifest.json")
        }
        if (manifest.entrypoint !in paths) add("Package must contain the manifest entrypoint")
        if (entries.any { it.directory && normalizedPath(it.path, directory = true) == manifest.entrypoint }) {
            add("Manifest entrypoint must be a file: ${manifest.entrypoint}")
        }
    }

    fun requireValid(manifest: SourceManifest, entries: List<SourcePackageEntry>) {
        val violations = violations(manifest, entries)
        if (violations.isNotEmpty()) throw SourcePackageLayoutException(violations)
    }

    companion object {
        const val DEFAULT_MAX_ENTRY_COUNT: Int = 4096
        const val DEFAULT_MAX_UNPACKED_SIZE_BYTES: Long = 256L * 1024L * 1024L

        fun isSafeRelativePath(path: String, directory: Boolean = false): Boolean {
            val normalized = normalizedPath(path, directory)
            if (normalized.isBlank() || normalized.startsWith('/') || normalized.contains('\\')) return false
            if (normalized.any { character -> character.code < 0x20 || character == '\u007f' }) return false
            if (!directory && path.endsWith('/')) return false
            if (normalized.split('/').any { it.isEmpty() || it == "." || it == ".." }) return false
            return true
        }

        private fun normalizedPath(path: String, directory: Boolean): String =
            if (directory && path.endsWith('/')) path.dropLast(1) else path
    }
}

class SourcePackageLayoutException(
    val violations: List<String>,
) : IllegalArgumentException(
    violations.joinToString(prefix = "Invalid source package layout: ", separator = "; "),
)
