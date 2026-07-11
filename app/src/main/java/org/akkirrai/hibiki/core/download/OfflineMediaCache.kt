package org.akkirrai.hibiki.core.download

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
            buildUpstreamDataSourceFactory(appContext, emptyMap()),
            Executors.newFixedThreadPool(2),
        ).also { downloadManager = it }
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
    ): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getStreamingCache(context))
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

    private fun SharedPreferences.markLegacyMigrationComplete() {
        edit().putBoolean(LEGACY_CACHE_MIGRATION_KEY, true).apply()
    }

    private const val LEGACY_CACHE_DIRECTORY = "offline_media"
    private const val MIGRATION_PREFERENCES = "hibiki_media_cache"
}
