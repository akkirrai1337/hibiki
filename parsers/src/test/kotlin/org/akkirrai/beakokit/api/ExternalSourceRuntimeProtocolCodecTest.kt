package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalSourceRuntimeProtocolCodecTest {
    @Test
    fun roundTripsRequestAndResponseWithExplicitProtocolFields() {
        val request = ExternalSourceRuntimeRequest(
            requestId = "codec-1",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )
        val response = ExternalSourceRuntimeResponse(
            requestId = request.requestId,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val requestBytes = ExternalSourceRuntimeProtocolCodec.encodeRequest(request)
        val responseBytes = ExternalSourceRuntimeProtocolCodec.encodeResponse(response)

        val restoredRequest = ExternalSourceRuntimeProtocolCodec.decodeResponse(responseBytes)
        assertEquals(request.requestId, restoredRequest.requestId)
        assertEquals(response.payload, restoredRequest.payload)
        assertContentEquals(requestBytes, ExternalSourceRuntimeProtocolCodec.encodeRequest(request))
    }

    @Test
    fun nativeBridgeReceivesSerializedRequestAndReturnsSerializedResponse() = runBlocking {
        var receivedRequest = byteArrayOf()
        var receivedLimit = 0L
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { request, maxResponseBytes ->
                receivedRequest = request
                receivedLimit = maxResponseBytes
                """
                {"requestId":"bridge-1","payload":{"id":"title-1"},"errorCode":null,"errorMessage":null,"protocolVersion":1}
                """.trimIndent().encodeToByteArray()
            },
        )
        val request = ExternalSourceRuntimeRequest(
            requestId = "bridge-1",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val response = transport.call(request, ExternalSourceRuntimeCallLimits(maxResponseBytes = 4096))

        assertEquals(request.requestId, response.requestId)
        assertEquals(4096, receivedLimit)
        assertContentEquals(
            ExternalSourceRuntimeProtocolCodec.encodeRequest(request),
            receivedRequest,
        )
    }

    @Test
    fun nativeBridgeRejectsOversizedResponseBeforeDecoding() = runBlocking {
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { _, _ -> ByteArray(33) },
        )
        val request = ExternalSourceRuntimeRequest(
            requestId = "bridge-oversized",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val error = assertFailsWith<SourceUnavailableException> {
            transport.call(
                request = request,
                limits = ExternalSourceRuntimeCallLimits(maxResponseBytes = 32),
            )
        }

        assertEquals("Native runtime response exceeds 32 bytes", error.message)
    }
}
