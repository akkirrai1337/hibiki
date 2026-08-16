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
        initialize: suspend (InstalledSourcePackage) -> Unit,
        beforeActivation: () -> Unit = {},
    ): SourcePackageActivationState {
        val approvedCandidate = candidateWithApprovedRequirements(repositoryManifest, candidate)
        validate(
            repositoryManifest = repositoryManifest,
            packageManifest = packageManifest,
            artifact = artifact,
            entries = entries,
            candidate = approvedCandidate,
        )
        initialize(approvedCandidate)
        beforeActivation()
        return activationRepository.activate(approvedCandidate, initializationSucceeded = true)
    }

    fun install(
        repositoryManifest: SourceManifest,
        packageManifest: SourceManifest,
        artifact: SourcePackageArtifact,
        entries: List<SourcePackageEntry>,
        candidate: InstalledSourcePackage,
        initializationSucceeded: Boolean,
    ): SourcePackageActivationState {
        val approvedCandidate = candidateWithApprovedRequirements(repositoryManifest, candidate)
        validate(
            repositoryManifest = repositoryManifest,
            packageManifest = packageManifest,
            artifact = artifact,
            entries = entries,
            candidate = approvedCandidate,
        )
        return activationRepository.activate(approvedCandidate, initializationSucceeded)
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

    private fun candidateWithApprovedRequirements(
        repositoryManifest: SourceManifest,
        candidate: InstalledSourcePackage,
    ): InstalledSourcePackage {
        val requested = repositoryManifest.hostRequirements()
        val granted = activationRepository.load().active?.approvedHostRequirements
        if (granted != null && !requested.isWithin(granted)) {
            throw SourcePackageValidationException(
                listOf("Package requests capabilities or network domains that were not previously approved"),
            )
        }
        return candidate.copy(
            approvedHostRequirements = requested.takeUnless {
                it.capabilities.isEmpty() && it.networkPolicy.allowedHosts.isEmpty()
            },
        )
    }
}
