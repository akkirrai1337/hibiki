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

    @Test
    fun storage_operations_round_trip_through_the_dispatcher() = runBlocking {
        val values = mutableMapOf<String, String>()
        val storage = object : ExternalSourceHostStorageAccess {
            override suspend fun read(key: String): String? = values[key]

            override suspend fun write(key: String, value: String) {
                values[key] = value
            }

            override suspend fun remove(key: String) {
                values.remove(key)
            }
        }
        val dispatcher = ExternalSourceHostDispatcher(
            executeHttpRequest = { error("HTTP must not be called") },
            storage = storage,
        )

        val writeRequest = ExternalSourceHostRequest(
            requestId = "storage-write",
            operation = ExternalSourceHostOperation.STORAGE_WRITE,
            payload = ExternalSourceHostProtocolCodec.encodeStorageWriteRequest(
                ExternalSourceHostStorageWriteRequest("token", "abc"),
            ),
        )
        dispatcher.dispatch(writeRequest)
        assertEquals("abc", values["token"])

        val readResponse = dispatcher.dispatch(
            ExternalSourceHostRequest(
                requestId = "storage-read",
                operation = ExternalSourceHostOperation.STORAGE_READ,
                payload = ExternalSourceHostProtocolCodec.encodeStorageReadRequest(
                    ExternalSourceHostStorageReadRequest("token"),
                ),
            ),
        )
        assertEquals(
            ExternalSourceHostStorageReadResponse("abc"),
            ExternalSourceHostProtocolCodec.decodeStorageReadResponse(
                readResponse.requirePayload("storage-read"),
            ),
        )

        dispatcher.dispatch(
            ExternalSourceHostRequest(
                requestId = "storage-remove",
                operation = ExternalSourceHostOperation.STORAGE_REMOVE,
                payload = ExternalSourceHostProtocolCodec.encodeStorageRemoveRequest(
                    ExternalSourceHostStorageRemoveRequest("token"),
                ),
            ),
        )
        assertEquals(null, values["token"])
    }

    @Test
    fun storage_operation_requires_a_storage_host() = runBlocking {
        assertFailsWith<SourceHostCapabilityException> {
            ExternalSourceHostDispatcher { error("HTTP must not be called") }.dispatch(
                ExternalSourceHostRequest(
                    requestId = "storage-denied",
                    operation = ExternalSourceHostOperation.STORAGE_READ,
                    payload = ExternalSourceHostProtocolCodec.encodeStorageReadRequest(
                        ExternalSourceHostStorageReadRequest("token"),
                    ),
                ),
            )
        }
    }

    @Test
    fun storage_operation_rejects_invalid_wire_limits() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceHostDispatcher(
                executeHttpRequest = { error("HTTP must not be called") },
                storage = object : ExternalSourceHostStorageAccess {
                    override suspend fun read(key: String): String? = null
                    override suspend fun write(key: String, value: String) = Unit
                    override suspend fun remove(key: String) = Unit
                },
            ).dispatch(
                ExternalSourceHostRequest(
                    requestId = "storage-invalid",
                    operation = ExternalSourceHostOperation.STORAGE_WRITE,
                    payload = ExternalSourceHostProtocolCodec.encodeStorageWriteRequest(
                        ExternalSourceHostStorageWriteRequest("", "value"),
                    ),
                ),
            )
        }
    }

    @Test
    fun cookies_operations_round_trip_through_the_dispatcher() = runBlocking {
        val values = mutableMapOf<String, Map<String, String>>()
        val cookies = object : SourceHostCookiesAccess {
            override suspend fun forUrl(url: String): Map<String, String> = values[url].orEmpty()

            override suspend fun storeFromResponse(url: String, cookies: Map<String, String>) {
                values[url] = cookies
            }

            override suspend fun clear(url: String) {
                values.remove(url)
            }
        }
        val dispatcher = ExternalSourceHostDispatcher(
            executeHttpRequest = { error("HTTP must not be called") },
            storage = null,
            cookies = cookies,
        )
        val url = "https://example.com/anime"

        dispatcher.dispatch(
            ExternalSourceHostRequest(
                requestId = "cookies-store",
                operation = ExternalSourceHostOperation.COOKIES_STORE_RESPONSE,
                payload = ExternalSourceHostProtocolCodec.encodeCookiesStoreResponseRequest(
                    ExternalSourceHostCookiesStoreResponseRequest(url, mapOf("session" to "abc")),
                ),
            ),
        )
        val response = dispatcher.dispatch(
            ExternalSourceHostRequest(
                requestId = "cookies-read",
                operation = ExternalSourceHostOperation.COOKIES_FOR_URL,
                payload = ExternalSourceHostProtocolCodec.encodeCookiesForUrlRequest(
                    ExternalSourceHostCookiesForUrlRequest(url),
                ),
            ),
        )
        assertEquals(
            ExternalSourceHostCookiesForUrlResponse(mapOf("session" to "abc")),
            ExternalSourceHostProtocolCodec.decodeCookiesForUrlResponse(
                response.requirePayload("cookies-read"),
            ),
        )
    }

    @Test
    fun cookies_operation_requires_a_cookie_host() = runBlocking {
        assertFailsWith<SourceHostCapabilityException> {
            ExternalSourceHostDispatcher { error("HTTP must not be called") }.dispatch(
                ExternalSourceHostRequest(
                    requestId = "cookies-denied",
                    operation = ExternalSourceHostOperation.COOKIES_FOR_URL,
                    payload = ExternalSourceHostProtocolCodec.encodeCookiesForUrlRequest(
                        ExternalSourceHostCookiesForUrlRequest("https://example.com"),
                    ),
                ),
            )
        }
    }

    @Test
    fun config_operations_keep_values_and_secrets_separate() = runBlocking {
        val config = object : SourceHostConfigAccess {
            override val requirements = SourceHostRequirements(
                capabilities = setOf(SourceHostCapability.CONFIG),
            )

            override fun value(key: String): String? = if (key == "base-url") "https://example.com" else null

            override fun secret(key: String): String? = if (key == "token") "secret" else null
        }
        val dispatcher = ExternalSourceHostDispatcher(
            executeHttpRequest = { error("HTTP must not be called") },
            storage = null,
            cookies = null,
            config = config,
        )
        val request = ExternalSourceHostRequest(
            requestId = "config-value",
            operation = ExternalSourceHostOperation.CONFIG_VALUE,
            payload = ExternalSourceHostProtocolCodec.encodeConfigRequest(
                ExternalSourceHostConfigRequest("base-url"),
            ),
        )
        val response = dispatcher.dispatch(request)
        assertEquals(
            ExternalSourceHostConfigResponse("https://example.com"),
            ExternalSourceHostProtocolCodec.decodeConfigResponse(response.requirePayload(request.requestId)),
        )
        val secretRequest = request.copy(
            requestId = "config-secret",
            operation = ExternalSourceHostOperation.CONFIG_SECRET,
            payload = ExternalSourceHostProtocolCodec.encodeConfigRequest(
                ExternalSourceHostConfigRequest("token"),
            ),
        )
        assertEquals(
            ExternalSourceHostConfigResponse("secret"),
            ExternalSourceHostProtocolCodec.decodeConfigResponse(
                dispatcher.dispatch(secretRequest).requirePayload(secretRequest.requestId),
            ),
        )
    }

    @Test
    fun config_operation_requires_config_capability() = runBlocking {
        val config = object : SourceHostConfigAccess {
            override val requirements = SourceHostRequirements()
            override fun value(key: String): String? = "value"
            override fun secret(key: String): String? = "secret"
        }
        assertFailsWith<SourceHostCapabilityException> {
            ExternalSourceHostDispatcher(
                executeHttpRequest = { error("HTTP must not be called") },
                storage = null,
                cookies = null,
                config = config,
            ).dispatch(
                ExternalSourceHostRequest(
                    requestId = "config-denied",
                    operation = ExternalSourceHostOperation.CONFIG_SECRET,
                    payload = ExternalSourceHostProtocolCodec.encodeConfigRequest(
                        ExternalSourceHostConfigRequest("token"),
                    ),
                ),
            )
        }
    }
}
