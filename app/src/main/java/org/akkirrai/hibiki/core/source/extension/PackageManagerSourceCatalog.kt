package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceCatalogEntry
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceFactory
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.hibiki.core.log.AppLogger

/**
 * Result of probing every installed source-extension APK once: the [SourceCatalog] used to
 * browse/search sources, plus each extension's declared [SourceInfo] keyed by Android package
 * name -- reused by the repository/management screen so it doesn't have to reload every
 * extension's class a second time just to read its website/iconUrl.
 */
data class PackageManagerSourceProbe(
    val catalog: SourceCatalog,
    val infoByPackageName: Map<String, SourceInfo>,
)

/**
 * Builds a [SourceCatalog] from every source-extension APK currently installed and discoverable
 * via [PackageManagerSourceDiscovery]. Each entry lazily loads its class through
 * [PackageManagerSourceLoader] only when actually selected -- discovery itself only needs to
 * read metadata, so one throwaway [SourceContext] is used solely to read each source's
 * declared [org.akkirrai.beakokit.api.SourceInfo] up front.
 */
object PackageManagerSourceCatalog {
    fun build(androidContext: Context): SourceCatalog = buildProbe(androidContext).catalog

    fun buildProbe(androidContext: Context): PackageManagerSourceProbe {
        val discoveryHttpClient = HttpClient(OkHttp) { installBeakoKitHttpDefaults() }
        val discoveryContext = DefaultSourceContext(
            httpClient = discoveryHttpClient,
            preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            logger = SourceLogger { level, message, throwable ->
                val tag = "BeakoKit/discovery"
                when (level) {
                    SourceLogLevel.DEBUG -> AppLogger.d(tag, message)
                    SourceLogLevel.WARNING -> AppLogger.w(tag, message, throwable)
                    SourceLogLevel.ERROR -> AppLogger.e(tag, message, throwable)
                }
            },
        )
        val infoByPackageName = mutableMapOf<String, SourceInfo>()
        val entries = PackageManagerSourceDiscovery.discover(androidContext).mapNotNull { extension ->
            runCatching {
                val probe = PackageManagerSourceLoader.load(androidContext, extension, discoveryContext)
                infoByPackageName[extension.packageName] = probe.info
                SourceCatalogEntry(
                    info = probe.info,
                    factory = SourceFactory { context ->
                        PackageManagerSourceLoader.load(androidContext, extension, context)
                    },
                )
            }.getOrElse { throwable ->
                AppLogger.e(
                    "BeakoKit/discovery",
                    "Failed to probe source extension ${extension.packageName} (${extension.sourceClassName}): " +
                        "${throwable::class.qualifiedName}: ${throwable.message}",
                    throwable,
                )
                null
            }
        }
        return PackageManagerSourceProbe(SourceCatalog(entries), infoByPackageName)
    }
}
