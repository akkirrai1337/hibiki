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

open class SourceException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    val kind: SourceErrorKind = SourceErrorKind.UNKNOWN,
) : RuntimeException(message, cause)

open class SourceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : SourceException(
    message = message,
    cause = cause,
    kind = SourceErrorKind.UNAVAILABLE,
)
