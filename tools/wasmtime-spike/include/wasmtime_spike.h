#ifndef WASMTIME_SPIKE_H
#define WASMTIME_SPIKE_H

#ifdef __cplusplus
extern "C" {
#endif

// Returns 0 when the runtime probe succeeds, or a negative value otherwise.
int beakokit_runtime_probe(void);

int Java_org_akkirrai_hibiki_WasmtimeRuntimeSmokeTest_probe(void* env, void* receiver);

#ifdef __cplusplus
}
#endif

#endif
