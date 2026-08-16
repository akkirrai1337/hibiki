package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceCatalogEntry
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceFactory
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults

/**
 * Builds a [SourceCatalog] from every source-extension APK currently installed and discoverable
 * via [PackageManagerSourceDiscovery]. Each entry lazily loads its class through
 * [PackageManagerSourceLoader] only when actually selected -- discovery itself only needs to
 * read metadata, so one throwaway [SourceContext] is used solely to read each source's
 * declared [org.akkirrai.beakokit.api.SourceInfo] up front.
 */
object PackageManagerSourceCatalog {
    fun build(androidContext: Context): SourceCatalog {
        val discoveryHttpClient = HttpClient(OkHttp) { installBeakoKitHttpDefaults() }
        val discoveryContext = DefaultSourceContext(
            httpClient = discoveryHttpClient,
            preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
        )
        val entries = PackageManagerSourceDiscovery.discover(androidContext).mapNotNull { extension ->
            runCatching {
                val probe = PackageManagerSourceLoader.load(androidContext, extension, discoveryContext)
                SourceCatalogEntry(
                    info = probe.info,
                    factory = SourceFactory { context ->
                        PackageManagerSourceLoader.load(androidContext, extension, context)
                    },
                )
            }.getOrNull()
        }
        return SourceCatalog(entries)
    }
}
