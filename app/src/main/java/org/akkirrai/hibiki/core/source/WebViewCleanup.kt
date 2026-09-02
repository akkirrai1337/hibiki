package org.akkirrai.hibiki.core.source

import android.webkit.WebStorage
import android.webkit.WebView

/**
 * These extractor/relay WebViews are throwaway - created per stream resolution, never reused for
 * a login or user session. `destroy()` alone only frees the Java/native objects; the HTTP cache,
 * Service Worker cache and IndexedDB it wrote to `app_webview/` on disk survive it and accumulate
 * across every site/CDN the extractors visit, unbounded. Cookies are deliberately left alone -
 * they carry bot-management clearance (e.g. Cloudflare) that's worth keeping across calls, unlike
 * [org.akkirrai.hibiki.core.discord.DiscordAuthActivity]'s own cleanup which clears everything
 * because that flow is done with the session entirely.
 *
 * Must be called on the main thread, same as [WebView.destroy] itself.
 */
internal fun WebView.destroyAndClearData() {
    clearCache(true)
    WebStorage.getInstance().deleteAllData()
    destroy()
}
