package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * The launcher icon an extension APK carries, saved as a small PNG the app can show wherever a source
 * is named. An extension's source has no icon URL of its own, and the repository index (which does)
 * is not always at hand, but the APK is installed, so its own icon is always there.
 */
object ExtensionIcons {
    private const val DIRECTORY = "extension_icons"
    private const val SIZE_PX = 128

    /** A `file://` URI for the icon of [packageName], or null if the APK has none that can be read. */
    fun uriFor(context: Context, packageName: String): String? = runCatching {
        val apk = InstalledApkExtensions.apkFile(context, packageName) ?: return null
        // Keyed by the APK's timestamp, so an updated extension gets its new icon.
        val file = File(File(context.filesDir, DIRECTORY).apply { mkdirs() }, "$packageName-${apk.lastModified()}.png")
        if (!file.isFile) {
            val packageManager = context.packageManager
            val info = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.applicationInfo ?: return null
            info.sourceDir = apk.absolutePath
            info.publicSourceDir = apk.absolutePath
            val bitmap = info.loadIcon(packageManager).toBitmap(SIZE_PX, SIZE_PX)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Older copies of this icon are no longer needed.
            file.parentFile?.listFiles { other -> other.name.startsWith("$packageName-") && other != file }
                ?.forEach(File::delete)
        }
        Uri.fromFile(file).toString()
    }.getOrNull()
}
