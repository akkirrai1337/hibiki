package org.akkirrai.beakokit.api

import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle

/** Platform transport for one request/response exchange with an external runtime. */
fun interface ExternalSourceRuntimeTransport {
    suspend fun call(
        request: ExternalSourceRuntimeRequest,
        limits: ExternalSourceRuntimeCallLimits,
    ): ExternalSourceRuntimeResponse
}

/** Host-enforced limits for one external runtime call. */
data class ExternalSourceRuntimeCallLimits(
    val timeoutMillis: Long = SourceHostHttpRequest.DEFAULT_TIMEOUT_MILLIS,
    val maxResponseBytes: Long = SourceHostHttpRequest.DEFAULT_MAX_RESPONSE_BYTES,
) {
    init {
        require(timeoutMillis > 0) { "Runtime timeout must be positive" }
        require(maxResponseBytes > 0) { "Maximum runtime response size must be positive" }
    }
}

/** Converts BeakoKit models to and from operation-specific runtime JSON payloads. */
interface ExternalSourceRuntimePayloadCodec {
    fun decodeSearch(payload: JsonObject): List<AnimeTitle>

    fun decodeDetails(payload: JsonObject): AnimeTitle
}

/** Turns the versioned wire exchange into the common external-source runtime contract. */
class ProtocolBackedExternalSourceRuntime(
    private val transport: ExternalSourceRuntimeTransport,
    private val payloadCodec: ExternalSourceRuntimePayloadCodec,
    private val requestIdFactory: () -> String,
    private val callLimits: ExternalSourceRuntimeCallLimits = ExternalSourceRuntimeCallLimits(),
) : ExternalSourceRuntime {
    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = call(
        operation = ExternalSourceRuntimeOperation.SEARCH,
        payload = ExternalSourceRuntimePayloads.search(request),
        decode = payloadCodec::decodeSearch,
    )

    override suspend fun details(id: String): AnimeTitle = call(
        operation = ExternalSourceRuntimeOperation.DETAILS,
        payload = ExternalSourceRuntimePayloads.details(id),
        decode = payloadCodec::decodeDetails,
    )

    private suspend fun <T> call(
        operation: ExternalSourceRuntimeOperation,
        payload: JsonObject,
        decode: (JsonObject) -> T,
    ): T {
        val request = ExternalSourceRuntimeRequest(
            requestId = requestIdFactory(),
            operation = operation,
            payload = payload,
        )
        val response = transport.call(request, callLimits)
        if (response.requestId != request.requestId) {
            throw SourceException(
                message = "Runtime response ID does not match request",
                kind = SourceErrorKind.PARSE,
            )
        }
        return try {
            decode(response.requirePayload())
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceException) {
            throw error
        } catch (error: Throwable) {
            throw SourceException(
                message = "External source runtime returned an invalid payload",
                cause = error,
                kind = SourceErrorKind.PARSE,
            )
        }
    }
}
