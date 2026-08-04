package org.akkirrai.beakokit.api

/** Coordinates one validated package installation without owning network or filesystem APIs. */
class SourcePackageInstaller(
    private val packageValidator: SourcePackageValidator,
    private val layoutValidator: SourcePackageLayoutValidator,
    private val activationRepository: SourcePackageActivationRepository,
) {
    suspend fun installAfterInitialization(
        repositoryManifest: SourceManifest,
        packageManifest: SourceManifest,
        artifact: SourcePackageArtifact,
        entries: List<SourcePackageEntry>,
        candidate: InstalledSourcePackage,
        initialize: suspend () -> Unit,
    ): SourcePackageActivationState {
        validate(
            repositoryManifest = repositoryManifest,
            packageManifest = packageManifest,
            artifact = artifact,
            entries = entries,
            candidate = candidate,
        )
        initialize()
        return activationRepository.activate(candidate, initializationSucceeded = true)
    }

    fun install(
        repositoryManifest: SourceManifest,
        packageManifest: SourceManifest,
        artifact: SourcePackageArtifact,
        entries: List<SourcePackageEntry>,
        candidate: InstalledSourcePackage,
        initializationSucceeded: Boolean,
    ): SourcePackageActivationState {
        validate(
            repositoryManifest = repositoryManifest,
            packageManifest = packageManifest,
            artifact = artifact,
            entries = entries,
            candidate = candidate,
        )
        return activationRepository.activate(candidate, initializationSucceeded)
    }

    private fun validate(
        repositoryManifest: SourceManifest,
        packageManifest: SourceManifest,
        artifact: SourcePackageArtifact,
        entries: List<SourcePackageEntry>,
        candidate: InstalledSourcePackage,
    ) {
        require(repositoryManifest.matchesPackageManifest(packageManifest)) {
            "Package manifest does not match repository manifest"
        }
        require(repositoryManifest.sourceId == candidate.sourceId) {
            "Manifest source ID does not match the installation candidate"
        }
        require(repositoryManifest.packageVersion == candidate.packageVersion) {
            "Manifest package version does not match the installation candidate"
        }
        require(candidate.artifactSha256 == null || candidate.artifactSha256 == artifact.sha256) {
            "Installation candidate checksum does not match the downloaded artifact"
        }
        packageValidator.requireValid(repositoryManifest, artifact)
        layoutValidator.requireValid(packageManifest, entries)
    }
}
