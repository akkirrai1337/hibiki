package org.akkirrai.beakokit.runtime

/** Android entry point for the packaged Wasmtime runtime. */
class NativeSourceRuntimeBridge private constructor() {
    fun interface Host {
        fun call(request: ByteArray): ByteArray
    }

    companion object {
        init {
            System.loadLibrary("wasmtime_spike")
        }

        @JvmStatic
        external fun validateModule(module: ByteArray)

        @JvmStatic
        external fun protocolModuleCallWithHost(
            module: ByteArray,
            request: String,
            host: Host,
            cancellationScopeId: Long,
        ): String

        @JvmStatic
        external fun beginCancellationScope(): Long

        @JvmStatic
        external fun cancelCancellationScope(scopeId: Long)

        @JvmStatic
        external fun finishCancellationScope(scopeId: Long)
    }
}
