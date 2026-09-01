package org.akkirrai.hibiki.core.download

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object OfflineMediaCache {
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "hibiki_downloads"

    private const val PLAYER_CONNECT_TIMEOUT_MS = 8_000
    private const val PLAYER_READ_TIMEOUT_MS = 20_000
    private const val PLAYER_HTTP_USER_AGENT = "HibikiPlayer/1.0 (Android Media3)"
    private const val STREAMING_CACHE_MAX_BYTES = 64L * 1024L * 1024L
    private const val LEGACY_CACHE_MIGRATION_KEY = "legacy_media_cache_migrated_v1"

    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var downloadCache: SimpleCache? = null
    private var streamingCache: SimpleCache? = null
    private var downloadManager: DownloadManager? = null

    /**
     * Removes the pre-v1 streaming cache only when Media3 has no recorded offline downloads.
     * The old player stored both kinds of media in filesDir, which Android counts as user data.
     */
    @Synchronized
    fun migrateLegacyStreamingCacheIfSafe(context: Context) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(MIGRATION_PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(LEGACY_CACHE_MIGRATION_KEY, false) || downloadCache != null) return

        val legacyDirectory = File(appContext.filesDir, LEGACY_CACHE_DIRECTORY)
        if (!legacyDirectory.exists()) {
            preferences.markLegacyMigrationComplete()
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        var legacyCache: SimpleCache? = null
        var legacyManager: DownloadManager? = null
        try {
            legacyCache = SimpleCache(
                legacyDirectory,
                NoOpCacheEvictor(),
                getDatabaseProvider(appContext),
            )
            legacyManager = DownloadManager(
                appContext,
                getDatabaseProvider(appContext),
                legacyCache,
                buildUpstreamDataSourceFactory(appContext, emptyMap()),
                executor,
            )
            val cursor = legacyManager.downloadIndex.getDownloads()
            val hasOfflineDownloads = try {
                cursor.moveToNext()
            } finally {
                cursor.close()
            }
            if (!hasOfflineDownloads) {
                legacyManager.release()
                legacyManager = null
                legacyCache.release()
                legacyCache = null
                legacyDirectory.deleteRecursively()
            }
            preferences.markLegacyMigrationComplete()
        } finally {
            legacyManager?.release()
            legacyCache?.release()
            executor.shutdownNow()
        }
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        val appContext = context.applicationContext
        return downloadManager ?: DownloadManager(
            appContext,
            getDatabaseProvider(appContext),
            getDownloadCache(appContext),
            buildDownloadUpstreamDataSourceFactory(appContext),
            Executors.newFixedThreadPool(2),
        ).also { downloadManager = it }
    }

    /**
     * Media3's `DownloadRequest` cannot carry per-download HTTP headers, and this manager's
     * upstream factory is a single process-wide instance shared by every active download -- so
     * headers a source requires (Referer/Cookie/User-Agent) are instead looked up per request URL
     * from [OfflineStreamHeaders], which [OfflineDownloadQueue] populates with the headers of each
     * resolved [org.akkirrai.hibiki.core.model.PlaybackStream] right before queueing its download.
     */
    private fun buildDownloadUpstreamDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(PLAYER_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(PLAYER_READ_TIMEOUT_MS)
            .setUserAgent(PLAYER_HTTP_USER_AGENT)
            .setDefaultRequestProperties(buildPlaybackRequestHeaders(emptyMap()))
        val headerInjectingFactory = HeaderInjectingHttpDataSourceFactory(httpFactory) { url ->
            OfflineStreamHeaders.get(appContext, url)
        }
        return DefaultDataSource.Factory(appContext, headerInjectingFactory)
    }

    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return downloadCache ?: SimpleCache(
            File(appContext.filesDir, LEGACY_CACHE_DIRECTORY),
            NoOpCacheEvictor(),
            getDatabaseProvider(appContext),
        ).also { downloadCache = it }
    }

    /**
     * Temporary cache for ordinary streaming. It lives in Android's cache directory and
     * evicts the least recently used media instead of growing with every watched episode.
     */
    @Synchronized
    fun getStreamingCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return streamingCache ?: SimpleCache(
            File(appContext.cacheDir, "streaming_media"),
            LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_MAX_BYTES),
            getDatabaseProvider(appContext),
        ).also { streamingCache = it }
    }

    fun buildPlaybackDataSourceFactory(
        context: Context,
        headers: Map<String, String>,
        resourceHeadersByUrl: Map<String, Map<String, String>> = emptyMap(),
    ): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getStreamingCache(context))
            .setUpstreamDataSourceFactory(buildUpstreamDataSourceFactory(context, headers, resourceHeadersByUrl))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun buildUpstreamDataSourceFactory(
        context: Context,
        headers: Map<String, String>,
        resourceHeadersByUrl: Map<String, Map<String, String>> = emptyMap(),
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(PLAYER_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(PLAYER_READ_TIMEOUT_MS)
            .setUserAgent(PLAYER_HTTP_USER_AGENT)
            .setDefaultRequestProperties(buildPlaybackRequestHeaders(emptyMap()))
        val headerInjectingFactory = HeaderInjectingHttpDataSourceFactory(httpFactory) { url ->
            playbackRequestHeadersForUrl(headers, resourceHeadersByUrl, url)
        }
        return DefaultDataSource.Factory(
            context.applicationContext,
            BrowserSessionCookieDataSourceFactory(headerInjectingFactory),
        )
    }

    fun buildPlaybackRequestHeaders(headers: Map<String, String>): Map<String, String> {
        val requestHeaders = LinkedHashMap<String, String>()
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestHeaders[name] = value
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

    internal fun playbackRequestHeadersForUrl(
        defaultHeaders: Map<String, String>,
        resourceHeadersByUrl: Map<String, Map<String, String>>,
        url: String,
    ): Map<String, String> = buildPlaybackRequestHeaders(
        resourceHeadersByUrl[url]?.ifEmpty { defaultHeaders } ?: defaultHeaders,
    )

    @Synchronized
    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        val appContext = context.applicationContext
        return databaseProvider ?: StandaloneDatabaseProvider(appContext)
            .also { databaseProvider = it }
    }

    private fun SharedPreferences.markLegacyMigrationComplete() {
        edit().putBoolean(LEGACY_CACHE_MIGRATION_KEY, true).apply()
    }

    private const val LEGACY_CACHE_DIRECTORY = "offline_media"
    private const val MIGRATION_PREFERENCES = "hibiki_media_cache"
}

/** Wraps an [HttpDataSource.Factory], adding [headersForUrl]'s headers to every request it opens. */
private class HeaderInjectingHttpDataSourceFactory(
    private val delegateFactory: HttpDataSource.Factory,
    private val headersForUrl: (String) -> Map<String, String>,
) : HttpDataSource.Factory {
    override fun createDataSource(): HttpDataSource =
        HeaderInjectingHttpDataSource(delegateFactory.createDataSource(), headersForUrl)

    override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
        delegateFactory.setDefaultRequestProperties(defaultRequestProperties)
        return this
    }
}

/**
 * HLS loads the master playlist and every media segment as individual ExoPlayer requests. Keep
 * them in the same cookie session which the resolver's temporary WebView established instead of
 * using only a stale cookie snapshot captured for the first URL.
 */
private class BrowserSessionCookieDataSourceFactory(
    private val delegateFactory: HttpDataSource.Factory,
) : HttpDataSource.Factory {
    override fun createDataSource(): HttpDataSource = BrowserSessionCookieDataSource(delegateFactory.createDataSource())

    override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
        delegateFactory.setDefaultRequestProperties(defaultRequestProperties)
        return this
    }
}

private class BrowserSessionCookieDataSource(
    private val delegate: HttpDataSource,
) : HttpDataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        val cookies = CookieManager.getInstance().getCookie(dataSpec.uri.toString())
        if (!cookies.isNullOrBlank()) {
            delegate.setRequestProperty("Cookie", cookies)
        }
        return delegate.open(dataSpec)
    }
}

private class HeaderInjectingHttpDataSource(
    private val delegate: HttpDataSource,
    private val headersForUrl: (String) -> Map<String, String>,
) : HttpDataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        headersForUrl(dataSpec.uri.toString()).forEach { (name, value) ->
            delegate.setRequestProperty(name, value)
        }
        return delegate.open(dataSpec)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }
}
