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
        buildList {
            if (!repositoryManifest.matchesPackageManifest(packageManifest)) {
                add("Package manifest does not match repository manifest")
            }
            if (repositoryManifest.sourceId != candidate.sourceId) {
                add("Manifest source ID does not match the installation candidate")
            }
            if (repositoryManifest.packageVersion != candidate.packageVersion) {
                add("Manifest package version does not match the installation candidate")
            }
            if (candidate.artifactSha256 != null && candidate.artifactSha256 != artifact.sha256) {
                add("Installation candidate checksum does not match the downloaded artifact")
            }
        }.takeIf { it.isNotEmpty() }?.let(::SourcePackageValidationException)?.let { throw it }
        packageValidator.requireValid(repositoryManifest, artifact)
        layoutValidator.requireValid(packageManifest, entries)
    }
}
