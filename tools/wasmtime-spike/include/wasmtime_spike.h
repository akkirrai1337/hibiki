#ifndef WASMTIME_SPIKE_H
#define WASMTIME_SPIKE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Returns 0 when the runtime probe succeeds, or a negative value otherwise.
int beakokit_runtime_probe(void);

#define BEAKOKIT_PROTOCOL_CALL_OK 0
#define BEAKOKIT_PROTOCOL_CALL_INVALID_REQUEST -2
#define BEAKOKIT_PROTOCOL_CALL_BUFFER_TOO_SMALL -3
#define BEAKOKIT_PROTOCOL_CALL_RUNTIME_FAILURE -4

int32_t beakokit_runtime_protocol_call(
    const uint8_t* request_ptr,
    size_t request_len,
    uint8_t* response_ptr,
    size_t response_capacity,
    size_t* response_len);

int Java_org_akkirrai_hibiki_WasmtimeRuntimeSmokeTest_probe(void* env, void* receiver);

#ifdef __cplusplus
}
#endif

#endif
