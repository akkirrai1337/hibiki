package org.akkirrai.beakokit.api

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException

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
    fun nativeBridgeSerializesConcurrentCallsForOneWasmInstance() = runBlocking {
        var activeCalls = 0
        var overlapped = false
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { request, _ ->
                if (++activeCalls > 1) overlapped = true
                delay(10)
                activeCalls--
                val requestId = ExternalSourceRuntimeProtocolCodec.decodeRequest(request).requestId
                """
                {"requestId":"$requestId","payload":{},"errorCode":null,"errorMessage":null,"protocolVersion":1}
                """.trimIndent().encodeToByteArray()
            },
        )
        val requests = (1..2).map { index ->
            ExternalSourceRuntimeRequest(
                requestId = "bridge-concurrent-$index",
                operation = ExternalSourceRuntimeOperation.DETAILS,
                payload = buildJsonObject { put("id", "title-$index") },
            )
        }

        coroutineScope {
            requests.map { request ->
                async { transport.call(request, ExternalSourceRuntimeCallLimits()) }
            }.forEach { it.await() }
        }

        assertEquals(false, overlapped)
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

    @Test
    fun nativeBridgeNormalizesMalformedResponse() = runBlocking {
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { _, _ -> "not-json".encodeToByteArray() },
        )
        val request = ExternalSourceRuntimeRequest(
            requestId = "bridge-malformed",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val error = assertFailsWith<SourceException> {
            transport.call(request, ExternalSourceRuntimeCallLimits())
        }

        assertEquals(SourceErrorKind.PARSE, error.kind)
        assertEquals(SourceErrorCode.INVALID_RESPONSE, error.code)
    }

    @Test
    fun nativeBridgeNormalizesBridgeFailures() = runBlocking {
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { _, _ -> error("native bridge crashed") },
        )
        val request = ExternalSourceRuntimeRequest(
            requestId = "bridge-failure",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val error = assertFailsWith<SourceException> {
            transport.call(request, ExternalSourceRuntimeCallLimits())
        }

        assertEquals(SourceErrorKind.UNKNOWN, error.kind)
        assertEquals(SourceErrorCode.RUNTIME_FAILURE, error.code)
        assertEquals("native bridge crashed", error.cause?.message)
    }

    @Test
    fun nativeBridgePreservesCancellation() = runBlocking {
        val cancellation = CancellationException("caller cancelled native runtime")
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { _, _ -> throw cancellation },
        )
        val request = ExternalSourceRuntimeRequest(
            requestId = "bridge-cancelled",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val error = assertFailsWith<CancellationException> {
            transport.call(request, ExternalSourceRuntimeCallLimits())
        }

        assertEquals(cancellation.message, error.message)
    }

    @Test
    fun nativeBridgeRejectsOversizedRequestBeforeCallingBridge() = runBlocking {
        var called = false
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { _, _ ->
                called = true
                error("Native bridge must not be called")
            },
        )
        val request = ExternalSourceRuntimeRequest(
            requestId = "bridge-request-too-large",
            operation = ExternalSourceRuntimeOperation.DETAILS,
            payload = buildJsonObject { put("id", "title-1") },
        )

        val error = assertFailsWith<SourceException> {
            transport.call(
                request = request,
                limits = ExternalSourceRuntimeCallLimits(maxRequestBytes = 1),
            )
        }

        assertEquals(SourceErrorCode.INVALID_REQUEST, error.code)
        assertEquals(false, called)
    }

    @Test
    fun wireCodecRejectsMalformedUtf8() {
        val requestError = assertFailsWith<SourceException> {
            ExternalSourceRuntimeProtocolCodec.decodeRequest(byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D))
        }
        assertEquals(SourceErrorCode.INVALID_REQUEST, requestError.code)
        val responseError = assertFailsWith<SourceException> {
            ExternalSourceRuntimeProtocolCodec.decodeResponse(byteArrayOf(0x7B, 0xE2.toByte(), 0x28, 0xA1.toByte(), 0x7D))
        }
        assertEquals(SourceErrorCode.INVALID_RESPONSE, responseError.code)
    }

    @Test
    fun runtimeLimits_reserve_space_for_size_sentinels() {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimeCallLimits(maxRequestBytes = Long.MAX_VALUE)
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimeCallLimits(maxResponseBytes = Long.MAX_VALUE)
        }
    }
}
