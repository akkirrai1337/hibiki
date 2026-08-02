package org.akkirrai.beakokit.api

import kotlinx.serialization.json.JsonObject
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle

/** Platform transport for one request/response exchange with an external runtime. */
fun interface ExternalSourceRuntimeTransport {
    suspend fun call(request: ExternalSourceRuntimeRequest): ExternalSourceRuntimeResponse
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
        val response = transport.call(request)
        if (response.requestId != request.requestId) {
            throw SourceException(
                message = "Runtime response ID does not match request",
                kind = SourceErrorKind.PARSE,
            )
        }
        return decode(response.requirePayload())
    }
}
