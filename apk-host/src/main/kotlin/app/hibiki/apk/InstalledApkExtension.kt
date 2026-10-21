package app.hibiki.apk

import java.io.File

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
