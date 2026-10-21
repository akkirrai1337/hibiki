package app.hibiki.apk

import android.net.Uri

/**
 * A source's pictures go through the host, which fetches them the way the source does: its own
 * headers (User-Agent, Referer) and the shared cookie store, so a site that let the source in
 * serves its posters too. The page asks for [base]; the application maps that address to this
 * process (see Server.kt, which does the fetching).
 */
object ApkImageProxy {
    /** Where the page loads pictures from; the application tells the host at start what its scheme is. */
    @Volatile
    var base: String = "/_hibiki/image"

    /** The proxied form of [url] for the page, or [url] itself when it is not a web address. */
    fun wrap(sourceId: String, url: String?): String? {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return url
        return "$base?s=${Uri.encode(sourceId)}&u=${Uri.encode(url)}"
    }
}
