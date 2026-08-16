package org.akkirrai.beakokit.api

/** Result of platform-specific extraction into a staging package directory. */
data class ExtractedSourcePackage(
    val manifest: SourceManifest,
    val entries: List<SourcePackageEntry>,
    val discard: () -> Unit = {},
)

/** Extracts a verified archive without exposing filesystem APIs to common code. */
fun interface SourcePackageExtractor {
    suspend fun extract(
        downloaded: DownloadedSourcePackage,
        stagingPath: String,
        repositoryManifest: SourceManifest,
    ): ExtractedSourcePackage
}

/** Connects download, verification, platform extraction, initialization and activation. */
class SourcePackageInstallationCoordinator(
    private val downloadService: SourcePackageDownloadService,
    private val extractor: SourcePackageExtractor,
    private val installer: SourcePackageInstaller,
) {
    /** Installs the manifest version into the supplied staging path. */
    suspend fun install(
        repositoryManifest: SourceManifest,
        stagingPath: String,
        initializeCandidate: (suspend (InstalledSourcePackage) -> Unit)? = null,
        onStage: (SourcePackageInstallStage) -> Unit = {},
        initialize: suspend () -> Unit,
    ): SourcePackageActivationState = install(
        repositoryManifest = repositoryManifest,
        candidate = InstalledSourcePackage(
            sourceId = repositoryManifest.sourceId,
            packageVersion = repositoryManifest.packageVersion,
            packagePath = stagingPath,
        ),
        stagingPath = stagingPath,
        initialize = initialize,
        initializeCandidate = initializeCandidate,
        onStage = onStage,
    )

    suspend fun install(
        repositoryManifest: SourceManifest,
        candidate: InstalledSourcePackage,
        stagingPath: String,
        initializeCandidate: (suspend (InstalledSourcePackage) -> Unit)? = null,
        onStage: (SourcePackageInstallStage) -> Unit = {},
        initialize: suspend () -> Unit,
    ): SourcePackageActivationState {
        buildList {
            if (candidate.sourceId != repositoryManifest.sourceId) {
                add("Installation candidate source ID does not match repository manifest")
            }
            if (candidate.packageVersion != repositoryManifest.packageVersion) {
                add("Installation candidate version does not match repository manifest")
            }
            if (candidate.packagePath != stagingPath) {
                add("Installation candidate path must match the extraction staging path")
            }
        }.takeIf { it.isNotEmpty() }?.let(::SourcePackageValidationException)?.let { throw it }
        onStage(SourcePackageInstallStage.DOWNLOADING)
        val verified = downloadService.download(repositoryManifest) { stage ->
            if (stage == SourcePackageInstallStage.VERIFYING_ARTIFACT) onStage(stage)
        }
        val installedCandidate = candidate.copy(artifactSha256 = verified.artifact.sha256)
        onStage(SourcePackageInstallStage.EXTRACTING)
        val extracted = extractor.extract(
            downloaded = verified.downloaded,
            stagingPath = stagingPath,
            repositoryManifest = repositoryManifest,
        )
        return try {
            onStage(SourcePackageInstallStage.VALIDATING_PACKAGE)
            installer.installAfterInitialization(
                repositoryManifest = repositoryManifest,
                packageManifest = extracted.manifest,
                artifact = verified.artifact,
                entries = extracted.entries,
                candidate = installedCandidate,
                initialize = { approvedCandidate ->
                    onStage(SourcePackageInstallStage.INITIALIZING_RUNTIME)
                    initializeCandidate?.invoke(approvedCandidate) ?: initialize()
                },
                beforeActivation = { onStage(SourcePackageInstallStage.ACTIVATING) },
            )
        } catch (error: Throwable) {
            runCatching { extracted.discard() }
            throw error
        }
    }
}
