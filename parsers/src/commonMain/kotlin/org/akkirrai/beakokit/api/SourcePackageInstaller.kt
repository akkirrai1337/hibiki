package org.akkirrai.beakokit.api

/** Coordinates one validated package installation without owning network or filesystem APIs. */
class SourcePackageInstaller(
    private val packageValidator: SourcePackageValidator,
    private val layoutValidator: SourcePackageLayoutValidator,
    private val activationRepository: SourcePackageActivationRepository,
) {
    fun install(
        repositoryManifest: SourceManifest,
        packageManifest: SourceManifest,
        artifact: SourcePackageArtifact,
        entries: List<SourcePackageEntry>,
        candidate: InstalledSourcePackage,
        initializationSucceeded: Boolean,
    ): SourcePackageActivationState {
        require(repositoryManifest == packageManifest) {
            "Package manifest does not match repository manifest"
        }
        require(repositoryManifest.sourceId == candidate.sourceId) {
            "Manifest source ID does not match the installation candidate"
        }
        require(repositoryManifest.packageVersion == candidate.packageVersion) {
            "Manifest package version does not match the installation candidate"
        }
        packageValidator.requireValid(repositoryManifest, artifact)
        layoutValidator.requireValid(packageManifest, entries)
        return activationRepository.activate(candidate, initializationSucceeded)
    }
}
