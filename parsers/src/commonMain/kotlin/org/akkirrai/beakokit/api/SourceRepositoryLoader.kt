package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Limits applied while loading one repository index. */
data class SourceRepositoryLoadLimits(
    val timeoutMillis: Long = 15_000,
    val maxResponseBytes: Long = 2L * 1024L * 1024L,
) {
    init {
        require(timeoutMillis > 0) { "Repository timeout must be positive" }
        require(timeoutMillis <= SourceHostHttpRequest.MAX_TIMEOUT_MILLIS) {
            "Repository timeout must not exceed ${SourceHostHttpRequest.MAX_TIMEOUT_MILLIS} ms"
        }
        require(maxResponseBytes > 0) { "Maximum repository response size must be positive" }
        require(maxResponseBytes < Long.MAX_VALUE) {
            "Maximum repository response size must leave room for the limit sentinel"
        }
    }
}

data class SourceRepositoryResponse(
    val statusCode: Int,
    val body: String,
) {
    init {
        require(statusCode in 100..599) { "Repository HTTP status code must be between 100 and 599" }
    }
}

/** Host-owned transport used to fetch repository metadata before package installation. */
fun interface SourceRepositoryTransport {
    suspend fun get(
        url: String,
        limits: SourceRepositoryLoadLimits,
    ): SourceRepositoryResponse
}

class SourceRepositoryLoader(
    private val transport: SourceRepositoryTransport,
    private val limits: SourceRepositoryLoadLimits = SourceRepositoryLoadLimits(),
) {
    suspend fun load(
        url: String,
        clientVersion: Int,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): SourceRepositoryIndex {
        if (!isValidHttpsUrl(url)) {
            throw SourceRepositoryLoadException(
                message = "Repository URL must be a valid HTTPS URL",
                kind = SourceErrorKind.PARSE,
                code = SourceErrorCode.INVALID_REQUEST,
            )
        }

        val response = try {
            withTimeout(limits.timeoutMillis) {
                transport.get(url, limits)
            }
        } catch (error: TimeoutCancellationException) {
            throw SourceRepositoryLoadException(
                message = "Repository request timed out after ${limits.timeoutMillis} ms",
                cause = error,
                kind = SourceErrorKind.UNAVAILABLE,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceRepositoryLoadException) {
            throw error
        } catch (error: Throwable) {
            throw SourceRepositoryLoadException(
                message = "Repository request failed",
                cause = error,
                kind = SourceErrorKind.NETWORK,
            )
        }

        if (response.statusCode !in 200..299) {
            throw SourceRepositoryLoadException(
                message = "Repository request returned HTTP ${response.statusCode}",
                statusCode = response.statusCode,
                kind = SourceErrorKind.UNAVAILABLE,
            )
        }
        if (response.body.encodeToByteArray().size.toLong() > limits.maxResponseBytes) {
            throw SourceRepositoryLoadException(
                message = "Repository response exceeds ${limits.maxResponseBytes} bytes",
                kind = SourceErrorKind.UNAVAILABLE,
            )
        }

        return try {
            SourceRepositoryIndexCodec.decodeAndValidate(
                value = response.body,
                clientVersion = clientVersion,
                supportedSourceApiVersion = supportedSourceApiVersion,
                supportedHostApiVersion = supportedHostApiVersion,
            )
        } catch (error: SourceRepositoryLoadException) {
            throw error
        } catch (error: SourceRepositoryIndexException) {
            throw SourceRepositoryLoadException(
                message = error.message ?: "Repository index is invalid",
                cause = error,
                kind = SourceErrorKind.PARSE,
                code = SourceErrorCode.INVALID_RESPONSE,
            )
        }
    }
}

class SourceRepositoryLoadException(
    message: String,
    statusCode: Int? = null,
    cause: Throwable? = null,
    kind: SourceErrorKind,
    code: SourceErrorCode = kind.defaultCode,
) : SourceException(message, statusCode, cause, kind, code)
