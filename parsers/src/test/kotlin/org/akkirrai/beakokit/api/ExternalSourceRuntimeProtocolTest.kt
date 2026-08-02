package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ExternalSourceRuntimeProtocolTest {
    @Test
    fun requestRoundTripsAsVersionedJson() {
        val request = ExternalSourceRuntimeRequest(
            requestId = "request-1",
            operation = ExternalSourceRuntimeOperation.SEARCH,
            payload = buildJsonObject { put("query", "frieren") },
        )

        val restored = Json.decodeFromString<ExternalSourceRuntimeRequest>(Json.encodeToString(request))

        assertEquals(request, restored)
        assertEquals(EXTERNAL_SOURCE_RUNTIME_PROTOCOL_VERSION, restored.protocolVersion)
    }

    @Test
    fun responseRequiresExactlyOneResult() {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimeResponse(requestId = "request-1")
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimeResponse(
                requestId = "request-1",
                payload = buildJsonObject { put("items", 0) },
                errorCode = ExternalSourceRuntimeErrorCode.RUNTIME_FAILURE,
            )
        }
    }

    @Test
    fun errorResponsePreservesCodeAndMessage() {
        val response = ExternalSourceRuntimeResponse(
            requestId = "request-2",
            errorCode = ExternalSourceRuntimeErrorCode.HOST_ACCESS_DENIED,
            errorMessage = "network capability is not granted",
        )

        val restored = Json.decodeFromString<ExternalSourceRuntimeResponse>(Json.encodeToString(response))

        assertEquals(ExternalSourceRuntimeErrorCode.HOST_ACCESS_DENIED, restored.errorCode)
        assertEquals("network capability is not granted", restored.errorMessage)
    }

    @Test
    fun protocolErrorMapsToStableSourceException() {
        val exception = assertFailsWith<SourceException> {
            ExternalSourceRuntimeResponse(
                requestId = "request-3",
                errorCode = ExternalSourceRuntimeErrorCode.HOST_ACCESS_DENIED,
                errorMessage = "cookies capability is not granted",
            ).requirePayload()
        }

        assertEquals(SourceErrorCode.HOST_ACCESS_DENIED, exception.code)
        assertEquals(SourceErrorKind.UNAVAILABLE, exception.kind)
        assertEquals("cookies capability is not granted", exception.message)
    }

    @Test
    fun cancelledProtocolResponseCancelsTheCaller() {
        val exception = assertFailsWith<CancellationException> {
            ExternalSourceRuntimeResponse(
                requestId = "request-4",
                errorCode = ExternalSourceRuntimeErrorCode.CANCELLED,
                errorMessage = "caller cancelled search",
            ).requirePayload()
        }

        assertEquals("caller cancelled search", exception.message)
    }
}
