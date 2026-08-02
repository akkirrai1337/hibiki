package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class ExternalSourceHostProtocolTest {
    @Test
    fun dispatcher_routes_http_requests_and_wraps_the_response() = runBlocking {
        val request = ExternalSourceHostRequest(
            requestId = "host-dispatch-1",
            operation = ExternalSourceHostOperation.HTTP_REQUEST,
            payload = ExternalSourceHostProtocolCodec.encodeHttpRequest(
                ExternalSourceHostHttpRequest(
                    method = "GET",
                    url = "https://example.com/anime",
                ),
            ),
        )
        var receivedRequest: ExternalSourceHostHttpRequest? = null
        val response = ExternalSourceHostDispatcher { httpRequest ->
            receivedRequest = httpRequest
            ExternalSourceHostHttpResponse(statusCode = 204, body = "")
        }.dispatch(request)

        assertEquals(
            ExternalSourceHostHttpRequest(
                method = "GET",
                url = "https://example.com/anime",
            ),
            receivedRequest,
        )
        assertEquals(request.requestId, response.requestId)
        assertEquals(
            ExternalSourceHostHttpResponse(statusCode = 204, body = ""),
            ExternalSourceHostProtocolCodec.decodeHttpResponse(
                response.requirePayload(request.requestId),
            ),
        )
    }

    @Test
    fun http_request_and_response_round_trip_with_explicit_nulls() {
        val request = ExternalSourceHostRequest(
            requestId = "host-1",
            operation = ExternalSourceHostOperation.HTTP_REQUEST,
            payload = ExternalSourceHostProtocolCodec.encodeHttpRequest(
                ExternalSourceHostHttpRequest(
                    method = "GET",
                    url = "https://example.com/anime",
                    headers = mapOf("Accept-Language" to "ru"),
                ),
            ),
        )
        val decodedRequest = ExternalSourceHostProtocolCodec.decodeRequest(
            ExternalSourceHostProtocolCodec.encodeRequest(request),
        )
        assertEquals(request, decodedRequest)

        val response = ExternalSourceHostResponse(
            requestId = "host-1",
            payload = ExternalSourceHostProtocolCodec.encodeHttpResponse(
                ExternalSourceHostHttpResponse(
                    statusCode = 200,
                    body = "{}",
                ),
            ),
        )
        val decodedResponse = ExternalSourceHostProtocolCodec.decodeResponse(
            ExternalSourceHostProtocolCodec.encodeResponse(response),
        )
        assertEquals(response, decodedResponse)
        assertEquals(response.payload, decodedResponse.requirePayload("host-1"))
    }

    @Test
    fun response_rejects_a_different_request_id() {
        val response = ExternalSourceHostResponse(
            requestId = "host-2",
            payload = ExternalSourceHostProtocolCodec.encodeHttpResponse(
                ExternalSourceHostHttpResponse(statusCode = 200, body = "{}"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            response.requirePayload("host-1")
        }
    }

    @Test
    fun error_response_does_not_expose_a_payload() {
        val response = ExternalSourceHostResponse(
            requestId = "host-3",
            errorCode = ExternalSourceHostErrorCode.HOST_ACCESS_DENIED,
            errorMessage = "network capability is missing",
        )

        assertFailsWith<IllegalStateException> {
            response.requirePayload("host-3")
        }
    }

    @Test
    fun http_request_rejects_invalid_limits_at_the_wire_boundary() {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceHostHttpRequest(method = "", url = "https://example.com")
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceHostHttpRequest(
                method = "GET",
                url = "https://example.com",
                timeoutMillis = SourceHostHttpRequest.MAX_TIMEOUT_MILLIS + 1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceHostHttpRequest(
                method = "GET",
                url = "https://example.com",
                maxResponseBytes = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceHostHttpRequest(
                method = "GET",
                url = "https://example.com",
                maxResponseBytes = Long.MAX_VALUE,
            )
        }
    }
}
