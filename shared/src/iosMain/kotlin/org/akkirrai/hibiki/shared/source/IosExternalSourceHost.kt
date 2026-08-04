package org.akkirrai.hibiki.shared.source

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.ExternalSourceHostDispatcher
import org.akkirrai.beakokit.api.ExternalSourceHostErrorCode
import org.akkirrai.beakokit.api.ExternalSourceHostHttpResponse
import org.akkirrai.beakokit.api.ExternalSourceHostProtocolCodec
import org.akkirrai.beakokit.api.ExternalSourceHostResponse
import org.akkirrai.beakokit.api.EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES
import org.akkirrai.beakokit.api.EXTERNAL_SOURCE_HOST_MAX_RESPONSE_BYTES
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceHostCapability
import org.akkirrai.beakokit.api.SourceHostCapabilityException
import org.akkirrai.beakokit.api.SourceHostConfigAccess
import org.akkirrai.beakokit.api.SourceHostHttpClient
import org.akkirrai.beakokit.api.SourceHostHttpRequest
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceId

/** Platform host implementation used by the future iOS native runtime callback. */
@OptIn(ExperimentalForeignApi::class)
internal class IosExternalSourceHost(
    private val client: HttpClient,
    sourceId: SourceId,
    private val sourceContext: org.akkirrai.beakokit.api.SourceContext,
    private val requirements: SourceHostRequirements,
) {
    private val dispatcher = ExternalSourceHostDispatcher(
        executeHttpRequest = { wireRequest ->
            withTimeout(wireRequest.timeoutMillis) {
                IosSourceHostHttpClient(client, requirements).execute(
                    SourceHostHttpRequest(
                        method = wireRequest.method,
                        url = wireRequest.url,
                        headers = wireRequest.headers,
                        body = wireRequest.body,
                        timeoutMillis = wireRequest.timeoutMillis,
                        maxResponseBytes = wireRequest.maxResponseBytes,
                    ),
                )
            }.let { response ->
                ExternalSourceHostHttpResponse(
                    statusCode = response.statusCode,
                    headers = response.headers,
                    body = response.body,
                )
            }
        },
        storage = IosSourceHostStorage(sourceId, requirements),
        cookies = IosSourceHostCookies(sourceId, requirements),
        config = IosSourceHostConfig(sourceContext.config, requirements),
        requirements = requirements,
    )

    fun call(bytes: ByteArray): ByteArray {
        if (bytes.size.toLong() > EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES) {
            return ExternalSourceHostProtocolCodec.encodeResponse(
                ExternalSourceHostResponse(
                    requestId = "host-invalid-request",
                    errorCode = ExternalSourceHostErrorCode.INVALID_REQUEST,
                    errorMessage = "Host request exceeds $EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES bytes",
                ),
            )
        }
        val response = try {
            runBlocking { dispatcher.dispatch(ExternalSourceHostProtocolCodec.decodeRequest(bytes)) }
        } catch (_: CancellationException) {
            errorResponse(bytes, ExternalSourceHostErrorCode.CANCELLED, "Host request was cancelled")
        } catch (error: Throwable) {
            errorResponse(
                bytes = bytes,
                code = when (error) {
                    is SourceHostCapabilityException -> ExternalSourceHostErrorCode.HOST_ACCESS_DENIED
                    is IllegalArgumentException -> ExternalSourceHostErrorCode.INVALID_REQUEST
                    else -> ExternalSourceHostErrorCode.HOST_FAILURE
                },
                message = error.message ?: "Host request failed",
            )
        }
        return ExternalSourceHostProtocolCodec.encodeResponse(response).let { encoded ->
            if (encoded.size.toLong() <= EXTERNAL_SOURCE_HOST_MAX_RESPONSE_BYTES) {
                encoded
            } else {
                ExternalSourceHostProtocolCodec.encodeResponse(
                    ExternalSourceHostResponse(
                        requestId = response.requestId,
                        errorCode = ExternalSourceHostErrorCode.HOST_FAILURE,
                        errorMessage = "Host response exceeds $EXTERNAL_SOURCE_HOST_MAX_RESPONSE_BYTES bytes",
                    ),
                )
            }
        }
    }

    private fun errorResponse(
        bytes: ByteArray,
        code: ExternalSourceHostErrorCode,
        message: String,
    ): ExternalSourceHostResponse = ExternalSourceHostResponse(
        requestId = runCatching {
            ExternalSourceHostProtocolCodec.decodeRequest(bytes).requestId
        }.getOrDefault("host-invalid-request"),
        errorCode = code,
        errorMessage = message
            .replace('\r', ' ')
            .replace('\n', ' ')
            .take(ExternalSourceHostResponse.MAX_ERROR_MESSAGE_LENGTH),
    )
}

private class IosSourceHostHttpClient(
    private val client: HttpClient,
    override val requirements: SourceHostRequirements,
) : SourceHostHttpClient() {
    override suspend fun executeNetwork(request: SourceHostHttpRequest): org.akkirrai.beakokit.api.SourceHostHttpResponse {
        val response = client.request(request.url) {
            method = HttpMethod.parse(request.method)
            headers { request.headers.forEach { (name, value) -> append(name, value) } }
            request.body?.let(::setBody)
        }
        val body = response.bodyAsChannel()
            .readRemaining(request.maxResponseBytes + 1)
            .readText()
        require(body.encodeToByteArray().size.toLong() <= request.maxResponseBytes) {
            "Host HTTP response exceeds ${request.maxResponseBytes} bytes"
        }
        return org.akkirrai.beakokit.api.SourceHostHttpResponse(
            statusCode = response.status.value,
            headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(",") },
            body = body,
        )
    }
}

private class IosSourceHostConfig(
    private val config: SourceConfig,
    override val requirements: SourceHostRequirements,
) : SourceHostConfigAccess {
    override fun value(key: String): String? {
        require(SourceHostCapability.CONFIG)
        return config.value(key)
    }

    override fun secret(key: String): String? {
        require(SourceHostCapability.CONFIG)
        return config.secret(key)
    }
}
