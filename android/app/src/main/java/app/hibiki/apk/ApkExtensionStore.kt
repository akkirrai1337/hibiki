package app.hibiki.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** An APK source installed into the app (not as a system package), as read from its own manifest. */
data class InstalledApkExtension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    /** Aniyomi's extension API major version: the first component of versionName (14 in 14.10). */
    val apiVersion: Int?,
    /** The entry point declared by tachiyomi.animeextension.class. */
    val sourceClassName: String?,
    val isNsfw: Boolean,
    val signingFingerprint: String?,
    val isTrusted: Boolean,
    val file: File,
    val iconFile: File?,
)

/**
 * APK sources installed inside the app: the APK files themselves under files/exts (read-only, as
 * Android 14+ requires of DEX it loads), each with its icon, and the signing certificates the
 * person has agreed to trust. No system package installer is involved, so no "install unknown
 * apps" permission and nothing in the phone's app list; loading is ApkSourceRegistry's job.
 */
object ApkExtensionStore {
    private const val DIRECTORY = "exts"
    private const val EXTENSION = "ext"
    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
    private const val TRUST_PREFS = "hibiki-apk-trust"
    private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /** extensions-lib v14 through v16 (hosters, seasons and related titles arrived in 16). */
    val SUPPORTED_API_VERSIONS = 14..16

    @Suppress("DEPRECATION")
    private val packageFlags: Int
        get() = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun list(context: Context): List<InstalledApkExtension> =
        directory(context).listFiles().orEmpty()
            .filter { it.isFile && it.extension == EXTENSION }
            .mapNotNull { read(context, it) }
            .sortedBy { it.name.lowercase() }

    fun find(context: Context, packageName: String): InstalledApkExtension? =
        list(context).firstOrNull { it.packageName == packageName }

    /** What an APK file declares, or null for a file that is not an anime extension. */
    fun inspect(context: Context, apk: File): InstalledApkExtension? = read(context, apk)

    private fun read(context: Context, apk: File): InstalledApkExtension? {
        val packageManager = context.packageManager
        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, packageFlags) ?: return null
        if (info.reqFeatures.orEmpty().none { it.name == ANIME_EXTENSION_FEATURE }) return null
        val versionName = info.versionName ?: return null
        val appInfo = info.applicationInfo
        // An archive's resources resolve only once the info knows where the file is.
        appInfo?.sourceDir = apk.absolutePath
        appInfo?.publicSourceDir = apk.absolutePath
        val label = runCatching { appInfo?.let { packageManager.getApplicationLabel(it).toString() } }.getOrNull()
        val fingerprint = info.signingFingerprint()
        val metadata = appInfo?.metaData
        return InstalledApkExtension(
            packageName = info.packageName,
            name = (label ?: info.packageName).removePrefix("Aniyomi: ").removePrefix("Tachiyomi: "),
            versionName = versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
            apiVersion = versionName.substringBefore('.').toIntOrNull(),
            sourceClassName = metadata?.getString(METADATA_SOURCE_CLASS)?.takeIf(String::isNotBlank),
            isNsfw = metadata?.getInt(METADATA_NSFW, 0) == 1,
            signingFingerprint = fingerprint,
            isTrusted = fingerprint != null && isTrusted(context, info.packageName, fingerprint),
            file = apk,
            iconFile = File(directory(context), "${info.packageName}.png").takeIf(File::isFile),
        )
    }

    /**
     * Takes a downloaded APK in: checks it is an anime extension of a supported API, then moves it
     * into place read-only and saves its icon. Returns what it installed; [expectedPackage], when
     * given, must match (a repository entry names the package it is supposed to be).
     */
    fun install(context: Context, downloaded: File, expectedPackage: String? = null): InstalledApkExtension {
        val inspected = read(context, downloaded) ?: error("The file is not an Aniyomi anime extension")
        require(inspected.packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: ${inspected.packageName}" }
        if (expectedPackage != null) {
            require(inspected.packageName == expectedPackage) { "Package mismatch: expected $expectedPackage, got ${inspected.packageName}" }
        }
        requireNotNull(inspected.sourceClassName) { "The extension does not declare a source class" }
        val api = inspected.apiVersion
        require(api != null && api in SUPPORTED_API_VERSIONS) {
            "This extension uses Aniyomi API ${api ?: "unknown"}; supported are ${SUPPORTED_API_VERSIONS.first}-${SUPPORTED_API_VERSIONS.last}"
        }
        requireNotNull(inspected.signingFingerprint) { "The extension is not signed" }

        val target = File(directory(context), "${inspected.packageName}.$EXTENSION")
        // The old copy is read-only; it has to be made writable before it can be replaced.
        if (target.exists()) {
            target.setWritable(true, true)
            target.delete()
        }
        downloaded.copyTo(target, overwrite = true)
        downloaded.delete()
        makeReadOnly(target)
        saveIcon(context, target, inspected.packageName)
        return read(context, target) ?: error("The installed extension could not be read back")
    }

    fun uninstall(context: Context, packageName: String) {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        File(directory(context), "$packageName.$EXTENSION").apply { setWritable(true, true); delete() }
        File(directory(context), "$packageName.png").delete()
        File(context.codeCacheDir, "apk-sources/$packageName").deleteRecursively()
    }

    fun isTrusted(context: Context, packageName: String, fingerprint: String): Boolean =
        trustPrefs(context).getString(packageName, null) == fingerprint

    /** The signer trusted for [packageName], if any. */
    fun trustedFingerprint(context: Context, packageName: String): String? = trustPrefs(context).getString(packageName, null)

    fun trust(context: Context, packageName: String, fingerprint: String) {
        trustPrefs(context).edit().putString(packageName, fingerprint).apply()
    }

    fun forget(context: Context, packageName: String) {
        trustPrefs(context).edit().remove(packageName).apply()
    }

    private fun trustPrefs(context: Context) = context.getSharedPreferences(TRUST_PREFS, Context.MODE_PRIVATE)

    private fun makeReadOnly(apk: File) {
        Os.chmod(apk.absolutePath, 0b100_100_100) // 0444
        check(!apk.canWrite()) { "Could not make the extension read-only: ${apk.absolutePath}" }
    }

    private fun saveIcon(context: Context, apk: File, packageName: String) {
        runCatching {
            val packageManager = context.packageManager
            val info = packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return
            val appInfo = info.applicationInfo ?: return
            appInfo.sourceDir = apk.absolutePath
            appInfo.publicSourceDir = apk.absolutePath
            val drawable = packageManager.getApplicationIcon(appInfo)
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            FileOutputStream(File(directory(context), "$packageName.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signingFingerprint(): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) signingInfo?.apkContentsSigners else signatures
        return signatures
            ?.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }
            ?.sorted()
            ?.joinToString(",")
            ?.takeIf(String::isNotBlank)
    }
}
