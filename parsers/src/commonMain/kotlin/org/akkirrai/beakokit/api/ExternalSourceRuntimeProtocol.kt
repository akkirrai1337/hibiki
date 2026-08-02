package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Stable wire operations exposed by an external-source runtime. */
@Serializable
enum class ExternalSourceRuntimeOperation {
    SEARCH,
    DETAILS,
}

/** Errors that can cross the runtime boundary without exposing platform exceptions. */
@Serializable
enum class ExternalSourceRuntimeErrorCode {
    INVALID_REQUEST,
    HOST_ACCESS_DENIED,
    SOURCE_FAILURE,
    RUNTIME_FAILURE,
    CANCELLED,
}

/**
 * Versioned JSON envelope sent from the host to an external source runtime.
 *
 * `payload` is operation-specific and deliberately stays a JSON object so the native bridge
 * does not depend on Kotlin/JVM model classes. The payload schema is versioned together with
 * [PROTOCOL_VERSION].
 */
@Serializable
data class ExternalSourceRuntimeRequest(
    val requestId: String,
    val operation: ExternalSourceRuntimeOperation,
    val payload: JsonObject,
    val protocolVersion: Int = PROTOCOL_VERSION,
) {
    init {
        require(requestId.isNotBlank()) { "Runtime request ID must not be blank" }
        require(protocolVersion == PROTOCOL_VERSION) {
            "Unsupported runtime protocol version: $protocolVersion"
        }
    }
}

/** Versioned JSON envelope returned by an external source runtime. */
@Serializable
data class ExternalSourceRuntimeResponse(
    val requestId: String,
    val payload: JsonObject? = null,
    val errorCode: ExternalSourceRuntimeErrorCode? = null,
    val errorMessage: String? = null,
    val protocolVersion: Int = PROTOCOL_VERSION,
) {
    init {
        require(requestId.isNotBlank()) { "Runtime response ID must not be blank" }
        require(protocolVersion == PROTOCOL_VERSION) {
            "Unsupported runtime protocol version: $protocolVersion"
        }
        require((payload == null) != (errorCode == null)) {
            "Runtime response must contain either payload or error"
        }
        if (errorCode == null) require(errorMessage == null) {
            "Successful runtime response must not contain an error message"
        }
    }
}

const val EXTERNAL_SOURCE_RUNTIME_PROTOCOL_VERSION: Int = 1

private const val PROTOCOL_VERSION: Int = EXTERNAL_SOURCE_RUNTIME_PROTOCOL_VERSION
