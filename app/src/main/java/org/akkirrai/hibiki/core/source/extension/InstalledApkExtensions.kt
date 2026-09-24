package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import org.akkirrai.hibiki.app.settings.AppPreferences
import java.security.MessageDigest
import java.io.File

data class InstalledApkExtensionInfo(
    val versionName: String,
    /** Aniyomi's extension API major version is the first component of versionName (for example 14 in 14.10). */
    val apiVersion: Int?,
    /** Entry point declared by tachiyomi.animeextension.class in the APK manifest. */
    val sourceClassName: String?,
    val signingFingerprint: String?,
    val isTrusted: Boolean,
    val isSystemInstalled: Boolean,
)

object InstalledApkExtensions {
    /** Android 14+ refuses to load DEX from a file that still has write permissions. */
    fun makeReadOnly(apk: File) {
        require(apk.isFile) { "APK file does not exist: ${apk.absolutePath}" }
        Os.chmod(apk.absolutePath, READ_ONLY_FILE_MODE)
        check(!apk.canWrite()) { "Could not make extension APK read-only: ${apk.absolutePath}" }
    }

    fun apkFile(context: Context, packageName: String): File? {
        if (!packageName.matches(PACKAGE_NAME_PATTERN)) return null
        return File(context.filesDir, "$EXTENSIONS_DIRECTORY/$packageName.$PRIVATE_EXTENSION")
            .takeIf(File::isFile)
    }

    fun scan(context: Context): Map<String, InstalledApkExtensionInfo> {
        val packageManager = context.packageManager
        val repositories = AppPreferences.readSourceRepositoryUrls(context)
        return File(context.filesDir, EXTENSIONS_DIRECTORY).listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == PRIVATE_EXTENSION }
            .mapNotNull { apk ->
                val packageInfo = packageManager.getPackageArchiveInfo(apk.absolutePath, packageFlags)
                    ?: return@mapNotNull null
                if (packageInfo.reqFeatures.orEmpty().none { it.name == ANIME_EXTENSION_FEATURE }) {
                    return@mapNotNull null
                }
                val version = packageInfo.versionName ?: return@mapNotNull null
                val signingFingerprint = packageInfo.signingFingerprint()
                val extensionMetadata = packageInfo.applicationInfo?.metaData
                packageInfo.packageName to InstalledApkExtensionInfo(
                    versionName = version,
                    apiVersion = version.substringBefore('.').toIntOrNull(),
                    sourceClassName = extensionMetadata?.getString(METADATA_SOURCE_CLASS)
                        ?.takeIf(String::isNotBlank),
                    signingFingerprint = signingFingerprint,
                    isTrusted = signingFingerprint != null &&
                        ApkExtensionTrustStore.isTrusted(context, packageInfo.packageName, signingFingerprint) &&
                        ApkExtensionTrustStore.isOriginTrusted(context, packageInfo.packageName, repositories),
                    isSystemInstalled = ApkExtensionInstaller.wasInstalledBySystem(context, packageInfo.packageName),
                )
            }
            .toMap()
    }

    @Suppress("DEPRECATION")
    val packageFlags: Int
        get() = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                0
            })

    @Suppress("DEPRECATION")
    internal fun PackageInfo.signingFingerprint(): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        }
        return signatures
            ?.map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }
            ?.sorted()
            ?.joinToString(",")
            ?.takeIf(String::isNotBlank)
    }

    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val EXTENSIONS_DIRECTORY = "exts"
    private const val PRIVATE_EXTENSION = "ext"
    private const val READ_ONLY_FILE_MODE = 0b100_100_100 // 0444
    private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
}
