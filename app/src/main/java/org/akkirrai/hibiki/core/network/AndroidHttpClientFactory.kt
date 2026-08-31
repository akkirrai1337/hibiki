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
}
