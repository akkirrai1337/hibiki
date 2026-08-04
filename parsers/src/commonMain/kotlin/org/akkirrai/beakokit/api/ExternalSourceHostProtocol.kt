package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Versioned operations that an external source may request from the application host. */
@Serializable
enum class ExternalSourceHostOperation {
    HTTP_REQUEST,
    STORAGE_READ,
    STORAGE_WRITE,
    STORAGE_REMOVE,
    COOKIES_FOR_URL,
    COOKIES_STORE_RESPONSE,
    COOKIES_CLEAR,
    CONFIG_VALUE,
    CONFIG_SECRET,
}

@Serializable
enum class ExternalSourceHostErrorCode {
    INVALID_REQUEST,
    HOST_ACCESS_DENIED,
    HOST_FAILURE,
    CANCELLED,
}

/** Wire envelope sent by a Wasm source to the host callback. */
@Serializable
data class ExternalSourceHostRequest(
    val requestId: String,
    val operation: ExternalSourceHostOperation,
    val payload: JsonObject,
    val protocolVersion: Int = EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION,
) {
    init {
        require(requestId.isNotBlank()) { "Host request ID must not be blank" }
        require('\r' !in requestId && '\n' !in requestId) {
            "Host request ID must not contain CR or LF"
        }
        require(protocolVersion == EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION) {
            "Unsupported host protocol version: $protocolVersion"
        }
    }
}

/** Wire envelope returned by the application host callback. */
@Serializable
data class ExternalSourceHostResponse(
    val requestId: String,
    val payload: JsonObject? = null,
    val errorCode: ExternalSourceHostErrorCode? = null,
    val errorMessage: String? = null,
    val protocolVersion: Int = EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION,
) {
    init {
        require(requestId.isNotBlank()) { "Host response ID must not be blank" }
        require('\r' !in requestId && '\n' !in requestId) {
            "Host response ID must not contain CR or LF"
        }
        require(protocolVersion == EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION) {
            "Unsupported host protocol version: $protocolVersion"
        }
        require((payload == null) != (errorCode == null)) {
            "Host response must contain either payload or error"
        }
        require(errorMessage == null || errorMessage.length <= MAX_ERROR_MESSAGE_LENGTH) {
            "Host error message must be at most $MAX_ERROR_MESSAGE_LENGTH characters"
        }
        if (errorCode == null) require(errorMessage == null) {
            "Successful host response must not contain an error message"
        }
        require(errorMessage == null || ('\r' !in errorMessage && '\n' !in errorMessage)) {
            "Host error message must not contain CR or LF"
        }
    }

    fun requirePayload(expectedRequestId: String): JsonObject {
        require(requestId == expectedRequestId) { "Host response request ID does not match" }
        return payload ?: error(
            errorMessage?.takeIf(String::isNotBlank)
                ?: "Host request failed with ${errorCode?.name}",
        )
    }

    companion object {
        const val MAX_ERROR_MESSAGE_LENGTH: Int = 4 * 1024
    }
}

@Serializable
data class ExternalSourceHostHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMillis: Long = SourceHostHttpRequest.DEFAULT_TIMEOUT_MILLIS,
    val maxResponseBytes: Long = SourceHostHttpRequest.DEFAULT_MAX_RESPONSE_BYTES,
) {
    init {
        require(method.isNotBlank()) { "HTTP method must not be blank" }
        require(url.isNotBlank()) { "HTTP URL must not be blank" }
        requireHttpToken(method, "HTTP method")
        requireSafeHttpField(url, "HTTP URL")
        requireSafeHttpHeaders(headers)
        require(timeoutMillis > 0) { "HTTP timeout must be positive" }
        require(timeoutMillis <= SourceHostHttpRequest.MAX_TIMEOUT_MILLIS) {
            "HTTP timeout must not exceed ${SourceHostHttpRequest.MAX_TIMEOUT_MILLIS} ms"
        }
        require(maxResponseBytes > 0) { "Maximum HTTP response size must be positive" }
        require(maxResponseBytes <= SourceHostHttpRequest.MAX_MAX_RESPONSE_BYTES) {
            "Maximum HTTP response size must not exceed ${SourceHostHttpRequest.MAX_MAX_RESPONSE_BYTES} bytes"
        }
    }
}

@Serializable
data class ExternalSourceHostHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
) {
    init {
        require(statusCode in 100..599) { "HTTP status code must be between 100 and 599" }
        requireSafeHttpHeaders(headers)
    }
}

@Serializable
data class ExternalSourceHostStorageReadRequest(
    val key: String,
) {
    init { SourceHostStorage.requireKey(key) }
}

@Serializable
data class ExternalSourceHostStorageReadResponse(
    val value: String?,
) {
    init { SourceHostStorage.requireValue(value ?: "") }
}

@Serializable
data class ExternalSourceHostStorageWriteRequest(
    val key: String,
    val value: String,
) {
    init {
        SourceHostStorage.requireKey(key)
        SourceHostStorage.requireValue(value)
    }
}

@Serializable
data class ExternalSourceHostStorageRemoveRequest(
    val key: String,
) {
    init { SourceHostStorage.requireKey(key) }
}

@Serializable
data class ExternalSourceHostStorageMutationResponse(
    val success: Boolean = true,
)

@Serializable
data class ExternalSourceHostCookiesForUrlRequest(
    val url: String,
) {
    init { SourceHostCookies.requireUrl(url) }
}

@Serializable
data class ExternalSourceHostCookiesForUrlResponse(
    val cookies: Map<String, String> = emptyMap(),
) {
    init { SourceHostCookies.requireCookies(cookies) }
}

@Serializable
data class ExternalSourceHostCookiesStoreResponseRequest(
    val url: String,
    val cookies: Map<String, String>,
) {
    init {
        SourceHostCookies.requireUrl(url)
        SourceHostCookies.requireCookies(cookies)
    }
}

@Serializable
data class ExternalSourceHostCookiesClearRequest(
    val url: String,
) {
    init { SourceHostCookies.requireUrl(url) }
}

@Serializable
data class ExternalSourceHostConfigRequest(
    val key: String,
) {
    init { SourceHostConfigLimits.requireKey(key) }
}

@Serializable
data class ExternalSourceHostConfigResponse(
    val value: String?,
) {
    init { SourceHostConfigLimits.requireValue(value) }
}

object ExternalSourceHostProtocolCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: ExternalSourceHostRequest): ByteArray =
        json.encodeToString(request).encodeToByteArray().also {
            requireProtocolPayloadSize(it, EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES, "request")
        }

    fun decodeRequest(bytes: ByteArray): ExternalSourceHostRequest {
        requireProtocolPayloadSize(bytes, EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES, "request")
        return json.decodeFromString(bytes.decodeToString())
    }

    fun encodeResponse(response: ExternalSourceHostResponse): ByteArray =
        json.encodeToString(response).encodeToByteArray().also {
            requireProtocolPayloadSize(it, EXTERNAL_SOURCE_HOST_MAX_RESPONSE_BYTES, "response")
        }

    fun decodeResponse(bytes: ByteArray): ExternalSourceHostResponse {
        requireProtocolPayloadSize(bytes, EXTERNAL_SOURCE_HOST_MAX_RESPONSE_BYTES, "response")
        return json.decodeFromString(bytes.decodeToString())
    }

    fun encodeHttpRequest(request: ExternalSourceHostHttpRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeHttpRequest(payload: JsonObject): ExternalSourceHostHttpRequest =
        json.decodeFromJsonElement(payload)

    fun encodeHttpResponse(response: ExternalSourceHostHttpResponse): JsonObject =
        json.encodeToJsonElement(response) as JsonObject

    fun decodeHttpResponse(payload: JsonObject): ExternalSourceHostHttpResponse =
        json.decodeFromJsonElement(payload)

    fun encodeStorageReadRequest(request: ExternalSourceHostStorageReadRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeStorageReadRequest(payload: JsonObject): ExternalSourceHostStorageReadRequest =
        json.decodeFromJsonElement(payload)

    fun encodeStorageReadResponse(response: ExternalSourceHostStorageReadResponse): JsonObject =
        json.encodeToJsonElement(response) as JsonObject

    fun decodeStorageReadResponse(payload: JsonObject): ExternalSourceHostStorageReadResponse =
        json.decodeFromJsonElement(payload)

    fun encodeStorageWriteRequest(request: ExternalSourceHostStorageWriteRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeStorageWriteRequest(payload: JsonObject): ExternalSourceHostStorageWriteRequest =
        json.decodeFromJsonElement(payload)

    fun encodeStorageRemoveRequest(request: ExternalSourceHostStorageRemoveRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeStorageRemoveRequest(payload: JsonObject): ExternalSourceHostStorageRemoveRequest =
        json.decodeFromJsonElement(payload)

    fun encodeStorageMutationResponse(response: ExternalSourceHostStorageMutationResponse): JsonObject =
        json.encodeToJsonElement(response) as JsonObject

    fun decodeStorageMutationResponse(payload: JsonObject): ExternalSourceHostStorageMutationResponse =
        json.decodeFromJsonElement(payload)

    fun encodeCookiesForUrlRequest(request: ExternalSourceHostCookiesForUrlRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeCookiesForUrlRequest(payload: JsonObject): ExternalSourceHostCookiesForUrlRequest =
        json.decodeFromJsonElement(payload)

    fun encodeCookiesForUrlResponse(response: ExternalSourceHostCookiesForUrlResponse): JsonObject =
        json.encodeToJsonElement(response) as JsonObject

    fun decodeCookiesForUrlResponse(payload: JsonObject): ExternalSourceHostCookiesForUrlResponse =
        json.decodeFromJsonElement(payload)

    fun encodeCookiesStoreResponseRequest(
        request: ExternalSourceHostCookiesStoreResponseRequest,
    ): JsonObject = json.encodeToJsonElement(request) as JsonObject

    fun decodeCookiesStoreResponseRequest(
        payload: JsonObject,
    ): ExternalSourceHostCookiesStoreResponseRequest = json.decodeFromJsonElement(payload)

    fun encodeCookiesClearRequest(request: ExternalSourceHostCookiesClearRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeCookiesClearRequest(payload: JsonObject): ExternalSourceHostCookiesClearRequest =
        json.decodeFromJsonElement(payload)

    fun encodeConfigRequest(request: ExternalSourceHostConfigRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeConfigRequest(payload: JsonObject): ExternalSourceHostConfigRequest =
        json.decodeFromJsonElement(payload)

    fun encodeConfigResponse(response: ExternalSourceHostConfigResponse): JsonObject =
        json.encodeToJsonElement(response) as JsonObject

    fun decodeConfigResponse(payload: JsonObject): ExternalSourceHostConfigResponse =
        json.decodeFromJsonElement(payload)
}

private fun requireProtocolPayloadSize(bytes: ByteArray, limit: Long, label: String) {
    require(bytes.size.toLong() <= limit) {
        "External source host $label exceeds $limit bytes"
    }
}

const val EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION: Int = 1
const val EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES: Long = 8L * 1024L * 1024L
const val EXTERNAL_SOURCE_HOST_MAX_RESPONSE_BYTES: Long = 8L * 1024L * 1024L
