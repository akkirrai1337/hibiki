package org.akkirrai.hibiki.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults

object AndroidHttpClientFactory {
    fun create(): HttpClient = HttpClient(OkHttp) {
        installBeakoKitHttpDefaults(BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Android"))
    }
}
