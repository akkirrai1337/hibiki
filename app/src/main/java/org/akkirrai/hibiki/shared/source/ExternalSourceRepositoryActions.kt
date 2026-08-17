package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourcePackageInstallStage
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint

/** Narrow repository-management boundary for shared settings UI. */
interface ExternalSourceRepositoryActions {
    suspend fun repositories(): List<SourceRepositoryEndpoint>

    suspend fun addRepositoryFromUi(endpoint: SourceRepositoryEndpoint)

    suspend fun removeRepositoryFromUi(url: String)

    suspend fun refreshRepositories()

    suspend fun refreshRepository(url: String) {
        refreshRepositories()
    }

    suspend fun packageStatusesForUi(): List<ExternalSourcePackageStatus>

    suspend fun repositoryContentsForUi(): List<ExternalSourceRepositoryContent>

    /** The host supplies initialization because it owns the platform runtime setup. */
    suspend fun installAvailablePackageFromUi(
        sourceId: SourceId,
        onStage: (SourcePackageInstallStage) -> Unit = {},
        initialize: suspend () -> Unit,
    )

    suspend fun rollbackPackageFromUi(sourceId: SourceId)

    suspend fun uninstallPackageFromUi(sourceId: SourceId)
}

data class ExternalSourcePackageStatus(
    val sourceId: SourceId,
    val availableManifest: SourceManifest,
    val activePackage: ActiveExternalSourcePackage?,
    val rollbackAvailable: Boolean = false,
) {
    init {
        require(sourceId == availableManifest.sourceId) {
            "Package status source ID does not match its manifest"
        }
    }

    val updateAvailable: Boolean
        get() = activePackage != null && (
            activePackage.manifest.packageVersion != availableManifest.packageVersion ||
                activePackage.installed.artifactSha256 == null ||
                activePackage.installed.artifactSha256 != availableManifest.sha256
            )
}

data class ExternalSourceRepositoryContent(
    val endpoint: SourceRepositoryEndpoint,
    val packages: List<ExternalSourcePackageStatus> = emptyList(),
    val error: Throwable? = null,
)
