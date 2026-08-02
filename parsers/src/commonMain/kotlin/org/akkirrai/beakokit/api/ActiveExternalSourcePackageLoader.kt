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
        val manifest = manifestReader.read(installed.packagePath)
        return try {
            ActiveExternalSourcePackage(manifest = manifest, installed = installed)
        } catch (error: IllegalArgumentException) {
            throw SourcePackageStateException(
                message = "Active source package manifest does not match persisted state",
                cause = error,
            )
        }
    }
}
