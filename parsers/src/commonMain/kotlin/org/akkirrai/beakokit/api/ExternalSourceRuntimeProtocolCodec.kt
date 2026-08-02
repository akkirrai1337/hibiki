package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Strict UTF-8 JSON codec shared by native runtime bridges on every platform. */
object ExternalSourceRuntimeProtocolCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: ExternalSourceRuntimeRequest): ByteArray =
        json.encodeToString(request).encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): ExternalSourceRuntimeRequest =
        json.decodeFromString(bytes.decodeToString())

    fun encodeResponse(response: ExternalSourceRuntimeResponse): ByteArray =
        json.encodeToString(response).encodeToByteArray()

    fun decodeResponse(bytes: ByteArray): ExternalSourceRuntimeResponse =
        json.decodeFromString(bytes.decodeToString())
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
) : ExternalSourceRuntimeTransport {
    override suspend fun call(
        request: ExternalSourceRuntimeRequest,
        limits: ExternalSourceRuntimeCallLimits,
    ): ExternalSourceRuntimeResponse = ExternalSourceRuntimeProtocolCodec.decodeResponse(
        bridge.call(
            request = ExternalSourceRuntimeProtocolCodec.encodeRequest(request),
            maxResponseBytes = limits.maxResponseBytes,
        ),
    )
}
