package org.akkirrai.hibiki.core.download

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.hibiki.core.network.ChallengeSessionProviderImpl
import org.akkirrai.hibiki.core.source.EmbedWebViewExtractor
import org.akkirrai.hibiki.core.source.ExternalSourceConfigStore
import org.akkirrai.hibiki.core.source.extension.PackageManagerSourceCatalog
import org.akkirrai.hibiki.player.SharedAnimeWatchRepository

/**
 * Background-reachable twin of the [SharedAnimeWatchRepository] built inside the Compose-scoped
 * HibikiApp composition. Offline downloads resolve their stream from [OfflineDownloadQueue]'s
 * plain IO-dispatcher scope, outside any Composable, so they cannot reuse the UI's `remember`ed
 * instance and need one built from just a [Context] instead.
 */
internal object BackgroundExternalWatchRepositoryFactory {
    @Volatile
    private var instance: SharedAnimeWatchRepository? = null

    fun get(context: Context): SharedAnimeWatchRepository {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val created = create(context.applicationContext)
            instance = created
            return created
        }
    }

    private fun create(appContext: Context): SharedAnimeWatchRepository {
        val sourceHttpClient = HttpClient(OkHttp) {
            installBeakoKitHttpDefaults(
                BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Android external-source"),
            )
            followRedirects = false
        }
        val configStore = ExternalSourceConfigStore(appContext)
        val challengeSessionProvider = ChallengeSessionProviderImpl(appContext)
        val embedWebViewExtractor = EmbedWebViewExtractor(appContext)
        return SharedAnimeWatchRepository(
            client = HttpClient(OkHttp) {
                installBeakoKitHttpDefaults(
                    BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Android external-playback"),
                )
            },
            sourceHttpClient = sourceHttpClient,
            externalSourceFactory = { sourceId, sourceContext ->
                // Rebuilt per call (cheap metadata probe) so newly installed extensions are
                // picked up without requiring the download queue to track install broadcasts.
                val registry = ExternalSourceRegistry(PackageManagerSourceCatalog.build(appContext))
                if (registry.sources.any { it.id == sourceId }) registry.create(sourceId, sourceContext) else null
            },
            sourceConfigProvider = configStore::load,
            challengeSessionProvider = challengeSessionProvider,
            additionalExtractors = listOf(embedWebViewExtractor),
        )
    }
}
