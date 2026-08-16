package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.akkirrai.beakokit.api.SourceExtensionContract

/** One source-extension APK found installed on the device via [SourceExtensionContract]. */
data class DiscoveredSourceExtension(
    val packageName: String,
    val apkPath: String,
    val sourceClassName: String,
    val contractVersion: Int,
)

/** Finds installed source-extension APKs through the same PackageManager query Mihon-style apps use. */
object PackageManagerSourceDiscovery {
    fun discover(context: Context): List<DiscoveredSourceExtension> {
        val packageManager = context.packageManager
        val intent = Intent(SourceExtensionContract.ACTION)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        return resolved.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val metaData = activityInfo.metaData ?: return@mapNotNull null
            val sourceClassName = metaData.getString(SourceExtensionContract.META_SOURCE_CLASS)
                ?: return@mapNotNull null
            val contractVersion = metaData.getInt(SourceExtensionContract.META_CONTRACT_VERSION, -1)
            if (contractVersion != SourceExtensionContract.CONTRACT_VERSION) return@mapNotNull null
            val apkPath = activityInfo.applicationInfo?.sourceDir ?: return@mapNotNull null
            DiscoveredSourceExtension(
                packageName = activityInfo.packageName,
                apkPath = apkPath,
                sourceClassName = sourceClassName,
                contractVersion = contractVersion,
            )
        }
    }
}
