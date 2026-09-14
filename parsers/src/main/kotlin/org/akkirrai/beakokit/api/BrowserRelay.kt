package org.akkirrai.beakokit.api

import java.net.URI

/**
 * A batch of URLs a resolver wants relayed through a real, persistent browser network stack rather
 * than fetched once - for continuous HLS playback (a manifest plus its segments and subtitles, all
 * pulled over time by the player, not by the resolver itself) against a CDN that TLS/JA3-fingerprints
 * or otherwise blocks a plain HTTP client outright. Unlike [BrowserFetchProvider] (one request, one
 * response, answered immediately), this hands back proxy URLs the resolver can put straight into the
 * video stream/subtitle URLs it returns - the host keeps relaying every request against those proxy
 * URLs for as long as the relay session stays alive, the same way
 * `BrowserPlayerWebViewExtractor`'s own WebView-backed relay already does for BROWSER-runtime
 * resolvers, just made available to HTTP-runtime resolvers that already know the real URLs and only
 * need a browser to be believed by the CDN.
 */
data class BrowserRelayRequest(
    val pageUrl: String,
    val urls: List<String>,
    val headers: Map<String, String> = emptyMap(),
) {
    init {
        val uri = runCatching { URI(pageUrl) }.getOrNull()
        require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            "pageUrl must be an absolute HTTPS URL"
        }
        require(urls.isNotEmpty()) { "At least one URL is required to relay" }
    }
}

/** [urls] maps each of [BrowserRelayRequest.urls] to the proxy URL that relays it. */
data class BrowserRelayResult(
    val urls: Map<String, String>,
)

fun interface BrowserRelayProvider {
    suspend fun relay(request: BrowserRelayRequest): BrowserRelayResult

    companion object {
        val UNSUPPORTED = BrowserRelayProvider {
            throw SourceUnavailableException(
                "This resolver requires a persistent browser relay, but the host does not provide one",
            )
        }
    }
}
