package org.akkirrai.hibiki.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object AndroidHttpClientFactory {
    fun create(): HttpClient = HttpClient(Android) {
        expectSuccess = false

        install(UserAgent) {
            agent = "Hibiki/0.1 Android"
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, response ->
                response.status == HttpStatusCode.TooManyRequests ||
                    response.status.value in 500..599
            }
            retryOnExceptionIf { _, cause -> cause is java.io.IOException }
            exponentialDelay()
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
    }
}
