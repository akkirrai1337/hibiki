package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asCPointer
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSUUID
import platform.posix.memcpy
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.ExternalSourceRuntimeNativeBridge
import org.akkirrai.beakokit.api.ExternalSourceRuntimeNativeBridgeFactory
import org.akkirrai.beakokit.api.IosSourcePackageModuleReader
import org.akkirrai.beakokit.api.NativeBridgeExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourcePackageStateException
import org.akkirrai.beakokit.api.SourceRuntimeSupportPolicy
import org.akkirrai.hibiki.shared.runtime.nativebridge.BEAKOKIT_PROTOCOL_CALL_OK
import org.akkirrai.hibiki.shared.runtime.nativebridge.BEAKOKIT_PROTOCOL_CALL_BUFFER_TOO_SMALL
import org.akkirrai.hibiki.shared.runtime.nativebridge.BEAKOKIT_PROTOCOL_CALL_RUNTIME_FAILURE
import org.akkirrai.hibiki.shared.runtime.nativebridge.beakokit_runtime_protocol_call_with_module_and_host

/** Creates the iOS runtime factory once the Rust library is linked by the Apple app. */
@OptIn(ExperimentalForeignApi::class)
internal fun createIosExternalSourceRuntimeFactory(client: HttpClient): ExternalSourceRuntimeFactory =
    NativeBridgeExternalSourceRuntimeFactory(
        bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { sourcePackage, context, module, requirements ->
            IosExternalSourceRuntimeBridge(
                module = module,
                sourcePackage = sourcePackage,
                sourceContext = context,
                requirements = requirements,
                client = client,
            )
        },
        moduleReader = IosSourcePackageModuleReader(),
        requestIdFactory = { NSUUID().UUIDString },
        runtimeSupportPolicy = SourceRuntimeSupportPolicy.WASMTIME_WASI,
    )

@OptIn(ExperimentalForeignApi::class)
private class IosExternalSourceRuntimeBridge(
    private val module: ByteArray,
    sourcePackage: ActiveExternalSourcePackage,
    sourceContext: SourceContext,
    requirements: SourceHostRequirements,
    client: HttpClient,
) : ExternalSourceRuntimeNativeBridge {
    private val host = IosExternalSourceHost(
        client = client,
        sourceId = sourcePackage.manifest.sourceId,
        sourceContext = sourceContext,
        requirements = requirements,
    )

    override suspend fun call(request: ByteArray, maxResponseBytes: Long): ByteArray =
        withContext(Dispatchers.Default) {
            require(maxResponseBytes in 1..Int.MAX_VALUE.toLong()) {
                "Native runtime response limit is outside the iOS bridge range"
            }
            val callbackState = IosHostCallbackState(host)
            val stateRef = StableRef.create(callbackState)
            try {
                val responseLength = invokeNative(
                    request = request,
                    response = null,
                    state = stateRef,
                )
                if (responseLength.status != BEAKOKIT_PROTOCOL_CALL_BUFFER_TOO_SMALL) {
                    throw SourcePackageStateException(
                        "iOS native runtime sizing call failed with status ${responseLength.status}",
                    )
                }
                require(responseLength.length.toLong() <= maxResponseBytes) {
                    "Native runtime response exceeds $maxResponseBytes bytes"
                }
                val response = ByteArray(responseLength.length)
                val result = invokeNative(
                    request = request,
                    response = response,
                    state = stateRef,
                )
                if (result.status != BEAKOKIT_PROTOCOL_CALL_OK) {
                    throw SourcePackageStateException(
                        "iOS native runtime call failed with status ${result.status}",
                    )
                }
                response.copyOf(result.length)
            } finally {
                stateRef.dispose()
            }
        }

    private fun invokeNative(
        request: ByteArray,
        response: ByteArray?,
        state: StableRef<IosHostCallbackState>,
    ): NativeCallResult = module.usePinned { modulePinned ->
        request.usePinned { requestPinned ->
            response.usePinnedOrNull { responsePinned ->
                memScoped {
                    val responseLength = alloc<ULongVar>()
                    val status = beakokit_runtime_protocol_call_with_module_and_host(
                        module_ptr = modulePinned.addressOf(0),
                        module_len = module.size.convert(),
                        request_ptr = requestPinned.addressOf(0),
                        request_len = request.size.convert(),
                        host_call = staticCFunction(::iosHostCallback),
                        user_data = state.asCPointer(),
                        response_ptr = responsePinned?.addressOf(0),
                        response_capacity = response?.size?.toULong() ?: 0UL,
                        response_len = responseLength.ptr,
                    )
                    NativeCallResult(status, responseLength.value.toInt())
                }
            }
        }
    }
}

private data class NativeCallResult(
    val status: Int,
    val length: Int,
)

private class IosHostCallbackState(
    val host: IosExternalSourceHost,
) {
    var pendingRequest: ByteArray? = null
    var pendingResponse: ByteArray? = null
}

@OptIn(ExperimentalForeignApi::class)
private fun iosHostCallback(
    userData: kotlinx.cinterop.COpaquePointer?,
    requestPtr: kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>?,
    requestLen: ULong,
    responsePtr: kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>?,
    responseCapacity: ULong,
    responseLen: kotlinx.cinterop.CPointer<kotlinx.cinterop.ULongVar>?,
): Int {
    if (userData == null || responseLen == null || (requestPtr == null && requestLen != 0UL)) {
        return BEAKOKIT_PROTOCOL_CALL_RUNTIME_FAILURE
    }
    val state = userData.asStableRef<IosHostCallbackState>().get()
    val request = if (requestLen == 0UL) ByteArray(0) else requestPtr!!.readBytes(requestLen.toInt())
    if (responsePtr == null) {
        val response = try {
            state.host.call(request)
        } catch (_: Throwable) {
            return BEAKOKIT_PROTOCOL_CALL_RUNTIME_FAILURE
        }
        state.pendingRequest = request
        state.pendingResponse = response
        responseLen.pointed.value = response.size.toULong()
        return BEAKOKIT_PROTOCOL_CALL_BUFFER_TOO_SMALL
    }
    val pendingRequest = state.pendingRequest
    val pendingResponse = state.pendingResponse
    if (pendingRequest == null || pendingResponse == null || !pendingRequest.contentEquals(request)) {
        return BEAKOKIT_PROTOCOL_CALL_RUNTIME_FAILURE
    }
    if (responseCapacity < pendingResponse.size.toULong()) {
        responseLen.pointed.value = pendingResponse.size.toULong()
        return BEAKOKIT_PROTOCOL_CALL_BUFFER_TOO_SMALL
    }
    if (pendingResponse.isNotEmpty()) {
        pendingResponse.usePinned { pinned ->
            memcpy(responsePtr, pinned.addressOf(0), pendingResponse.size.convert())
        }
    }
    responseLen.pointed.value = pendingResponse.size.toULong()
    state.pendingRequest = null
    state.pendingResponse = null
    return BEAKOKIT_PROTOCOL_CALL_OK
}

private inline fun <R> ByteArray?.usePinnedOrNull(
    block: (kotlinx.cinterop.Pinned<kotlinx.cinterop.ByteVar>?) -> R,
): R = if (this == null || isEmpty()) {
    block(null)
} else {
    usePinned { pinned -> block(pinned) }
}
