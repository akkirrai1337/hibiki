package org.akkirrai.hibiki.core.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.net.URI
import java.util.concurrent.Executors

@UnstableApi
object OfflineMediaCache {
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "hibiki_downloads"

    private const val PLAYER_CONNECT_TIMEOUT_MS = 8_000
    private const val PLAYER_READ_TIMEOUT_MS = 20_000
    private const val PLAYER_HTTP_USER_AGENT = "HibikiPlayer/1.0 (Android Media3)"

    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var downloadCache: SimpleCache? = null
    private var downloadManager: DownloadManager? = null

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        val appContext = context.applicationContext
        return downloadManager ?: DownloadManager(
            appContext,
            getDatabaseProvider(appContext),
            getDownloadCache(appContext),
            buildUpstreamDataSourceFactory(appContext, emptyMap()),
            Executors.newFixedThreadPool(2),
        ).also { downloadManager = it }
    }

    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return downloadCache ?: SimpleCache(
            File(appContext.filesDir, "offline_media"),
            NoOpCacheEvictor(),
            getDatabaseProvider(appContext),
        ).also { downloadCache = it }
    }

    fun buildPlaybackDataSourceFactory(
        context: Context,
        headers: Map<String, String>,
    ): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getDownloadCache(context))
            .setUpstreamDataSourceFactory(buildUpstreamDataSourceFactory(context, headers))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun buildUpstreamDataSourceFactory(
        context: Context,
        headers: Map<String, String>,
    ): DataSource.Factory {
        val requestHeaders = buildPlaybackRequestHeaders(headers)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(PLAYER_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(PLAYER_READ_TIMEOUT_MS)
            .setUserAgent(PLAYER_HTTP_USER_AGENT)
            .setDefaultRequestProperties(requestHeaders)
        return DefaultDataSource.Factory(context.applicationContext, httpFactory)
    }

    fun buildPlaybackRequestHeaders(headers: Map<String, String>): Map<String, String> {
        val requestHeaders = LinkedHashMap<String, String>()
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestHeaders[name] = value
            }
        }

        val referer = requestHeaders.entries
            .firstOrNull { (name, _) -> name.equals("Referer", ignoreCase = true) || name.equals("Referrer", ignoreCase = true) }
            ?.value
        if (referer != null) {
            val origin = runCatching {
                URI(referer).let { uri -> "${uri.scheme}://${uri.host}" }
            }.getOrNull()
            if (origin != null && requestHeaders.keys.none { it.equals("Origin", ignoreCase = true) }) {
                requestHeaders["Origin"] = origin
            }
        }

        if (requestHeaders.keys.none { it.equals("Accept", ignoreCase = true) }) {
            requestHeaders["Accept"] = "*/*"
        }
        if (requestHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            requestHeaders["User-Agent"] = PLAYER_HTTP_USER_AGENT
        }

        return requestHeaders
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        val appContext = context.applicationContext
        return databaseProvider ?: StandaloneDatabaseProvider(appContext)
            .also { databaseProvider = it }
    }
}
