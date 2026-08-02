package org.akkirrai.beakokit.api

/** Host-owned compatibility policy for the runtime/ABI declared by a source package. */
fun interface SourceRuntimeSupportPolicy {
    fun supports(runtime: SourceRuntime): Boolean

    companion object {
        val WASMTIME_WASI = SourceRuntimeSupportPolicy { runtime ->
            runtime.id == "wasm" && runtime.abi in setOf("wasm32-wasi", "wasm32-wasi-preview1")
        }
    }
}
