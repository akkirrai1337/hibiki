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
    suspend fun install(
        repositoryManifest: SourceManifest,
        candidate: InstalledSourcePackage,
        stagingPath: String,
        initialize: suspend () -> Unit,
    ): SourcePackageActivationState {
        require(candidate.sourceId == repositoryManifest.sourceId) {
            "Installation candidate source ID does not match repository manifest"
        }
        require(candidate.packageVersion == repositoryManifest.packageVersion) {
            "Installation candidate version does not match repository manifest"
        }
        require(candidate.packagePath == stagingPath) {
            "Installation candidate path must match the extraction staging path"
        }
        val verified = downloadService.download(repositoryManifest)
        val extracted = extractor.extract(
            downloaded = verified.downloaded,
            stagingPath = stagingPath,
            repositoryManifest = repositoryManifest,
        )
        return try {
            installer.installAfterInitialization(
                repositoryManifest = repositoryManifest,
                packageManifest = extracted.manifest,
                artifact = verified.artifact,
                entries = extracted.entries,
                candidate = candidate,
                initialize = initialize,
            )
        } catch (error: Throwable) {
            runCatching { extracted.discard() }
            throw error
        }
    }
}
