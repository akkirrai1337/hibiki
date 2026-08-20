package org.akkirrai.hibiki.core.download

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Persists the HTTP headers (Referer/Cookie/User-Agent/...) a resolved [org.akkirrai.hibiki.player.model.PlaybackStream]
 * needs, keyed by its stream URL -- with the URL's origin (scheme+host) kept as a fallback key, since
 * an HLS/DASH manifest's individual segments are fetched from their own URLs (usually the same CDN
 * host as the manifest, but never the manifest's own URL) and still need the same headers.
 *
 * AndroidX Media3's `DownloadRequest` (unlike `MediaItem` used for live playback) has no field for
 * per-request HTTP headers, and [OfflineMediaCache]'s `DownloadManager` uses one process-wide
 * [androidx.media3.datasource.DataSource.Factory] for every active download. This store lets that
 * shared factory look up the right headers for each download's segment/manifest requests by URL at
 * request time, and survives process death since Media3's `DownloadService` can resume downloads
 * after the app process restarts.
 */
internal object OfflineStreamHeaders {
    private const val PREFS_NAME = "hibiki_offline_download_headers"
    private const val ORIGIN_KEY_PREFIX = "origin:"

    fun save(context: Context, url: String, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        val encoded = JSONObject(headers).toString()
        val editor = prefs(context).edit().putString(url, encoded)
        originKeyOf(url)?.let { originKey -> editor.putString(originKey, encoded) }
        editor.apply()
    }

    fun get(context: Context, url: String): Map<String, String> {
        val preferences = prefs(context)
        val raw = preferences.getString(url, null)
            ?: originKeyOf(url)?.let { originKey -> preferences.getString(originKey, null) }
            ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.getString(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    fun remove(context: Context, url: String) {
        val editor = prefs(context).edit().remove(url)
        originKeyOf(url)?.let(editor::remove)
        editor.apply()
    }

    private fun originKeyOf(url: String): String? {
        val uri = Uri.parse(url)
        val host = uri.host?.takeIf(String::isNotBlank) ?: return null
        val scheme = uri.scheme?.takeIf(String::isNotBlank) ?: return null
        val port = uri.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()
        return "$ORIGIN_KEY_PREFIX$scheme://$host$port"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
