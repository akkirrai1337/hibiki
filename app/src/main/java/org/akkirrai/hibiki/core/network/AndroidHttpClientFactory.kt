package org.akkirrai.hibiki.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults

object AndroidHttpClientFactory {
    // A self-identifying UA ("Hibiki/0.1 Android") is an obvious non-browser signature that some
    // sources' own bot-management rejects outright (confirmed against anichi.to: the identical
    // request 500s every time through this client's old default, but 200s through a plain client
    // sending a normal desktop-Chrome UA). Defaulting to a real browser UA here means every current
    // and future extension gets this for free instead of each one having to override it itself.
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun create(): HttpClient = HttpClient(OkHttp) {
        installBeakoKitHttpDefaults(BeakoKitHttpPolicy(userAgent = DEFAULT_USER_AGENT))
    }

    /**
     * For the metadata aggregators only. No retries: the shared client's retry sat inside
     * MetadataRequestQueue's request, so a 429 from Jikan was slept on (Retry-After, up to
     * 30s, twice) instead of reaching the queue's stand-down, while the queue kept admitting more
     * requests into the same throttle - and each such request held a card-match slot for up to a
     * minute. Short timeouts for the same reason: a lookup that slow is worth less than the source's
     * own description.
     */
    fun createMetadata(): HttpClient = HttpClient(OkHttp) {
        installBeakoKitHttpDefaults(
            BeakoKitHttpPolicy(
                userAgent = DEFAULT_USER_AGENT,
                connectTimeoutMillis = 5_000,
                requestTimeoutMillis = 10_000,
                socketTimeoutMillis = 10_000,
                maxRetries = 0,
            ),
        )
    }
}
