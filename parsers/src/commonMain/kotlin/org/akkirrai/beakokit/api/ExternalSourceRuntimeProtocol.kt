package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException

/** Stable wire operations exposed by an external-source runtime. */
@Serializable
enum class ExternalSourceRuntimeOperation {
    SEARCH,
    DETAILS,
    LATEST,
    PLAYBACK_GROUPS,
    PLAYER_LINKS,
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

fun ExternalSourceRuntimeResponse.requirePayload(): JsonObject = when {
    payload != null -> payload
    errorCode == ExternalSourceRuntimeErrorCode.CANCELLED -> throw CancellationException(
        errorMessage?.takeIf(String::isNotBlank) ?: "External source runtime call was cancelled",
    )
    else -> throw toSourceException()
}

fun ExternalSourceRuntimeResponse.toSourceException(): SourceException {
    val protocolError = errorCode
        ?: return SourceException(
            message = "Runtime response does not contain a payload",
            kind = SourceErrorKind.PARSE,
        )
    val message = errorMessage?.takeIf(String::isNotBlank)
        ?: "External source runtime failed with ${protocolError.name}"
    return SourceException(
        message = message,
        kind = protocolError.sourceErrorKind,
        code = protocolError.sourceErrorCode,
    )
}

private val ExternalSourceRuntimeErrorCode.sourceErrorKind: SourceErrorKind
    get() = when (this) {
        ExternalSourceRuntimeErrorCode.INVALID_REQUEST -> SourceErrorKind.PARSE
        ExternalSourceRuntimeErrorCode.HOST_ACCESS_DENIED -> SourceErrorKind.UNAVAILABLE
        ExternalSourceRuntimeErrorCode.SOURCE_FAILURE -> SourceErrorKind.UNKNOWN
        ExternalSourceRuntimeErrorCode.RUNTIME_FAILURE -> SourceErrorKind.UNKNOWN
        ExternalSourceRuntimeErrorCode.CANCELLED -> SourceErrorKind.UNKNOWN
    }

private val ExternalSourceRuntimeErrorCode.sourceErrorCode: SourceErrorCode
    get() = when (this) {
        ExternalSourceRuntimeErrorCode.INVALID_REQUEST -> SourceErrorCode.INVALID_REQUEST
        ExternalSourceRuntimeErrorCode.HOST_ACCESS_DENIED -> SourceErrorCode.HOST_ACCESS_DENIED
        ExternalSourceRuntimeErrorCode.SOURCE_FAILURE -> SourceErrorCode.SOURCE_FAILURE
        ExternalSourceRuntimeErrorCode.RUNTIME_FAILURE -> SourceErrorCode.RUNTIME_FAILURE
        ExternalSourceRuntimeErrorCode.CANCELLED -> SourceErrorCode.CANCELLED
    }

const val EXTERNAL_SOURCE_RUNTIME_PROTOCOL_VERSION: Int = 1

private const val PROTOCOL_VERSION: Int = EXTERNAL_SOURCE_RUNTIME_PROTOCOL_VERSION
