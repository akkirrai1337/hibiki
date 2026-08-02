#ifndef WASMTIME_SPIKE_H
#define WASMTIME_SPIKE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define BEAKOKIT_PROTOCOL_CALL_OK 0
#define BEAKOKIT_PROTOCOL_CALL_INVALID_REQUEST -2
#define BEAKOKIT_PROTOCOL_CALL_BUFFER_TOO_SMALL -3
#define BEAKOKIT_PROTOCOL_CALL_RUNTIME_FAILURE -4
#define BEAKOKIT_PROTOCOL_MAX_REQUEST_BYTES (2u * 1024u * 1024u)
#define BEAKOKIT_PROTOCOL_MAX_MODULE_BYTES (16u * 1024u * 1024u)

// Executes one already verified Wasm module with the same protocol contract.
int32_t beakokit_runtime_protocol_call_with_module(
    const uint8_t* module_ptr,
    size_t module_len,
    const uint8_t* request_ptr,
    size_t request_len,
    uint8_t* response_ptr,
    size_t response_capacity,
    size_t* response_len);

typedef int32_t (*beakokit_host_call_fn)(
    void* user_data,
    const uint8_t* request_ptr,
    size_t request_len,
    uint8_t* response_ptr,
    size_t response_capacity,
    size_t* response_len);

// Executes one Wasm module while routing imported host.call requests through host_call.
int32_t beakokit_runtime_protocol_call_with_module_and_host(
    const uint8_t* module_ptr,
    size_t module_len,
    const uint8_t* request_ptr,
    size_t request_len,
    beakokit_host_call_fn host_call,
    void* user_data,
    uint8_t* response_ptr,
    size_t response_capacity,
    size_t* response_len);

#ifdef __cplusplus
}
#endif

#endif
