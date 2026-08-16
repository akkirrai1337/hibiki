package org.akkirrai.beakokit.api

/** Platform-owned activation store factory used for one source at a time. */
fun interface SourcePackageActivationStoreFactory {
    fun create(sourceId: SourceId): SourcePackageActivationStore
}

/** Creates source-specific installation coordinators without sharing activation state. */
class SourcePackageInstallationCoordinatorFactory(
    private val downloadService: SourcePackageDownloadService,
    private val extractor: SourcePackageExtractor,
    private val packageValidator: SourcePackageValidator,
    private val layoutValidator: SourcePackageLayoutValidator = SourcePackageLayoutValidator(),
    private val activationStoreFactory: SourcePackageActivationStoreFactory,
) {
    fun create(sourceId: SourceId): SourcePackageInstallationCoordinator =
        SourcePackageInstallationCoordinator(
            downloadService = downloadService,
            extractor = extractor,
            installer = SourcePackageInstaller(
                packageValidator = packageValidator,
                layoutValidator = layoutValidator,
                activationRepository = SourcePackageActivationRepository(
                    sourceId = sourceId,
                    store = activationStoreFactory.create(sourceId),
                ),
            ),
        )
}
