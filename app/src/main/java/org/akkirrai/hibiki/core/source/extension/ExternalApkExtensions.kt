package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.log.AppLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** An Aniyomi extension that is installed on the device but was not put there through Hibiki's own flow. */
data class ExternalApkExtension(
    val packageName: String,
    val label: String,
    val versionName: String,
    val signingFingerprint: String,
    /** Package that started the install (a browser, a file manager, another extension manager), if Android recorded one. */
    val installerPackage: String?,
    val installerLabel: String?,
    val apkPath: String,
)

/**
 * Finds extensions installed on the device that Hibiki does not trust (yet). Hibiki runs an extension
 * from its own private copy, and trusts one that comes from a repository the user has added. An
 * extension that is not in any added repository, or whose repository was removed, waits for the
 * user to trust it. The installer Android records (usually just the package installer) says nothing
 * about where the APK came from, so it is only shown to the user.
 */
object ExternalApkExtensions {
    data class Detection(
        /** True when the set of usable extensions changed, so sources should be reloaded. */
        val changed: Boolean,
        /** Extensions from another installer that still need the user's decision. */
        val pending: List<ExternalApkExtension>,
    )

    fun sync(context: Context): Detection {
        val appContext = context.applicationContext
        var changed = false
        val pending = mutableListOf<ExternalApkExtension>()
        val repositories = AppPreferences.readSourceRepositoryUrls(appContext)
        // The last index fetched for each added repository; no network is involved here.
        val repositoryOfPackage = mutableMapOf<String, String>()
        repositories.forEach { url ->
            RepositoryCatalogCache.get(appContext, url)?.extensions?.forEach { repositoryOfPackage.putIfAbsent(it.pkg, url) }
        }
        for (extension in installedExtensions(appContext)) {
            if (ApkExtensionInstaller.wasInstalledBySystem(appContext, extension.packageName)) {
                if (ApkExtensionTrustStore.isOriginTrusted(appContext, extension.packageName, repositories)) {
                    if (refreshAdoptedCopy(appContext, extension)) changed = true
                } else {
                    pending += extension
                }
                continue
            }
            val repository = repositoryOfPackage[extension.packageName]
            if (repository != null) {
                if (adopt(appContext, extension, repository)) changed = true
            } else {
                pending += extension
            }
        }
        return Detection(changed, pending)
    }

    /**
     * Copies the installed APK into Hibiki's private store and trusts its signing certificate, on the
     * strength of [repositoryUrl] or, when that is null, of the user's decision.
     */
    fun adopt(context: Context, extension: ExternalApkExtension, repositoryUrl: String? = null): Boolean = try {
        val appContext = context.applicationContext
        copyPrivate(appContext, extension)
        ApkExtensionTrustStore.trust(appContext, extension.packageName, extension.signingFingerprint)
        ApkExtensionTrustStore.setOrigin(appContext, extension.packageName, repositoryUrl)
        ApkExtensionInstaller.markAdopted(appContext, extension.packageName)
        true
    } catch (error: Exception) {
        AppLogger.w("ExternalExtensions", "Could not adopt ${extension.packageName}", error)
        false
    }

    /** An extension updated outside Hibiki keeps its trust when the signer is unchanged; refresh the private copy to match. */
    private fun refreshAdoptedCopy(context: Context, extension: ExternalApkExtension): Boolean {
        val private = InstalledApkExtensions.scan(context)[extension.packageName] ?: return false
        if (private.versionName == extension.versionName) return false
        if (!ApkExtensionTrustStore.isTrusted(context, extension.packageName, extension.signingFingerprint)) return false
        return runCatching { copyPrivate(context, extension) }
            .onFailure { AppLogger.w("ExternalExtensions", "Could not refresh ${extension.packageName}", it) }
            .isSuccess
    }

    private fun copyPrivate(context: Context, extension: ExternalApkExtension) {
        val directory = File(context.filesDir, EXTENSIONS_DIRECTORY).apply { mkdirs() }
        val staged = File(directory, "${extension.packageName}.$TEMP_EXTENSION")
        val target = File(directory, "${extension.packageName}.$PRIVATE_EXTENSION")
        staged.delete()
        try {
            File(extension.apkPath).copyTo(staged, overwrite = true)
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            InstalledApkExtensions.makeReadOnly(target)
        } finally {
            staged.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun installedExtensions(context: Context): List<ExternalApkExtension> {
        val packageManager = context.packageManager
        val flags = InstalledApkExtensions.packageFlags
        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                packageManager.getInstalledPackages(flags)
            }
        } catch (error: Exception) {
            AppLogger.w("ExternalExtensions", "Could not list installed packages", error)
            return emptyList()
        }
        return packages.mapNotNull { info -> info.toExternal(context, packageManager) }
    }

    private fun PackageInfo.toExternal(context: Context, packageManager: PackageManager): ExternalApkExtension? {
        if (packageName == context.packageName) return null
        if (reqFeatures.orEmpty().none { it.name == ANIME_EXTENSION_FEATURE }) return null
        val metadata = applicationInfo?.metaData
        if (metadata?.getString(METADATA_SOURCE_CLASS).isNullOrBlank()) return null
        val version = versionName ?: return null
        val fingerprint = with(InstalledApkExtensions) { signingFingerprint() } ?: return null
        val apkPath = applicationInfo?.sourceDir ?: return null
        val installer = installerOf(packageManager, packageName)
        return ExternalApkExtension(
            packageName = packageName,
            label = applicationInfo?.loadLabel(packageManager)?.toString()?.removePrefix("Aniyomi: ")?.ifBlank { null }
                ?: packageName,
            versionName = version,
            signingFingerprint = fingerprint,
            installerPackage = installer,
            installerLabel = installer?.let { labelOf(packageManager, it) },
            apkPath = apkPath,
        )
    }

    @Suppress("DEPRECATION")
    private fun installerOf(packageManager: PackageManager, packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val source = packageManager.getInstallSourceInfo(packageName)
            source.initiatingPackageName ?: source.installingPackageName
        } else {
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    private fun labelOf(packageManager: PackageManager, packageName: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val EXTENSIONS_DIRECTORY = "exts"
    private const val PRIVATE_EXTENSION = "ext"
    private const val TEMP_EXTENSION = "tmp"
}
