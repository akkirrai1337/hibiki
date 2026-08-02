package org.akkirrai.hibiki.shared.source

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.akkirrai.beakokit.api.ExternalSourceHostErrorCode
import org.akkirrai.beakokit.api.ExternalSourceHostHttpRequest
import org.akkirrai.beakokit.api.ExternalSourceHostHttpResponse
import org.akkirrai.beakokit.api.ExternalSourceHostDispatcher
import org.akkirrai.beakokit.api.ExternalSourceHostProtocolCodec
import org.akkirrai.beakokit.api.ExternalSourceHostResponse
import org.akkirrai.beakokit.api.ExternalSourceRuntimeNativeBridge
import org.akkirrai.beakokit.api.ExternalSourceRuntimeNativeBridgeFactory
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.JvmSourcePackageModuleReader
import org.akkirrai.beakokit.api.NativeBridgeExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceHostHttpClient
import org.akkirrai.beakokit.api.SourceHostHttpRequest
import org.akkirrai.beakokit.api.SourceHostHttpResponse
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceHostCapabilityException
import org.akkirrai.beakokit.runtime.NativeSourceRuntimeBridge
import java.util.UUID

/** Creates the Android native runtime adapter without changing the built-in source registry. */
fun createAndroidExternalSourceRuntimeFactory(): ExternalSourceRuntimeFactory =
    NativeBridgeExternalSourceRuntimeFactory(
        bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, context, module, requirements ->
            AndroidExternalSourceRuntimeBridge(
                context = context,
                module = module,
                requirements = requirements,
            )
        },
        moduleReader = JvmSourcePackageModuleReader(),
        requestIdFactory = { UUID.randomUUID().toString() },
    )

private class AndroidExternalSourceRuntimeBridge(
    private val context: SourceContext,
    private val module: ByteArray,
    requirements: SourceHostRequirements,
) : ExternalSourceRuntimeNativeBridge {
    private val host = AndroidExternalSourceHost(context.httpClient, requirements)

    override suspend fun call(request: ByteArray, maxResponseBytes: Long): ByteArray =
        withContext(Dispatchers.IO) {
            ensureActive()
            val response = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
                module = module,
                request = request.decodeToString(),
                host = NativeSourceRuntimeBridge.Host(host::call),
            )
            ensureActive()
            response.encodeToByteArray().also { responseBytes ->
                require(responseBytes.size.toLong() <= maxResponseBytes) {
                    "Native runtime response exceeds $maxResponseBytes bytes"
                }
            }
        }
}

private class AndroidExternalSourceHost(
    private val client: HttpClient,
    private val requirements: SourceHostRequirements,
) {
    private val dispatcher = ExternalSourceHostDispatcher { wireRequest ->
        withTimeout(wireRequest.timeoutMillis) {
            AndroidSourceHostHttpClient(client, requirements).execute(
                SourceHostHttpRequest(
                    method = wireRequest.method,
                    url = wireRequest.url,
                    headers = wireRequest.headers,
                    body = wireRequest.body,
                    timeoutMillis = wireRequest.timeoutMillis,
                    maxResponseBytes = wireRequest.maxResponseBytes,
                ),
            )
        }.let { httpResponse ->
            ExternalSourceHostHttpResponse(
                statusCode = httpResponse.statusCode,
                headers = httpResponse.headers,
                body = httpResponse.body,
            )
        }
    }

    fun call(bytes: ByteArray): ByteArray {
        val response = try {
            val request = ExternalSourceHostProtocolCodec.decodeRequest(bytes)
            runBlocking { dispatcher.dispatch(request) }
        } catch (_: CancellationException) {
            return errorResponse(
                requestId = extractRequestId(bytes),
                code = ExternalSourceHostErrorCode.CANCELLED,
                message = "Host request was cancelled",
            )
        } catch (error: Throwable) {
            return errorResponse(
                requestId = extractRequestId(bytes),
                code = when (error) {
                    is SourceHostCapabilityException,
                    is IllegalArgumentException,
                    -> ExternalSourceHostErrorCode.HOST_ACCESS_DENIED
                    else -> ExternalSourceHostErrorCode.HOST_FAILURE
                },
                message = error.message ?: "Host request failed",
            )
        }
        return ExternalSourceHostProtocolCodec.encodeResponse(response)
    }

    private fun errorResponse(
        requestId: String,
        code: ExternalSourceHostErrorCode,
        message: String,
    ): ByteArray = ExternalSourceHostProtocolCodec.encodeResponse(
        ExternalSourceHostResponse(
            requestId = requestId,
            errorCode = code,
            errorMessage = message,
        ),
    )

    private fun extractRequestId(bytes: ByteArray): String = runCatching {
        ExternalSourceHostProtocolCodec.decodeRequest(bytes).requestId
    }.getOrDefault("host-invalid-request")
}

private class AndroidSourceHostHttpClient(
    private val client: HttpClient,
    override val requirements: SourceHostRequirements,
) : SourceHostHttpClient() {
    override suspend fun executeNetwork(request: SourceHostHttpRequest): SourceHostHttpResponse {
        val response = client.request(request.url) {
            method = HttpMethod.parse(request.method)
            headers {
                request.headers.forEach { (name, value) -> append(name, value) }
            }
            request.body?.let(::setBody)
        }
        val body = response.bodyAsChannel()
            .readRemaining(request.maxResponseBytes + 1)
            .readText()
        require(body.encodeToByteArray().size.toLong() <= request.maxResponseBytes) {
            "Host HTTP response exceeds ${request.maxResponseBytes} bytes"
        }
        return SourceHostHttpResponse(
            statusCode = response.status.value,
            headers = response.headers.entries().associate { (name, values) ->
                name to values.joinToString(",")
            },
            body = body,
        )
    }
}
