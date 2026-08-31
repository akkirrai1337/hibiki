package org.akkirrai.beakokit.api

import java.net.URI

/**
 * A single request a source wants performed from inside a real browser page's own JS context
 * instead of a plain HTTP client - for a site whose bot-management binds a solved challenge to the
 * exact client that solved it (TLS/JA3 fingerprint, not just a cookie), a cookie harvested from a
 * WebView and replayed through a separate HTTP client can still be rejected. The host loads
 * [pageUrl] in a WebView, then runs `fetch([targetUrl], ...)` from within that loaded page and
 * returns the result - a genuine same-stack request, not a replay.
 */
data class BrowserFetchRequest(
    val pageUrl: String,
    val targetUrl: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
) {
    init {
        listOf("pageUrl" to pageUrl, "targetUrl" to targetUrl).forEach { (label, url) ->
            val uri = runCatching { URI(url) }.getOrNull()
            require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
                "$label must be an absolute HTTPS URL"
            }
        }
    }
}

data class BrowserFetchResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

fun interface BrowserFetchProvider {
    suspend fun fetch(request: BrowserFetchRequest): BrowserFetchResponse

    companion object {
        val UNSUPPORTED = BrowserFetchProvider {
            throw SourceUnavailableException(
                "This source requires an in-page browser fetch, but the host does not provide one",
            )
        }
    }
}
