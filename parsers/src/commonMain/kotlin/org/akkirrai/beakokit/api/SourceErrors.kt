package org.akkirrai.beakokit.api

enum class SourceErrorKind {
    NETWORK,
    PARSE,
    AUTH,
    NOT_FOUND,
    RATE_LIMITED,
    UNAVAILABLE,
    UNKNOWN,
}

/** Stable machine-readable error codes exposed across built-in and external source runtimes. */
enum class SourceErrorCode(val value: String) {
    NETWORK_FAILURE("network_failure"),
    INVALID_RESPONSE("invalid_response"),
    AUTHENTICATION_REQUIRED("authentication_required"),
    NOT_FOUND("not_found"),
    RATE_LIMITED("rate_limited"),
    UNAVAILABLE("unavailable"),
    INVALID_REQUEST("invalid_request"),
    UNSUPPORTED_OPERATION("unsupported_operation"),
    CONFIGURATION_REQUIRED("configuration_required"),
    HOST_ACCESS_DENIED("host_access_denied"),
    SOURCE_FAILURE("source_failure"),
    RUNTIME_FAILURE("runtime_failure"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown"),
}

val SourceErrorKind.defaultCode: SourceErrorCode
    get() = when (this) {
        SourceErrorKind.NETWORK -> SourceErrorCode.NETWORK_FAILURE
        SourceErrorKind.PARSE -> SourceErrorCode.INVALID_RESPONSE
        SourceErrorKind.AUTH -> SourceErrorCode.AUTHENTICATION_REQUIRED
        SourceErrorKind.NOT_FOUND -> SourceErrorCode.NOT_FOUND
        SourceErrorKind.RATE_LIMITED -> SourceErrorCode.RATE_LIMITED
        SourceErrorKind.UNAVAILABLE -> SourceErrorCode.UNAVAILABLE
        SourceErrorKind.UNKNOWN -> SourceErrorCode.UNKNOWN
    }

open class SourceException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    val kind: SourceErrorKind = SourceErrorKind.UNKNOWN,
    val code: SourceErrorCode = kind.defaultCode,
) : RuntimeException(message, cause)

open class SourceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : SourceException(
    message = message,
    cause = cause,
    kind = SourceErrorKind.UNAVAILABLE,
)
