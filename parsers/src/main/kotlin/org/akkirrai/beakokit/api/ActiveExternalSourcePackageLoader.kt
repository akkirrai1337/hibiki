package org.akkirrai.beakokit.api

/** Platform-owned reader for the manifest inside an installed source package. */
fun interface SourcePackageManifestReader {
    fun read(packagePath: String): SourceManifest

    companion object {
        const val DEFAULT_MAX_MANIFEST_BYTES: Long = 256L * 1024L
    }
}

/** Reconstructs the active runtime package without consulting a remote repository. */
class ActiveExternalSourcePackageLoader(
    private val activationRepository: SourcePackageActivationRepository,
    private val manifestReader: SourcePackageManifestReader,
) {
    fun load(): ActiveExternalSourcePackage? {
        val installed = activationRepository.load().active ?: return null
        return load(installed)
    }

    /** Reconstructs a specific persisted package version without mutating activation state. */
    fun load(installed: InstalledSourcePackage): ActiveExternalSourcePackage {
        return try {
            val manifest = manifestReader.read(installed.packagePath)
            if (!SourcePackageLayoutValidator.isSafeRelativePath(manifest.entrypoint) ||
                manifest.entrypoint == "manifest.json"
            ) {
                throw SourcePackageStateException(
                    "Active source package manifest contains an unsafe entrypoint",
                )
            }
            ActiveExternalSourcePackage(manifest = manifest, installed = installed)
        } catch (error: SourcePackageStateException) {
            throw error
        } catch (error: Exception) {
            throw SourcePackageStateException(
                message = "Active source package manifest does not match persisted state",
                cause = error,
            )
        }
    }
}
