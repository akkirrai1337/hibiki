package org.akkirrai.beakokit.api

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUUID

/** Owns the only filesystem subtree in which iOS source packages may live. */
@OptIn(ExperimentalForeignApi::class)
class IosSourcePackageStorage(
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
    private val rootPath: String = NSHomeDirectory() + "/Library/Application Support/beakokit/source-packages",
) {
    init {
        ensureDirectory(rootPath, "iOS source package root")
    }

    fun newStagingPath(sourceId: SourceId): String {
        val sourceRoot = "$rootPath/${sourceId.value}"
        ensureDirectory(sourceRoot, "iOS source package directory")
        return "$sourceRoot/package-${NSUUID().UUIDString}"
    }

    fun requireManagedPackagePath(path: String): String {
        val normalized = path.trimEnd('/')
        require(normalized.startsWith("$rootPath/")) {
            "iOS source package must be inside the managed package root"
        }
        require(!normalized.removePrefix("$rootPath/").split('/').any { it.isBlank() || it == "." || it == ".." }) {
            "iOS source package path is invalid"
        }
        requireNoSymbolicLinks(normalized, "iOS source package")
        return normalized
    }

    fun ensurePackageDirectory(path: String, label: String) {
        requireManagedPackagePath(path)
        ensureDirectory(path, label)
    }

    fun requireNoSymbolicLinks(path: String, label: String) {
        val normalized = requireManagedPackagePathPrefix(path)
        var current = rootPath
        checkNotSymbolicLink(current, label)
        normalized.removePrefix("$rootPath/").split('/').forEach { component ->
            current += "/$component"
            checkNotSymbolicLink(current, label)
        }
    }

    fun removePackage(path: String) {
        val normalized = requireManagedPackagePath(path)
        if (fileManager.fileExistsAtPath(normalized)) {
            fileManager.removeItemAtPath(normalized, error = null)
        }
    }

    /** Removes abandoned staging directories that are not referenced by activation state. */
    fun removeUnreferencedPackages(referencedPaths: Set<String>) {
        val referenced = referencedPaths.map(::requireManagedPackagePath).toSet()
        fileManager.contentsOfDirectoryAtPath(rootPath, error = null)
            .orEmpty()
            .filterIsInstance<String>()
            .forEach { sourceDirectory ->
                val sourceRoot = "$rootPath/$sourceDirectory"
                if (sourceDirectory.isBlank() || sourceDirectory.contains('/') || !fileManager.fileExistsAtPath(sourceRoot)) {
                    return@forEach
                }
                requireNoSymbolicLinks(sourceRoot, "iOS source package directory")
                fileManager.contentsOfDirectoryAtPath(sourceRoot, error = null)
                    .orEmpty()
                    .filterIsInstance<String>()
                    .filter { name -> name.startsWith("package-") && '/' !in name }
                    .map { name -> "$sourceRoot/$name" }
                    .filterNot(referenced::contains)
                    .forEach { stalePath -> runCatching { removePackage(stalePath) } }
            }
    }

    private fun ensureDirectory(path: String, label: String) {
        if (path != rootPath) requireManagedPackagePath(path)
        checkNotSymbolicLink(path, label)
        if (fileManager.fileExistsAtPath(path)) return
        require(fileManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )) { "Unable to create $label" }
        checkNotSymbolicLink(path, label)
    }

    private fun requireManagedPackagePathPrefix(path: String): String {
        val normalized = path.trimEnd('/')
        require(normalized == rootPath || normalized.startsWith("$rootPath/")) {
            "iOS source package must be inside the managed package root"
        }
        return normalized
    }

    private fun checkNotSymbolicLink(path: String, label: String) {
        require(fileManager.destinationOfSymbolicLinkAtPath(path, error = null) == null) {
            "$label must not be a symbolic link: $path"
        }
    }
}
