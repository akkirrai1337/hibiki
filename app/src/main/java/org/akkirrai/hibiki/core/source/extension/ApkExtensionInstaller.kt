package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Downloads Aniyomi APKs, then hands installation to Android's package installer. */
object ApkExtensionInstaller {
    suspend fun prepareSystemInstall(
        context: Context,
        client: HttpClient,
        indexUrl: String,
        extension: ApkRepositoryExtension,
    ): PreparedApkExtensionInstall = withContext(Dispatchers.IO) {
        require(extension.pkg.matches(PACKAGE_NAME_PATTERN)) {
            "Invalid Android package name: ${extension.pkg}"
        }

        val cacheDirectory = File(context.cacheDir, APK_CACHE_DIRECTORY).apply { mkdirs() }
        val downloadedApk = File(cacheDirectory, "${extension.pkg}.download")
        val preparedApk = File(cacheDirectory, "${extension.pkg}.pending.apk")
        downloadedApk.delete()
        preparedApk.delete()

        try {
            ExtensionMarketplaceClient(client, indexUrl).downloadApk(extension, downloadedApk)
            val packageInfo = context.packageManager.getPackageArchiveInfo(downloadedApk.absolutePath, PACKAGE_FLAGS)
                ?: error("Downloaded file is not a valid Android APK")
            require(packageInfo.packageName == extension.pkg) {
                "APK package mismatch: expected ${extension.pkg}, got ${packageInfo.packageName}"
            }
            require(packageInfo.reqFeatures.orEmpty().any { it.name == ANIME_EXTENSION_FEATURE }) {
                "APK does not declare the Aniyomi anime extension feature"
            }
            require(!packageInfo.applicationInfo?.metaData?.getString(METADATA_SOURCE_CLASS).isNullOrBlank()) {
                "APK does not declare an Aniyomi source class"
            }
            Files.move(downloadedApk.toPath(), preparedApk.toPath(), StandardCopyOption.REPLACE_EXISTING)
            PreparedApkExtensionInstall(extension.pkg, preparedApk)
        } catch (error: Exception) {
            preparedApk.delete()
            throw error
        } finally {
            downloadedApk.delete()
        }
    }

    fun packageInstallIntent(context: Context, prepared: PreparedApkExtensionInstall): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            prepared.apkFile,
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    /** Keeps a private copy for Hibiki's in-process DEX loader after Android confirms installation. */
    fun completeSystemInstall(context: Context, prepared: PreparedApkExtensionInstall) {
        val appContext = context.applicationContext
        val extensionDirectory = File(appContext.filesDir, EXTENSIONS_DIRECTORY).apply { mkdirs() }
        val stagedApk = File(extensionDirectory, "${prepared.packageName}.$TEMP_EXTENSION")
        val privateApk = File(extensionDirectory, "${prepared.packageName}.$PRIVATE_EXTENSION")
        stagedApk.delete()
        try {
            prepared.apkFile.copyTo(stagedApk, overwrite = true)
            Files.move(
                stagedApk.toPath(),
                privateApk.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            InstalledApkExtensions.makeReadOnly(privateApk)
            val info = InstalledApkExtensions.scan(appContext)[prepared.packageName]
                ?: error("Installed APK metadata could not be read")
            val fingerprint = info.signingFingerprint
                ?: error("Installed APK signing certificate is missing")
            ApkExtensionTrustStore.trust(appContext, prepared.packageName, fingerprint)
            ApkExtensionInstallRegistry.markSystemInstalled(appContext, prepared.packageName)
        } catch (error: Exception) {
            stagedApk.delete()
            throw error
        } finally {
            prepared.apkFile.delete()
        }
    }

    fun cancelSystemInstall(prepared: PreparedApkExtensionInstall) {
        prepared.apkFile.delete()
    }

    fun packageUninstallIntent(packageName: String): Intent {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) {
            "Invalid Android package name: $packageName"
        }
        return Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    fun completeSystemUninstall(context: Context, packageName: String) {
        removePrivateCopy(context, packageName)
        ApkExtensionInstallRegistry.forget(context, packageName)
        ApkExtensionTrustStore.forget(context, packageName)
    }

    fun removePrivateCopy(context: Context, packageName: String) {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) {
            "Invalid Android package name: $packageName"
        }
        File(context.filesDir, "$EXTENSIONS_DIRECTORY/$packageName.$PRIVATE_EXTENSION").delete()
    }

    fun wasInstalledBySystem(context: Context, packageName: String): Boolean =
        ApkExtensionInstallRegistry.isSystemInstalled(context, packageName)

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    @Suppress("DEPRECATION")
    private fun packageInfoFlags(): Int = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private val PACKAGE_FLAGS: Int get() = packageInfoFlags()

    private const val APK_CACHE_DIRECTORY = "extension-apks"
    private const val EXTENSIONS_DIRECTORY = "exts"
    private const val PRIVATE_EXTENSION = "ext"
    private const val TEMP_EXTENSION = "tmp"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
}

data class PreparedApkExtensionInstall(
    val packageName: String,
    val apkFile: File,
)

private object ApkExtensionInstallRegistry {
    fun isSystemInstalled(context: Context, packageName: String): Boolean =
        preferences(context).getStringSet(INSTALLED_PACKAGES_KEY, emptySet()).orEmpty().contains(packageName)

    fun markSystemInstalled(context: Context, packageName: String) {
        val current = preferences(context).getStringSet(INSTALLED_PACKAGES_KEY, emptySet()).orEmpty().toMutableSet()
        preferences(context).edit().putStringSet(INSTALLED_PACKAGES_KEY, current + packageName).apply()
    }

    fun forget(context: Context, packageName: String) {
        val current = preferences(context).getStringSet(INSTALLED_PACKAGES_KEY, emptySet()).orEmpty() - packageName
        preferences(context).edit().putStringSet(INSTALLED_PACKAGES_KEY, current).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "aniyomi_extension_installs"
    private const val INSTALLED_PACKAGES_KEY = "system_installed_packages"
}
