package org.akkirrai.hibiki.core.source.extension.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.InstalledSourcePackage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourcePackageInstallStage
import org.akkirrai.beakokit.api.SourcePackageStateException
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRuntime
import org.akkirrai.hibiki.core.source.extension.DiscoveredSourceExtension
import org.akkirrai.hibiki.core.source.extension.PackageManagerSourceDiscovery
import org.akkirrai.hibiki.shared.source.ExternalSourcePackageStatus
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryActions
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryContent
import java.io.File
import java.security.MessageDigest

/**
 * [ExternalSourceRepositoryActions] backed by Hibiki's fixed, curated
 * `hibiki-sources` APK repository index instead of the WASM package pipeline.
 * Only one, non-removable repository is exposed; installs/uninstalls hand off
 * to the system [android.content.pm.PackageInstaller] and complete
 * asynchronously once the user confirms the system dialog, so the UI reflects
 * the outcome on the next refresh rather than immediately.
 */
class ApkSourceRepositoryActions(
    private val androidContext: Context,
    private val repositoryClient: SourceRepositoryClient,
    private val installer: SourceExtensionInstaller,
    // Repository index entries carry no icon/website metadata -- this reuses the same probe
    // PackageManagerSourceCatalog already did to build the browsing catalog, instead of loading
    // every extension's class a second time just to read its website/iconUrl. A supplier (not a
    // plain map) so it always reflects the latest installed-extension revision.
    private val sourceInfoByPackageName: () -> Map<String, SourceInfo>,
) : ExternalSourceRepositoryActions {

    private val endpoint = SourceRepositoryEndpoint(SOURCE_REPOSITORY_INDEX_URL)

    private fun probedSourceInfo(packageName: String): SourceInfo? = sourceInfoByPackageName()[packageName]

    @Volatile
    private var cachedEntries: List<SourceRepositoryEntry> = emptyList()

    @Volatile
    private var lastRefreshError: Throwable? = null

    override suspend fun repositories(): List<SourceRepositoryEndpoint> = listOf(endpoint)

    override suspend fun addRepositoryFromUi(endpoint: SourceRepositoryEndpoint) {
        throw SourcePackageStateException("Custom repositories are not supported yet")
    }

    override suspend fun removeRepositoryFromUi(url: String) {
        throw SourcePackageStateException("Custom repositories are not supported yet")
    }

    override suspend fun refreshRepositories() {
        repositoryClient.fetchIndex().fold(
            onSuccess = { index ->
                cachedEntries = index.sources
                lastRefreshError = null
            },
            onFailure = { throwable ->
                lastRefreshError = throwable
                throw throwable
            },
        )
    }

    override suspend fun refreshRepository(url: String) = refreshRepositories()

    override suspend fun packageStatusesForUi(): List<ExternalSourcePackageStatus> {
        val repositoryStatuses = cachedEntries.map(::statusFor)
        val repositoryPackageNames = cachedEntries.map { it.packageName }.toSet()
        // Extensions installed by dropping an APK on the device directly (not published in the
        // hibiki-sources repository index yet) are already discoverable via PackageManager for
        // catalog/search purposes -- surface them here too so they show up as installed instead
        // of only appearing once someone adds them to the repository index.
        val localOnlyStatuses = PackageManagerSourceDiscovery.discover(androidContext)
            .filter { it.packageName !in repositoryPackageNames }
            .mapNotNull { extension -> runCatching { statusForLocalOnly(extension) }.getOrNull() }
        return repositoryStatuses + localOnlyStatuses
    }

    private fun statusForLocalOnly(extension: DiscoveredSourceExtension): ExternalSourcePackageStatus? {
        val packageManager = androidContext.packageManager
        val info = runCatching { packageManager.getPackageInfo(extension.packageName, 0) }.getOrNull() ?: return null
        val installedVersion = info.versionName?.takeIf(String::isNotBlank) ?: return null
        val displayName = info.applicationInfo
            ?.let { runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull() }
            ?.takeIf(String::isNotBlank)
            ?: extension.packageName
        // InstalledSourcePackage.artifactSha256 requires either null or a valid 64-char hex
        // digest -- an empty string would fail that check, so bail out entirely if hashing fails.
        val sha256 = apkSha256(info) ?: return null
        val sourceId = localSourceIdFor(extension.packageName) ?: return null
        val probed = probedSourceInfo(extension.packageName)
        val manifest = SourceManifest(
            manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
            sourceId = sourceId,
            packageVersion = installedVersion,
            sourceInfo = SourceManifestInfo(
                displayName = displayName,
                languages = probed?.languages ?: setOf(SourceLanguage.RUSSIAN),
                primaryLanguage = probed?.primaryLanguage ?: SourceLanguage.RUSSIAN,
                website = probed?.website,
                iconUrl = probed?.iconUrl,
            ),
            apiVersion = extension.contractVersion,
            runtime = SourceRuntime(id = "apk", abi = "android"),
            entrypoint = extension.packageName,
            packageUrl = "",
            sha256 = sha256,
            artifactSizeBytes = File(extension.apkPath).length(),
            minClientVersion = 1,
        )
        return ExternalSourcePackageStatus(
            sourceId = sourceId,
            availableManifest = manifest,
            activePackage = ActiveExternalSourcePackage(
                manifest = manifest,
                installed = InstalledSourcePackage(
                    sourceId = sourceId,
                    packageVersion = installedVersion,
                    packagePath = extension.packageName,
                    artifactSha256 = sha256,
                ),
            ),
        )
    }

    override suspend fun repositoryContentsForUi(): List<ExternalSourceRepositoryContent> = listOf(
        ExternalSourceRepositoryContent(
            endpoint = endpoint,
            packages = packageStatusesForUi(),
            error = lastRefreshError,
        ),
    )

    override suspend fun installAvailablePackageFromUi(
        sourceId: SourceId,
        onStage: (SourcePackageInstallStage) -> Unit,
        initialize: suspend () -> Unit,
    ) {
        val entry = cachedEntries.firstOrNull { it.id == sourceId.value }
            ?: throw SourcePackageStateException(
                "Source package is no longer advertised by the repository: $sourceId",
            )
        onStage(SourcePackageInstallStage.DOWNLOADING)
        val apkFile = installer.downloadAndVerify(entry).getOrElse { throwable ->
            throw SourcePackageStateException("Failed to download ${entry.id}", throwable)
        }
        onStage(SourcePackageInstallStage.ACTIVATING)
        installer.requestInstall(apkFile)
    }

    override suspend fun uninstallPackageFromUi(sourceId: SourceId) {
        // Repository-advertised packages are looked up by packageName; local-only packages
        // (see packageStatusesForUi/localSourceIdFor) are keyed by a slug derived from their
        // real packageName, so re-derive that mapping from what's currently installed.
        val packageName = cachedEntries.firstOrNull { it.id == sourceId.value }?.packageName
            ?: PackageManagerSourceDiscovery.discover(androidContext)
                .firstOrNull { localSourceIdFor(it.packageName) == sourceId }
                ?.packageName
            ?: throw SourcePackageStateException(
                "Source package is no longer installed or advertised: $sourceId",
            )
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE)
            .setData(packageUri)
            .putExtra(Intent.EXTRA_RETURN_RESULT, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            androidContext.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            androidContext.startActivity(
                Intent(Intent.ACTION_DELETE)
                    .setData(packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun statusFor(entry: SourceRepositoryEntry): ExternalSourcePackageStatus {
        val availableManifest = manifestFor(
            entry,
            version = entry.version,
            sha256 = entry.sha256,
            sizeBytes = entry.sizeBytes,
            probed = probedSourceInfo(entry.packageName),
        )
        val installedInfo = runCatching {
            androidContext.packageManager.getPackageInfo(entry.packageName, 0)
        }.getOrNull()
        val activePackage = installedInfo?.let { info -> activePackageFor(entry, info) }
        return ExternalSourcePackageStatus(
            sourceId = SourceId(entry.id),
            availableManifest = availableManifest,
            activePackage = activePackage,
        )
    }

    private fun activePackageFor(entry: SourceRepositoryEntry, info: PackageInfo): ActiveExternalSourcePackage? {
        val installedVersion = info.versionName?.takeIf(String::isNotBlank) ?: return null
        val installedSha256 = apkSha256(info) ?: entry.sha256
        val installedManifest = manifestFor(
            entry,
            version = installedVersion,
            sha256 = installedSha256,
            sizeBytes = entry.sizeBytes,
            probed = probedSourceInfo(entry.packageName),
        )
        return ActiveExternalSourcePackage(
            manifest = installedManifest,
            installed = InstalledSourcePackage(
                sourceId = SourceId(entry.id),
                packageVersion = installedVersion,
                packagePath = entry.packageName,
                artifactSha256 = installedSha256,
            ),
        )
    }

    private fun manifestFor(
        entry: SourceRepositoryEntry,
        version: String,
        sha256: String,
        sizeBytes: Long,
        probed: SourceInfo? = null,
    ): SourceManifest = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId(entry.id),
        packageVersion = version,
        sourceInfo = SourceManifestInfo(
            displayName = entry.name,
            languages = probed?.languages ?: setOf(SourceLanguage.RUSSIAN),
            primaryLanguage = probed?.primaryLanguage ?: SourceLanguage.RUSSIAN,
            website = probed?.website,
            iconUrl = probed?.iconUrl,
        ),
        apiVersion = entry.contractVersion,
        runtime = SourceRuntime(id = "apk", abi = "android"),
        entrypoint = entry.packageName,
        packageUrl = entry.apkUrl,
        sha256 = sha256,
        artifactSizeBytes = sizeBytes,
        minClientVersion = 1,
    )

    /** SourceId requires a lowercase slug; an Android package name (reverse-DNS, dots) doesn't
     *  qualify, so derive a slug from it for locally-discovered, non-repository packages. */
    private fun localSourceIdFor(packageName: String): SourceId? =
        runCatching { SourceId(packageName.lowercase().replace('.', '-')) }.getOrNull()

    private fun apkSha256(info: PackageInfo): String? = runCatching {
        val apkPath = info.applicationInfo?.sourceDir ?: return null
        File(apkPath).inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString(separator = "") { "%02x".format(it) }
        }
    }.getOrNull()
}
