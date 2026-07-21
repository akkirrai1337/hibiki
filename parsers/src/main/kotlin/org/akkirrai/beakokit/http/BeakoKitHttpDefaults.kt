package org.akkirrai.beakokit.http

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

/** Shared, engine-agnostic network policy for hosts that run BeakoKit sources. */
data class BeakoKitHttpPolicy(
    val userAgent: String = "BeakoKit/1.0",
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 30_000,
    val socketTimeoutMillis: Long = 30_000,
    val maxRetries: Int = 2,
    val maxRetryAfterMillis: Long = 30_000,
) {
    init {
        require(userAgent.isNotBlank()) { "User agent must not be blank" }
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive" }
        require(requestTimeoutMillis > 0) { "Request timeout must be positive" }
        require(socketTimeoutMillis > 0) { "Socket timeout must be positive" }
        require(maxRetries >= 0) { "Max retries must not be negative" }
        require(maxRetryAfterMillis >= 0) { "Maximum Retry-After must not be negative" }
    }
}

/**
 * Installs BeakoKit defaults without selecting an engine, keeping the framework usable outside Android.
 * Only idempotent requests are retried; a server-provided Retry-After is honoured with a bounded delay.
 */
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installBeakoKitHttpDefaults(
    policy: BeakoKitHttpPolicy = BeakoKitHttpPolicy(),
) {
    expectSuccess = false

    install(UserAgent) {
        agent = policy.userAgent
    }
    install(HttpTimeout) {
        connectTimeoutMillis = policy.connectTimeoutMillis
        requestTimeoutMillis = policy.requestTimeoutMillis
        socketTimeoutMillis = policy.socketTimeoutMillis
    }
    install(HttpRequestRetry) {
        maxRetries = policy.maxRetries
        retryIf { request, response ->
            request.isSafeToRetry() &&
                (response.status.value == 429 || response.status.value in 500..599)
        }
        retryOnExceptionIf { request, cause ->
            request.isSafeToRetry() && cause is IOException
        }
        delayMillis { retry ->
            response?.headers?.get(HttpHeaders.RetryAfter)
                ?.toLongOrNull()
                ?.times(1_000)
                ?.coerceIn(0, policy.maxRetryAfterMillis)
                ?: (1_000L shl (retry - 1).coerceIn(0, 5))
        }
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}

private fun HttpRequestBuilder.isSafeToRetry(): Boolean = method in SAFE_RETRY_METHODS

private fun HttpRequest.isSafeToRetry(): Boolean = method in SAFE_RETRY_METHODS

private val SAFE_RETRY_METHODS = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)
