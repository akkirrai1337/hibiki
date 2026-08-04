package org.akkirrai.hibiki.shared.source

import android.content.Context
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
import kotlin.coroutines.coroutineContext
import org.akkirrai.beakokit.api.ExternalSourceHostErrorCode
import org.akkirrai.beakokit.api.ExternalSourceHostHttpRequest
import org.akkirrai.beakokit.api.ExternalSourceHostHttpResponse
import org.akkirrai.beakokit.api.ExternalSourceHostDispatcher
import org.akkirrai.beakokit.api.EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES
import org.akkirrai.beakokit.api.ExternalSourceHostProtocolCodec
import org.akkirrai.beakokit.api.ExternalSourceHostResponse
import org.akkirrai.beakokit.api.ExternalSourceRuntimeNativeBridge
import org.akkirrai.beakokit.api.ExternalSourceRuntimeNativeBridgeFactory
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.JvmSourcePackageModuleReader
import org.akkirrai.beakokit.api.NativeBridgeExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceHostHttpClient
import org.akkirrai.beakokit.api.SourceHostHttpRequest
import org.akkirrai.beakokit.api.SourceHostHttpResponse
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceHostCapabilityException
import org.akkirrai.beakokit.api.SourceHostConfigAccess
import org.akkirrai.beakokit.api.SourceHostCapability
import org.akkirrai.beakokit.runtime.NativeSourceRuntimeBridge
import java.util.UUID

/** Creates the Android native runtime adapter without changing the built-in source registry. */
fun createAndroidExternalSourceRuntimeFactory(context: Context): ExternalSourceRuntimeFactory =
    NativeBridgeExternalSourceRuntimeFactory(
        bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { sourcePackage, sourceContext, module, requirements ->
            AndroidExternalSourceRuntimeBridge(
                appContext = context.applicationContext,
                sourceContext = sourceContext,
                sourcePackage = sourcePackage,
                module = module,
                requirements = requirements,
            )
        },
        moduleReader = JvmSourcePackageModuleReader(),
        requestIdFactory = { UUID.randomUUID().toString() },
    )

/** Compiles a staging module in Wasmtime without executing a source operation. */
suspend fun validateAndroidExternalSourceRuntime(sourcePackage: ActiveExternalSourcePackage) {
    val module = JvmSourcePackageModuleReader().read(
        packagePath = sourcePackage.installed.packagePath,
        entrypoint = sourcePackage.manifest.entrypoint,
    )
    withContext(Dispatchers.IO) {
        NativeSourceRuntimeBridge.validateModule(module)
    }
}

private class AndroidExternalSourceRuntimeBridge(
    private val appContext: Context,
    private val sourceContext: SourceContext,
    sourcePackage: ActiveExternalSourcePackage,
    private val module: ByteArray,
    requirements: SourceHostRequirements,
) : ExternalSourceRuntimeNativeBridge {
    private val host = AndroidExternalSourceHost(
        client = sourceContext.httpClient,
        requirements = requirements,
        storage = AndroidSourceHostStorage(
            context = appContext,
            sourceId = sourcePackage.manifest.sourceId,
            requirements = requirements,
        ),
        cookies = AndroidSourceHostCookies(
            context = appContext,
            sourceId = sourcePackage.manifest.sourceId,
            requirements = requirements,
        ),
        config = AndroidSourceHostConfig(
            config = sourceContext.config,
            requirements = requirements,
        ),
    )

    override suspend fun call(request: ByteArray, maxResponseBytes: Long): ByteArray =
        kotlinx.coroutines.coroutineScope {
            val cancellationScopeId = NativeSourceRuntimeBridge.beginCancellationScope()
            val cancellationHandle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    NativeSourceRuntimeBridge.cancelCancellationScope(cancellationScopeId)
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    ensureActive()
                    val response = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
                        module = module,
                        request = request.decodeToString(),
                        host = NativeSourceRuntimeBridge.Host(host::call),
                        cancellationScopeId = cancellationScopeId,
                    )
                    ensureActive()
                    response.encodeToByteArray().also { responseBytes ->
                        require(responseBytes.size.toLong() <= maxResponseBytes) {
                            "Native runtime response exceeds $maxResponseBytes bytes"
                        }
                    }
                }
            } finally {
                cancellationHandle?.dispose()
                NativeSourceRuntimeBridge.finishCancellationScope(cancellationScopeId)
            }
        }
}

private class AndroidExternalSourceHost(
    private val client: HttpClient,
    private val requirements: SourceHostRequirements,
    private val storage: AndroidSourceHostStorage,
    private val cookies: AndroidSourceHostCookies,
    private val config: AndroidSourceHostConfig,
) {
    private val dispatcher = ExternalSourceHostDispatcher(
        executeHttpRequest = { wireRequest ->
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
        },
        storage = storage,
        cookies = cookies,
        config = config,
        requirements = requirements,
    )

    fun call(bytes: ByteArray): ByteArray {
        if (bytes.size.toLong() > EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES) {
            return errorResponse(
                requestId = "host-invalid-request",
                code = ExternalSourceHostErrorCode.INVALID_REQUEST,
                message = "Host request exceeds $EXTERNAL_SOURCE_HOST_MAX_REQUEST_BYTES bytes",
            )
        }
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

private class AndroidSourceHostConfig(
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
