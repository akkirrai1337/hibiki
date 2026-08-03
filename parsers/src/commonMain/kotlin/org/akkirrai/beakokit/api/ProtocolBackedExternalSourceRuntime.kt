package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink

/**
 * Platform transport for one request/response exchange with an external runtime.
 *
 * Implementations must reject an oversized native response before deserializing it. The common
 * adapter applies the timeout and validates the decoded JSON payload size as a second boundary.
 */
fun interface ExternalSourceRuntimeTransport {
    suspend fun call(
        request: ExternalSourceRuntimeRequest,
        limits: ExternalSourceRuntimeCallLimits,
    ): ExternalSourceRuntimeResponse
}

/** Host-enforced limits for one external runtime call. */
data class ExternalSourceRuntimeCallLimits(
    val timeoutMillis: Long = SourceHostHttpRequest.DEFAULT_TIMEOUT_MILLIS,
    val maxRequestBytes: Long = SourceHostHttpRequest.DEFAULT_MAX_RESPONSE_BYTES,
    val maxResponseBytes: Long = SourceHostHttpRequest.DEFAULT_MAX_RESPONSE_BYTES,
) {
    init {
        require(timeoutMillis > 0) { "Runtime timeout must be positive" }
        require(timeoutMillis <= SourceHostHttpRequest.MAX_TIMEOUT_MILLIS) {
            "Runtime timeout must not exceed ${SourceHostHttpRequest.MAX_TIMEOUT_MILLIS} ms"
        }
        require(maxRequestBytes > 0) { "Maximum runtime request size must be positive" }
        require(maxResponseBytes > 0) { "Maximum runtime response size must be positive" }
    }
}

/** Converts BeakoKit models to and from operation-specific runtime JSON payloads. */
interface ExternalSourceRuntimePayloadCodec {
    fun decodeSearch(payload: JsonObject): List<AnimeTitle>

    fun decodeDetails(payload: JsonObject): AnimeTitle
}

/** Response decoder for the optional playback operations. */
interface ExternalSourcePlaybackRuntimePayloadCodec : ExternalSourceRuntimePayloadCodec {
    fun decodePlaybackGroups(payload: JsonObject): List<PlaybackGroup>

    fun decodePlayerLinks(payload: JsonObject): List<PlayerLink>
}

/** Turns the versioned wire exchange into the common external-source runtime contract. */
open class ProtocolBackedExternalSourceRuntime(
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

    protected suspend fun <T> call(
        operation: ExternalSourceRuntimeOperation,
        payload: JsonObject,
        decode: (JsonObject) -> T,
    ): T {
        val request = ExternalSourceRuntimeRequest(
            requestId = requestIdFactory(),
            operation = operation,
            payload = payload,
        )
        val response = try {
            withTimeout(callLimits.timeoutMillis) {
                transport.call(request, callLimits)
            }
        } catch (error: TimeoutCancellationException) {
            throw SourceUnavailableException(
                message = "External source runtime timed out after ${callLimits.timeoutMillis} ms",
                cause = error,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceException) {
            throw error
        } catch (error: Throwable) {
            throw SourceException(
                message = "External source runtime transport failed",
                cause = error,
                kind = SourceErrorKind.UNAVAILABLE,
                code = SourceErrorCode.RUNTIME_FAILURE,
            )
        }
        if (response.requestId != request.requestId) {
            throw SourceException(
                message = "Runtime response ID does not match request",
                kind = SourceErrorKind.PARSE,
            )
        }
        return try {
            val responsePayload = response.requirePayload()
            requireResponseWithinLimit(responsePayload)
            decode(responsePayload)
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

    private fun requireResponseWithinLimit(payload: JsonObject) {
        val payloadSizeBytes = payload.toString().encodeToByteArray().size.toLong()
        if (payloadSizeBytes > callLimits.maxResponseBytes) {
            throw SourceUnavailableException(
                message = "External source runtime response exceeds ${callLimits.maxResponseBytes} bytes",
            )
        }
    }
}

/** Protocol-backed runtime that exposes the optional playback contract. */
class ProtocolBackedExternalSourcePlaybackRuntime(
    transport: ExternalSourceRuntimeTransport,
    private val payloadCodec: ExternalSourcePlaybackRuntimePayloadCodec,
    requestIdFactory: () -> String,
    callLimits: ExternalSourceRuntimeCallLimits = ExternalSourceRuntimeCallLimits(),
) : ProtocolBackedExternalSourceRuntime(
    transport = transport,
    payloadCodec = payloadCodec,
    requestIdFactory = requestIdFactory,
    callLimits = callLimits,
), ExternalSourcePlaybackRuntime {
    override suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup> = call(
        operation = ExternalSourceRuntimeOperation.PLAYBACK_GROUPS,
        payload = ExternalSourceRuntimePayloads.playbackGroups(title),
        decode = payloadCodec::decodePlaybackGroups,
    )

    override suspend fun playerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = call(
        operation = ExternalSourceRuntimeOperation.PLAYER_LINKS,
        payload = ExternalSourceRuntimePayloads.playerLinks(title, group, episode),
        decode = payloadCodec::decodePlayerLinks,
    )
}
