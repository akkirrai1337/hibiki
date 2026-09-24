package org.akkirrai.beakokit.extension

import org.akkirrai.beakokit.model.PlayerLink

/** How a browser-resolved player link is played: extracted for ExoPlayer, or shown as the page itself. */
enum class BrowserPlaybackMode { EXTRACT_STREAM, PAGE }

/** Optional capability of a player resolver that supplies a browser-page script. */
interface BrowserScriptResolver {
    fun supportsBrowser(link: PlayerLink): Boolean
    suspend fun browserScript(link: PlayerLink): String
    val browserPlaybackMode: BrowserPlaybackMode get() = BrowserPlaybackMode.EXTRACT_STREAM
    val requiresFrame: Boolean get() = false
}
