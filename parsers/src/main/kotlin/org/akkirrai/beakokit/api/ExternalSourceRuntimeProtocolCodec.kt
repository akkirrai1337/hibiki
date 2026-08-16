package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Strict UTF-8 JSON codec shared by native runtime bridges on every platform. */
object ExternalSourceRuntimeProtocolCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: ExternalSourceRuntimeRequest): ByteArray =
        json.encodeToString(request).encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): ExternalSourceRuntimeRequest = try {
        json.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))
    } catch (error: SourceException) {
        throw error
    } catch (error: Exception) {
        throw SourceException(
            message = "Native runtime request is invalid",
            cause = error,
            kind = SourceErrorKind.PARSE,
            code = SourceErrorCode.INVALID_REQUEST,
        )
    }

    fun encodeResponse(response: ExternalSourceRuntimeResponse): ByteArray =
        json.encodeToString(response).encodeToByteArray()

    fun decodeResponse(bytes: ByteArray): ExternalSourceRuntimeResponse = try {
        json.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))
    } catch (error: SourceException) {
        throw error
    } catch (error: Exception) {
        throw SourceException(
            message = "Native runtime response is invalid",
            cause = error,
            kind = SourceErrorKind.PARSE,
            code = SourceErrorCode.INVALID_RESPONSE,
        )
    }
}

/** Lowest-level platform bridge for one already serialized runtime request. */
fun interface ExternalSourceRuntimeNativeBridge {
    suspend fun call(
        request: ByteArray,
        maxResponseBytes: Long,
    ): ByteArray
}

/** Adapts a JNI/Obj-C/Swift byte bridge to the common runtime transport contract. */
class NativeBridgeExternalSourceRuntimeTransport(
    private val bridge: ExternalSourceRuntimeNativeBridge,
    private val logger: SourceLogger = SourceLogger.NONE,
) : ExternalSourceRuntimeTransport {
    // A bridge owns one WASM/Wasmtime instance. Wasmtime calls are not re-entrant;
    // concurrent catalog requests must queue instead of corrupting the instance.
    private val callMutex = Mutex()

    override suspend fun call(
        request: ExternalSourceRuntimeRequest,
        limits: ExternalSourceRuntimeCallLimits,
    ): ExternalSourceRuntimeResponse = callMutex.withLock {
        val response = try {
            bridge.call(
                request = ExternalSourceRuntimeProtocolCodec.encodeRequest(request).also { requestBytes ->
                    if (requestBytes.size.toLong() > limits.maxRequestBytes) {
                        throw SourceException(
                            message = "Native runtime request exceeds ${limits.maxRequestBytes} bytes",
                            kind = SourceErrorKind.PARSE,
                            code = SourceErrorCode.INVALID_REQUEST,
                        )
                    }
                },
                maxResponseBytes = limits.maxResponseBytes,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceException) {
            throw error
        } catch (error: Exception) {
            throw SourceException(
                message = "Native runtime bridge failed",
                cause = error,
                kind = SourceErrorKind.UNKNOWN,
                code = SourceErrorCode.RUNTIME_FAILURE,
            )
        }
        if (response.size.toLong() > limits.maxResponseBytes) {
            throw SourceUnavailableException(
                message = "Native runtime response exceeds ${limits.maxResponseBytes} bytes",
            )
        }
        return try {
            ExternalSourceRuntimeProtocolCodec.decodeResponse(response)
        } catch (error: SourceException) {
            logger.log(SourceLogLevel.ERROR, "External runtime response decode failed", error)
            throw error
        } catch (error: Exception) {
            logger.log(SourceLogLevel.ERROR, "External runtime response decode failed", error)
            throw SourceException(
                message = "Native runtime response is invalid",
                cause = error,
                kind = SourceErrorKind.PARSE,
                code = SourceErrorCode.INVALID_RESPONSE,
            )
        }.also { runtimeResponse ->
            if (runtimeResponse.errorCode != null) {
                logger.log(
                    SourceLogLevel.ERROR,
                    "External runtime ${request.operation} failed: " +
                        "${runtimeResponse.errorCode}: ${runtimeResponse.errorMessage ?: "no error message"}",
                    null,
                )
            }
        }
    }
}
