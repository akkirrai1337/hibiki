package org.akkirrai.beakokit.api

/** Runtime-neutral HTTP request exposed by the host. */
data class SourceHostHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
) {
    init {
        require(method.isNotBlank()) { "HTTP method must not be blank" }
        require(url.isNotBlank()) { "HTTP URL must not be blank" }
        requireSafeHttpField(method, "HTTP method")
        requireSafeHttpField(url, "HTTP URL")
        requireSafeHttpHeaders(headers)
        require(timeoutMillis > 0) { "HTTP timeout must be positive" }
        require(timeoutMillis <= MAX_TIMEOUT_MILLIS) {
            "HTTP timeout must not exceed $MAX_TIMEOUT_MILLIS ms"
        }
        require(maxResponseBytes > 0) { "Maximum HTTP response size must be positive" }
        require(maxResponseBytes < Long.MAX_VALUE) {
            "Maximum HTTP response size must leave room for the limit sentinel"
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 30_000
        const val MAX_TIMEOUT_MILLIS: Long = 120_000
        const val DEFAULT_MAX_RESPONSE_BYTES: Long = 8L * 1024L * 1024L
    }
}

internal fun requireSafeHttpField(value: String, label: String) {
    require('\r' !in value && '\n' !in value) {
        "$label must not contain CR or LF"
    }
}

internal fun requireSafeHttpHeaders(headers: Map<String, String>) {
    headers.forEach { (name, value) ->
        require(name.isNotBlank()) { "HTTP header name must not be blank" }
        requireSafeHttpField(name, "HTTP header name")
        requireSafeHttpField(value, "HTTP header value")
    }
}

/** Runtime-neutral HTTP response returned by a host implementation. */
data class SourceHostHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
) {
    init {
        require(statusCode in 100..599) { "HTTP status code must be between 100 and 599" }
    }
}

/** Host HTTP boundary available to external source runtimes. */
abstract class SourceHostHttpClient : SourceHostAccess {
    suspend fun execute(request: SourceHostHttpRequest): SourceHostHttpResponse {
        require(SourceHostCapability.NETWORK)
        requirements.networkPolicy.requireAllowed(request.url)
        val response = executeNetwork(request)
        require(response.body.encodeToByteArray().size.toLong() <= request.maxResponseBytes) {
            "Host HTTP response exceeds ${request.maxResponseBytes} bytes"
        }
        return response
    }

    protected abstract suspend fun executeNetwork(request: SourceHostHttpRequest): SourceHostHttpResponse
}
