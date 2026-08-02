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
        require(protocolVersion == EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION) {
            "Unsupported host protocol version: $protocolVersion"
        }
        require((payload == null) != (errorCode == null)) {
            "Host response must contain either payload or error"
        }
        if (errorCode == null) require(errorMessage == null) {
            "Successful host response must not contain an error message"
        }
    }

    fun requirePayload(expectedRequestId: String): JsonObject {
        require(requestId == expectedRequestId) { "Host response request ID does not match" }
        return payload ?: error(
            errorMessage?.takeIf(String::isNotBlank)
                ?: "Host request failed with ${errorCode?.name}",
        )
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
)

@Serializable
data class ExternalSourceHostHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
)

object ExternalSourceHostProtocolCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: ExternalSourceHostRequest): ByteArray =
        json.encodeToString(request).encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): ExternalSourceHostRequest =
        json.decodeFromString(bytes.decodeToString())

    fun encodeResponse(response: ExternalSourceHostResponse): ByteArray =
        json.encodeToString(response).encodeToByteArray()

    fun decodeResponse(bytes: ByteArray): ExternalSourceHostResponse =
        json.decodeFromString(bytes.decodeToString())

    fun encodeHttpRequest(request: ExternalSourceHostHttpRequest): JsonObject =
        json.encodeToJsonElement(request) as JsonObject

    fun decodeHttpRequest(payload: JsonObject): ExternalSourceHostHttpRequest =
        json.decodeFromJsonElement(payload)

    fun encodeHttpResponse(response: ExternalSourceHostHttpResponse): JsonObject =
        json.encodeToJsonElement(response) as JsonObject

    fun decodeHttpResponse(payload: JsonObject): ExternalSourceHostHttpResponse =
        json.decodeFromJsonElement(payload)
}

const val EXTERNAL_SOURCE_HOST_PROTOCOL_VERSION: Int = 1
