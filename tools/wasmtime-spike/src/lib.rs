#[cfg(feature = "android-production-jni")]
use std::collections::VecDeque;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr::null_mut;
use std::sync::mpsc;
#[cfg(feature = "android-production-jni")]
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

#[cfg(any(feature = "android-production-jni", feature = "android-harness"))]
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
#[cfg(any(feature = "android-production-jni", feature = "android-harness"))]
use jni::{JNIEnv, JavaVM};
#[cfg(feature = "spike-probes")]
use wasmtime::Instance;
use wasmtime::{Caller, Config, Engine, Linker, Module, Store};

pub mod protocol;

pub const PROTOCOL_CALL_OK: i32 = 0;
pub const PROTOCOL_CALL_INVALID_REQUEST: i32 = -2;
pub const PROTOCOL_CALL_BUFFER_TOO_SMALL: i32 = -3;
pub const PROTOCOL_CALL_RUNTIME_FAILURE: i32 = -4;
pub const PROTOCOL_MAX_REQUEST_BYTES: usize = 2 * 1024 * 1024;
pub const PROTOCOL_MAX_MODULE_BYTES: usize = 16 * 1024 * 1024;
pub const PROTOCOL_MAX_RESPONSE_BYTES: usize = 8 * 1024 * 1024;
pub const PROTOCOL_DEFAULT_TIMEOUT_MILLIS: u64 = 30_000;
const PROTOCOL_MAX_FUEL: u64 = 50_000_000;
const WASM_PAGE_SIZE_BYTES: usize = 64 * 1024;

fn runtime_engine() -> Result<Engine, wasmtime::Error> {
    let mut config = Config::new();
    config.consume_fuel(true);
    config.epoch_interruption(true);
    Engine::new(&config)
}

fn write_host_response(
    mut caller: Caller<'_, ()>,
    memory: wasmtime::Memory,
    response: &[u8],
) -> Result<i64, wasmtime::Error> {
    if response.len() > PROTOCOL_MAX_RESPONSE_BYTES {
        return Err(wasmtime::Error::msg(
            "host response exceeds the native limit",
        ));
    }
    let response_ptr = memory.data_size(&caller);
    let required_size = response_ptr
        .checked_add(response.len())
        .ok_or_else(|| wasmtime::Error::msg("host response address overflows"))?;
    let required_pages = required_size.div_ceil(WASM_PAGE_SIZE_BYTES) as u64;
    let current_pages = memory.size(&caller);
    if required_pages > current_pages {
        memory
            .grow(&mut caller, required_pages - current_pages)
            .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
    }
    memory
        .write(&mut caller, response_ptr, response)
        .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
    let packed = ((response_ptr as u64) << 32) | response.len() as u64;
    Ok(packed as i64)
}

fn finish_call_before_timeout(
    engine: &Engine,
    timeout_millis: u64,
    call: impl FnOnce() -> Result<i64, wasmtime::Error>,
) -> Result<i64, wasmtime::Error> {
    let (completed_tx, completed_rx) = mpsc::channel();
    let timeout_engine = engine.clone();
    let watchdog = thread::spawn(move || {
        if completed_rx
            .recv_timeout(Duration::from_millis(timeout_millis))
            .is_err()
        {
            timeout_engine.increment_epoch();
        }
    });
    let result = call();
    let _ = completed_tx.send(());
    let _ = watchdog.join();
    result
}

type HostCall = unsafe extern "C" fn(
    user_data: *mut core::ffi::c_void,
    request_ptr: *const u8,
    request_len: usize,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32;

/// Host callback used only by the spike C ABI tests.
#[cfg(feature = "spike-probes")]
pub type BeakokitHostCall = HostCall;

#[cfg(feature = "spike-probes")]
struct HostState {
    host_calls: u32,
}

#[cfg(feature = "spike-probes")]
pub fn run_probe() -> Result<(), Box<dyn std::error::Error>> {
    protocol::run_roundtrip_probe()?;
    run_protocol_guest_call()?;
    run_protocol_host_call()?;
    run_host_call()?;
    run_guest_error()?;
    run_cancellation()?;

    Ok(())
}

#[cfg(feature = "spike-probes")]
fn run_protocol_guest_call() -> Result<(), Box<dyn std::error::Error>> {
    let request = serde_json::json!({
        "requestId": "guest-probe-1",
        "operation": "SEARCH",
        "payload": {
            "query": "frieren",
            "limit": 20,
            "offset": 0,
            "sort": "RELEVANCE",
            "typeAliases": [],
            "statusAliases": [],
            "includedGenreAliases": [],
            "excludedGenreAliases": [],
            "yearFrom": null,
            "yearTo": null
        },
        "protocolVersion": protocol::PROTOCOL_VERSION
    });
    protocol::Request::from_value(&request)?;

    let response = serde_json::json!({
        "requestId": "guest-probe-1",
        "payload": { "items": [] },
        "errorCode": null,
        "errorMessage": null,
        "protocolVersion": protocol::PROTOCOL_VERSION
    });
    let response_bytes = serde_json::to_vec(&response)?;
    let guest = format!(
        r#"
            (module
                (memory (export "memory") 2)
                (global $heap (mut i32) (i32.const 4096))
                (data (i32.const 0) "{}")
                (func (export "beakokit_reset")
                    i32.const 4096
                    global.set $heap
                )
                (func (export "beakokit_alloc") (param i32) (result i32)
                    global.get $heap
                    global.get $heap
                    local.get 0
                    i32.add
                    global.set $heap
                )
                (func (export "beakokit_call") (param i32 i32) (result i64)
                    i64.const {}
                )
            )
        "#,
        escape_wat_string(&String::from_utf8(response_bytes.clone())?),
        response_bytes.len(),
    );

    let engine = runtime_engine()?;
    let module = Module::new(&engine, wat::parse_str(guest)?)?;
    let mut store = Store::new(&engine, ());
    store.set_fuel(PROTOCOL_MAX_FUEL)?;
    store.set_epoch_deadline(1);
    let instance = Instance::new(&mut store, &module, &[])?;
    let memory = instance
        .get_memory(&mut store, "memory")
        .ok_or("guest memory export is missing")?;
    let reset = instance.get_typed_func::<(), ()>(&mut store, "beakokit_reset")?;
    let alloc = instance.get_typed_func::<i32, i32>(&mut store, "beakokit_alloc")?;
    let call = instance.get_typed_func::<(i32, i32), i64>(&mut store, "beakokit_call")?;
    reset.call(&mut store, ())?;
    let request_bytes = serde_json::to_vec(&request)?;
    let request_ptr = alloc.call(&mut store, request_bytes.len() as i32)?;
    memory.write(&mut store, request_ptr as usize, &request_bytes)?;
    let packed = call.call(&mut store, (request_ptr, request_bytes.len() as i32))? as u64;
    let response_ptr = (packed >> 32) as usize;
    let response_len = (packed & u64::from(u32::MAX)) as usize;
    let memory_size = memory.data(&store).len();
    if response_ptr > memory_size || response_len > memory_size - response_ptr {
        return Err("guest response points outside linear memory".into());
    }
    let mut returned = vec![0; response_len];
    memory.read(&store, response_ptr, &mut returned)?;
    let decoded: protocol::Response = serde_json::from_slice(&returned)?;
    let request = protocol::Request::from_value(&request)?;
    decoded.validate_for_request(&request)?;
    println!("guest ABI: allocated request at {request_ptr}, returned {response_len} bytes");

    Ok(())
}

#[cfg(feature = "spike-probes")]
fn escape_wat_string(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}

#[cfg(feature = "spike-probes")]
fn run_protocol_host_call() -> Result<(), Box<dyn std::error::Error>> {
    let request = sample_search_request();
    let response = run_protocol_host_call_request(&request.to_string())?;
    let response: protocol::Response = serde_json::from_str(&response)?;
    if response.request_id != "host-probe-1" {
        return Err("host response request ID does not match".into());
    }

    let details_request = serde_json::json!({
        "requestId": "host-details-probe-1",
        "operation": "DETAILS",
        "payload": { "id": "title-1" },
        "protocolVersion": protocol::PROTOCOL_VERSION
    });
    let details_response = run_protocol_host_call_request(&details_request.to_string())?;
    let details_response: protocol::Response = serde_json::from_str(&details_response)?;
    if details_response.request_id != "host-details-probe-1" {
        return Err("host details response request ID does not match".into());
    }
    let details_id = details_response
        .payload
        .as_ref()
        .and_then(|payload| payload.get("id"))
        .and_then(|id| id.as_str());
    if details_id != Some("title-1") {
        return Err("host details response payload does not match".into());
    }
    println!("host ABI: guest request reached host and returned a JSON response");
    Ok(())
}

#[cfg(feature = "spike-probes")]
fn run_protocol_host_call_request(
    request_json: &str,
) -> Result<String, Box<dyn std::error::Error>> {
    let module_bytes = wat::parse_str(include_str!("../fixtures/minimal-source.wat"))?;
    run_protocol_host_call_request_with_module(request_json, &module_bytes)
}

fn run_protocol_host_call_request_with_module(
    request_json: &str,
    module_bytes: &[u8],
) -> Result<String, Box<dyn std::error::Error>> {
    let engine = runtime_engine()?;
    let module = Module::new(&engine, module_bytes)?;

    let mut linker = Linker::new(&engine);
    linker.func_wrap(
        "host",
        "call",
        |mut caller: Caller<'_, ()>, ptr: i32, len: i32| -> Result<i64, wasmtime::Error> {
            if ptr < 0 || len < 0 || (len as usize) > PROTOCOL_MAX_REQUEST_BYTES {
                return Err(wasmtime::Error::msg("guest request range is invalid"));
            }
            let memory = caller
                .get_export("memory")
                .and_then(|export| export.into_memory())
                .ok_or_else(|| wasmtime::Error::msg("guest memory export is missing"))?;
            let mut request_bytes = vec![0; len as usize];
            memory
                .read(&caller, ptr as usize, &mut request_bytes)
                .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
            let request_value: serde_json::Value = serde_json::from_slice(&request_bytes)
                .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
            let request = protocol::Request::from_value(&request_value)
                .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
            let payload = match &request.operation {
                protocol::Operation::Search => serde_json::json!({ "items": [] }),
                protocol::Operation::Details => sample_title_payload(),
            };
            let response = serde_json::json!({
                "requestId": request.request_id,
                "payload": payload,
                "errorCode": null,
                "errorMessage": null,
                "protocolVersion": protocol::PROTOCOL_VERSION
            });
            let response_bytes = serde_json::to_vec(&response)
                .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
            write_host_response(caller, memory, &response_bytes)
        },
    )?;

    let mut store = Store::new(&engine, ());
    store.set_fuel(PROTOCOL_MAX_FUEL)?;
    store.set_epoch_deadline(1);
    let instance = linker.instantiate(&mut store, &module)?;
    let memory = instance
        .get_memory(&mut store, "memory")
        .ok_or("guest memory export is missing")?;
    let reset = instance.get_typed_func::<(), ()>(&mut store, "beakokit_reset")?;
    let alloc = instance.get_typed_func::<i32, i32>(&mut store, "beakokit_alloc")?;
    let call = instance.get_typed_func::<(i32, i32), i64>(&mut store, "beakokit_call")?;
    reset.call(&mut store, ())?;
    let request_value: serde_json::Value = serde_json::from_str(request_json)?;
    let request = protocol::Request::from_value(&request_value)?;
    let request_bytes = serde_json::to_vec(&request)?;
    let request_ptr = alloc.call(&mut store, request_bytes.len() as i32)?;
    memory.write(&mut store, request_ptr as usize, &request_bytes)?;
    let packed_response = call.call(&mut store, (request_ptr, request_bytes.len() as i32))? as u64;
    let response_ptr = (packed_response >> 32) as usize;
    let response_len = (packed_response & u64::from(u32::MAX)) as usize;
    if response_len > PROTOCOL_MAX_RESPONSE_BYTES {
        return Err("guest response exceeds the native limit".into());
    }
    let mut response_bytes = vec![0; response_len];
    memory.read(&store, response_ptr, &mut response_bytes)?;
    let response: protocol::Response = serde_json::from_slice(&response_bytes)?;
    response.validate_for_request(&request)?;
    Ok(String::from_utf8(response_bytes)?)
}

#[cfg(feature = "spike-probes")]
fn sample_search_request() -> serde_json::Value {
    serde_json::json!({
        "requestId": "host-probe-1",
        "operation": "SEARCH",
        "payload": {
            "query": "frieren",
            "limit": 20,
            "offset": 0,
            "sort": "RELEVANCE",
            "typeAliases": [],
            "statusAliases": [],
            "includedGenreAliases": [],
            "excludedGenreAliases": [],
            "yearFrom": null,
            "yearTo": null
        },
        "protocolVersion": protocol::PROTOCOL_VERSION
    })
}

fn sample_title_payload() -> serde_json::Value {
    serde_json::json!({
        "id": "title-1",
        "russianName": null,
        "englishName": "Title",
        "originalName": "Title",
        "japaneseName": null,
        "synonyms": [],
        "year": null,
        "type": null,
        "episodeCount": null,
        "posterUrl": null,
        "status": null,
        "description": null,
        "nextEpisodeAt": null,
        "genres": [],
        "ratings": [],
        "ageRating": null,
        "viewCount": null,
        "screenshots": [],
        "trailer": null,
        "sourceMaterial": null,
        "studios": [],
        "mainCharacters": [],
        "similarAnime": [],
        "franchiseAnime": [],
        "relatedAnime": [],
        "season": null,
        "availableEpisodeCount": null,
        "posterFallbackUrl": null
    })
}

/// C ABI smoke entry point for the future Android/iOS bridge.
/// Returns 0 on success and a negative value on failure or panic.
#[no_mangle]
#[cfg(feature = "spike-probes")]
pub extern "C" fn beakokit_runtime_probe() -> i32 {
    match catch_unwind(AssertUnwindSafe(run_probe)) {
        Ok(Ok(())) => 0,
        Ok(Err(_)) | Err(_) => -1,
    }
}

/// C ABI used by Swift/Objective-C and other native hosts for one protocol call.
/// The caller owns both buffers; `response_len` receives the required/written size.
///
/// # Safety
///
/// Every non-null pointer must be valid for the supplied byte range, and `response_len` must be
/// writable for one `usize`.
#[no_mangle]
#[cfg(feature = "spike-probes")]
pub unsafe extern "C" fn beakokit_runtime_protocol_call(
    request_ptr: *const u8,
    request_len: usize,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32 {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if response_len.is_null() {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        unsafe { *response_len = 0 };
        if request_len > PROTOCOL_MAX_REQUEST_BYTES || (request_ptr.is_null() && request_len != 0) {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        let request_bytes: &[u8] = if request_len == 0 {
            &[]
        } else {
            unsafe { std::slice::from_raw_parts(request_ptr, request_len) }
        };
        let request_json = match std::str::from_utf8(request_bytes) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_INVALID_REQUEST,
        };
        let request_value: serde_json::Value = match serde_json::from_str(request_json) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_INVALID_REQUEST,
        };
        if protocol::Request::from_value(&request_value).is_err() {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        let response = match run_protocol_host_call_request(request_json) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
        };
        let response_bytes = response.as_bytes();
        unsafe { *response_len = response_bytes.len() };
        if response_bytes.len() > response_capacity || response_ptr.is_null() {
            return PROTOCOL_CALL_BUFFER_TOO_SMALL;
        }
        unsafe {
            std::ptr::copy_nonoverlapping(
                response_bytes.as_ptr(),
                response_ptr,
                response_bytes.len(),
            );
        }
        PROTOCOL_CALL_OK
    }));
    result.unwrap_or(PROTOCOL_CALL_RUNTIME_FAILURE)
}

/// C ABI for executing one caller-supplied, already verified Wasm module.
///
/// # Safety
///
/// Every non-null pointer must be valid for the supplied byte range, and `response_len` must be
/// writable for one `usize`.
#[no_mangle]
pub unsafe extern "C" fn beakokit_runtime_protocol_call_with_module(
    module_ptr: *const u8,
    module_len: usize,
    request_ptr: *const u8,
    request_len: usize,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32 {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if response_len.is_null() {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        unsafe { *response_len = 0 };
        if module_len > PROTOCOL_MAX_MODULE_BYTES
            || (module_ptr.is_null() && module_len != 0)
            || request_len > PROTOCOL_MAX_REQUEST_BYTES
            || (request_ptr.is_null() && request_len != 0)
        {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }

        let module_bytes = if module_len == 0 {
            &[]
        } else {
            unsafe { std::slice::from_raw_parts(module_ptr, module_len) }
        };
        let request_bytes = if request_len == 0 {
            &[]
        } else {
            unsafe { std::slice::from_raw_parts(request_ptr, request_len) }
        };
        let request_json = match std::str::from_utf8(request_bytes) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_INVALID_REQUEST,
        };
        let request_value: serde_json::Value = match serde_json::from_str(request_json) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_INVALID_REQUEST,
        };
        if protocol::Request::from_value(&request_value).is_err() {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        let response = match run_protocol_host_call_request_with_module(request_json, module_bytes)
        {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
        };
        let response_bytes = response.as_bytes();
        unsafe { *response_len = response_bytes.len() };
        if response_bytes.len() > response_capacity || response_ptr.is_null() {
            return PROTOCOL_CALL_BUFFER_TOO_SMALL;
        }
        unsafe {
            std::ptr::copy_nonoverlapping(
                response_bytes.as_ptr(),
                response_ptr,
                response_bytes.len(),
            );
        }
        PROTOCOL_CALL_OK
    }));
    result.unwrap_or(PROTOCOL_CALL_RUNTIME_FAILURE)
}

/// Executes one verified Wasm module and delegates host calls to the supplied callback.
///
/// # Safety
///
/// Every non-null pointer must be valid for the supplied byte range, `response_len` must be
/// writable for one `usize`, and `host_call` must remain valid for the duration of this call.
#[no_mangle]
#[cfg(feature = "spike-probes")]
pub unsafe extern "C" fn beakokit_runtime_protocol_call_with_module_and_host(
    module_ptr: *const u8,
    module_len: usize,
    request_ptr: *const u8,
    request_len: usize,
    host_call: Option<HostCall>,
    user_data: *mut core::ffi::c_void,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32 {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if response_len.is_null() || host_call.is_none() {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        unsafe { *response_len = 0 };
        if module_len > PROTOCOL_MAX_MODULE_BYTES
            || (module_ptr.is_null() && module_len != 0)
            || request_len > PROTOCOL_MAX_REQUEST_BYTES
            || (request_ptr.is_null() && request_len != 0)
        {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        let module_bytes = if module_len == 0 {
            &[]
        } else {
            unsafe { std::slice::from_raw_parts(module_ptr, module_len) }
        };
        let request_bytes = if request_len == 0 {
            &[]
        } else {
            unsafe { std::slice::from_raw_parts(request_ptr, request_len) }
        };
        let request_json = match std::str::from_utf8(request_bytes) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_INVALID_REQUEST,
        };
        let request_value: serde_json::Value = match serde_json::from_str(request_json) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_INVALID_REQUEST,
        };
        if protocol::Request::from_value(&request_value).is_err() {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        let callback = host_call.expect("host callback was checked above");
        let response = match run_protocol_host_call_request_with_callback(
            request_json,
            module_bytes,
            callback,
            user_data,
        ) {
            Ok(value) => value,
            Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
        };
        unsafe { *response_len = response.len() };
        if response.len() > response_capacity || response_ptr.is_null() {
            return PROTOCOL_CALL_BUFFER_TOO_SMALL;
        }
        unsafe {
            std::ptr::copy_nonoverlapping(response.as_ptr(), response_ptr, response.len());
        }
        PROTOCOL_CALL_OK
    }));
    result.unwrap_or(PROTOCOL_CALL_RUNTIME_FAILURE)
}

fn run_protocol_host_call_request_with_callback(
    request_json: &str,
    module_bytes: &[u8],
    host_call: HostCall,
    user_data: *mut core::ffi::c_void,
) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    let user_data = user_data as usize;
    let engine = runtime_engine()?;
    let module = Module::new(&engine, module_bytes)?;
    let mut linker = Linker::new(&engine);
    linker.func_wrap(
        "host",
        "call",
        move |mut caller: Caller<'_, ()>, ptr: i32, len: i32| -> Result<i64, wasmtime::Error> {
            if ptr < 0 || len < 0 || (len as usize) > PROTOCOL_MAX_REQUEST_BYTES {
                return Err(wasmtime::Error::msg("guest request range is invalid"));
            }
            let memory = caller
                .get_export("memory")
                .and_then(|export| export.into_memory())
                .ok_or_else(|| wasmtime::Error::msg("guest memory export is missing"))?;
            let mut request_bytes = vec![0; len as usize];
            memory
                .read(&caller, ptr as usize, &mut request_bytes)
                .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
            let mut response_len = 0usize;
            let status = unsafe {
                host_call(
                    user_data as *mut core::ffi::c_void,
                    request_bytes.as_ptr(),
                    request_bytes.len(),
                    null_mut(),
                    0,
                    &mut response_len,
                )
            };
            if status != PROTOCOL_CALL_BUFFER_TOO_SMALL && status != PROTOCOL_CALL_OK {
                return Err(wasmtime::Error::msg(format!(
                    "host callback sizing failed with status {status}"
                )));
            }
            if response_len > PROTOCOL_MAX_RESPONSE_BYTES {
                return Err(wasmtime::Error::msg(
                    "host response exceeds the native limit",
                ));
            }
            let mut response_bytes = vec![0; response_len];
            let status = unsafe {
                host_call(
                    user_data as *mut core::ffi::c_void,
                    request_bytes.as_ptr(),
                    request_bytes.len(),
                    response_bytes.as_mut_ptr(),
                    response_bytes.len(),
                    &mut response_len,
                )
            };
            if status != PROTOCOL_CALL_OK {
                return Err(wasmtime::Error::msg(format!(
                    "host callback failed with status {status}"
                )));
            }
            response_bytes.truncate(response_len);
            write_host_response(caller, memory, &response_bytes)
        },
    )?;
    let mut store = Store::new(&engine, ());
    store.set_fuel(PROTOCOL_MAX_FUEL)?;
    store.set_epoch_deadline(1);
    let instance = linker.instantiate(&mut store, &module)?;
    let memory = instance
        .get_memory(&mut store, "memory")
        .ok_or("guest memory export is missing")?;
    let reset = instance.get_typed_func::<(), ()>(&mut store, "beakokit_reset")?;
    let alloc = instance.get_typed_func::<i32, i32>(&mut store, "beakokit_alloc")?;
    let call = instance.get_typed_func::<(i32, i32), i64>(&mut store, "beakokit_call")?;
    reset.call(&mut store, ())?;
    let request_value: serde_json::Value = serde_json::from_str(request_json)?;
    let request = protocol::Request::from_value(&request_value)?;
    let request_bytes = serde_json::to_vec(&request)?;
    let request_ptr = alloc.call(&mut store, request_bytes.len() as i32)?;
    memory.write(&mut store, request_ptr as usize, &request_bytes)?;
    let packed_response =
        finish_call_before_timeout(&engine, PROTOCOL_DEFAULT_TIMEOUT_MILLIS, || {
            call.call(&mut store, (request_ptr, request_bytes.len() as i32))
        })? as u64;
    let response_ptr = (packed_response >> 32) as usize;
    let response_len = (packed_response & u64::from(u32::MAX)) as usize;
    if response_len > PROTOCOL_MAX_RESPONSE_BYTES {
        return Err("guest response exceeds the native limit".into());
    }
    let mut response_bytes = vec![0; response_len];
    memory.read(&store, response_ptr, &mut response_bytes)?;
    let response: protocol::Response = serde_json::from_slice(&response_bytes)?;
    response.validate_for_request(&request)?;
    Ok(response_bytes)
}

/// Production JNI bridge for one verified binary Wasm module call.
#[cfg(feature = "android-production-jni")]
#[no_mangle]
pub extern "system" fn Java_org_akkirrai_beakokit_runtime_NativeSourceRuntimeBridge_protocolModuleCall(
    mut env: JNIEnv,
    _class: JClass,
    module: JByteArray,
    request: JString,
) -> jni::sys::jstring {
    let response =
        protocol_response_from_production_jni(&mut env, module, request).unwrap_or_else(|error| {
            serde_json::json!({
                "requestId": "jni-runtime-error",
                "payload": null,
                "errorCode": "RUNTIME_FAILURE",
                "errorMessage": error.to_string(),
                "protocolVersion": protocol::PROTOCOL_VERSION
            })
            .to_string()
        });
    env.new_string(response)
        .map(|value| value.into_raw())
        .unwrap_or(null_mut())
}

/// Production JNI bridge that delegates runtime host calls to a Java/Kotlin host object.
#[cfg(feature = "android-production-jni")]
#[no_mangle]
pub extern "system" fn Java_org_akkirrai_beakokit_runtime_NativeSourceRuntimeBridge_protocolModuleCallWithHost(
    mut env: JNIEnv,
    _class: JClass,
    module: JByteArray,
    request: JString,
    host: JObject,
) -> jni::sys::jstring {
    let request_id = env
        .get_string(&request)
        .ok()
        .map(String::from)
        .and_then(|value| serde_json::from_str::<serde_json::Value>(&value).ok())
        .and_then(|value| value.get("requestId")?.as_str().map(str::to_owned))
        .unwrap_or_else(|| "jni-host-runtime-error".to_owned());
    let response = protocol_response_from_production_jni_with_host(&mut env, module, request, host)
        .unwrap_or_else(|error| {
            serde_json::json!({
                "requestId": request_id,
                "payload": null,
                "errorCode": "RUNTIME_FAILURE",
                "errorMessage": error.to_string(),
                "protocolVersion": protocol::PROTOCOL_VERSION
            })
            .to_string()
        });
    env.new_string(response)
        .map(|value| value.into_raw())
        .unwrap_or(null_mut())
}

#[cfg(feature = "android-production-jni")]
struct JniHostState {
    vm: JavaVM,
    host: jni::objects::GlobalRef,
    pending_responses: Mutex<VecDeque<(Vec<u8>, Vec<u8>)>>,
}

#[cfg(feature = "android-production-jni")]
unsafe extern "C" fn jni_host_callback(
    user_data: *mut core::ffi::c_void,
    request_ptr: *const u8,
    request_len: usize,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32 {
    if user_data.is_null() || response_len.is_null() || (request_ptr.is_null() && request_len != 0)
    {
        return PROTOCOL_CALL_INVALID_REQUEST;
    }
    let state = &*(user_data as *const JniHostState);
    let request = if request_len == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(request_ptr, request_len)
    };
    if !response_ptr.is_null() {
        let mut pending = match state.pending_responses.lock() {
            Ok(pending) => pending,
            Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
        };
        let Some((expected_request, response)) = pending.pop_front() else {
            return PROTOCOL_CALL_RUNTIME_FAILURE;
        };
        if expected_request != request {
            return PROTOCOL_CALL_RUNTIME_FAILURE;
        }
        *response_len = response.len();
        if response_capacity < response.len() {
            return PROTOCOL_CALL_BUFFER_TOO_SMALL;
        }
        std::ptr::copy_nonoverlapping(response.as_ptr(), response_ptr, response.len());
        return PROTOCOL_CALL_OK;
    }
    let mut env = match state.vm.attach_current_thread_as_daemon() {
        Ok(env) => env,
        Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
    };
    let request_array = match env.byte_array_from_slice(request) {
        Ok(value) => value,
        Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
    };
    let result = match env.call_method(
        state.host.as_obj(),
        "call",
        "([B)[B",
        &[JValue::Object(request_array.as_ref())],
    ) {
        Ok(value) => value,
        Err(_) => {
            let _ = env.exception_clear();
            return PROTOCOL_CALL_RUNTIME_FAILURE;
        }
    };
    let response_object = match result.l() {
        Ok(value) if !value.is_null() => value,
        _ => return PROTOCOL_CALL_RUNTIME_FAILURE,
    };
    let response_array = JByteArray::from(response_object);
    let response = match env.convert_byte_array(&response_array) {
        Ok(value) => value,
        Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
    };
    if response.len() > PROTOCOL_MAX_RESPONSE_BYTES {
        return PROTOCOL_CALL_RUNTIME_FAILURE;
    }
    *response_len = response.len();
    let mut pending = match state.pending_responses.lock() {
        Ok(pending) => pending,
        Err(_) => return PROTOCOL_CALL_RUNTIME_FAILURE,
    };
    pending.push_back((request.to_vec(), response));
    PROTOCOL_CALL_BUFFER_TOO_SMALL
}

#[cfg(feature = "android-production-jni")]
fn protocol_response_from_production_jni_with_host(
    env: &mut JNIEnv,
    module: JByteArray,
    request: JString,
    host: JObject,
) -> Result<String, Box<dyn std::error::Error>> {
    let module_len = env.get_array_length(&module)? as usize;
    if module_len > PROTOCOL_MAX_MODULE_BYTES {
        return Err("caller-supplied module exceeds the native limit".into());
    }
    let mut signed_module = vec![0_i8; module_len];
    env.get_byte_array_region(&module, 0, &mut signed_module)?;
    let module_bytes: Vec<u8> = signed_module.into_iter().map(|byte| byte as u8).collect();
    let request: String = env.get_string(&request)?.into();
    let host = JniHostState {
        vm: env.get_java_vm()?,
        host: env.new_global_ref(host)?,
        pending_responses: Mutex::new(VecDeque::new()),
    };
    let user_data = (&host as *const JniHostState).cast_mut().cast();
    let response = run_protocol_host_call_request_with_callback(
        &request,
        &module_bytes,
        jni_host_callback,
        user_data,
    )?;
    Ok(String::from_utf8(response)?)
}

#[cfg(feature = "android-production-jni")]
fn protocol_response_from_production_jni(
    env: &mut JNIEnv,
    module: JByteArray,
    request: JString,
) -> Result<String, Box<dyn std::error::Error>> {
    let module_len = env.get_array_length(&module)? as usize;
    if module_len > PROTOCOL_MAX_MODULE_BYTES {
        return Err("caller-supplied module exceeds the native limit".into());
    }
    let mut signed_module = vec![0_i8; module_len];
    env.get_byte_array_region(&module, 0, &mut signed_module)?;
    let module_bytes: Vec<u8> = signed_module.into_iter().map(|byte| byte as u8).collect();
    let request: String = env.get_string(&request)?.into();
    let request_bytes = request.as_bytes();
    let mut response_len = 0usize;
    let status = unsafe {
        beakokit_runtime_protocol_call_with_module(
            module_bytes.as_ptr(),
            module_bytes.len(),
            request_bytes.as_ptr(),
            request_bytes.len(),
            null_mut(),
            0,
            &mut response_len,
        )
    };
    if status != PROTOCOL_CALL_BUFFER_TOO_SMALL {
        return Err(format!("native runtime sizing call failed with status {status}").into());
    }
    if response_len > PROTOCOL_MAX_RESPONSE_BYTES {
        return Err("native runtime response exceeds the native limit".into());
    }
    let mut response = vec![0u8; response_len];
    let status = unsafe {
        beakokit_runtime_protocol_call_with_module(
            module_bytes.as_ptr(),
            module_bytes.len(),
            request_bytes.as_ptr(),
            request_bytes.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };
    if status != PROTOCOL_CALL_OK {
        return Err(format!("native runtime call failed with status {status}").into());
    }
    response.truncate(response_len);
    Ok(String::from_utf8(response)?)
}

/// JNI shim used only by the temporary Android instrumentation harness.
#[cfg(feature = "android-harness")]
#[no_mangle]
/// # Safety
///
/// JNI provides valid environment and receiver pointers for this entry point.
pub unsafe extern "system" fn Java_org_akkirrai_hibiki_WasmtimeRuntimeSmokeTest_probe(
    _env: *mut core::ffi::c_void,
    _receiver: *mut core::ffi::c_void,
) -> i32 {
    beakokit_runtime_probe()
}

#[cfg(feature = "android-harness")]
#[no_mangle]
/// # Safety
///
/// JNI provides valid environment and receiver pointers for this entry point.
pub unsafe extern "system" fn Java_org_akkirrai_wasmtime_WasmtimeRuntimeSmokeActivity_probe(
    _env: *mut core::ffi::c_void,
    _receiver: *mut core::ffi::c_void,
) -> i32 {
    beakokit_runtime_probe()
}

#[cfg(feature = "android-harness")]
#[no_mangle]
pub extern "system" fn Java_org_akkirrai_wasmtime_WasmtimeRuntimeSmokeActivity_protocolProbe(
    mut env: JNIEnv,
    _class: JClass,
    request: JString,
) -> jni::sys::jstring {
    let response = match protocol_response_from_jni(&mut env, request) {
        Ok(response) => response,
        Err(error) => serde_json::json!({
            "requestId": "jni-error",
            "payload": null,
            "errorCode": "INVALID_REQUEST",
            "errorMessage": error.to_string(),
            "protocolVersion": protocol::PROTOCOL_VERSION
        })
        .to_string(),
    };
    env.new_string(response)
        .map(|value| value.into_raw())
        .unwrap_or(null_mut())
}

#[cfg(feature = "android-harness")]
#[no_mangle]
pub extern "system" fn Java_org_akkirrai_wasmtime_WasmtimeRuntimeSmokeActivity_protocolModuleProbe(
    mut env: JNIEnv,
    _class: JClass,
    module: JByteArray,
    request: JString,
) -> jni::sys::jstring {
    let response = match protocol_response_from_jni_with_module(&mut env, module, request) {
        Ok(response) => response,
        Err(error) => serde_json::json!({
            "requestId": "jni-module-error",
            "payload": null,
            "errorCode": "RUNTIME_FAILURE",
            "errorMessage": error.to_string(),
            "protocolVersion": protocol::PROTOCOL_VERSION
        })
        .to_string(),
    };
    env.new_string(response)
        .map(|value| value.into_raw())
        .unwrap_or(null_mut())
}

#[cfg(feature = "android-harness")]
#[cfg(feature = "android-harness")]
fn protocol_response_from_jni(
    env: &mut JNIEnv,
    request: JString,
) -> Result<String, Box<dyn std::error::Error>> {
    let request: String = env.get_string(&request)?.into();
    run_protocol_host_call_request(&request)
}

#[cfg(feature = "android-harness")]
fn protocol_response_from_jni_with_module(
    env: &mut JNIEnv,
    module: JByteArray,
    request: JString,
) -> Result<String, Box<dyn std::error::Error>> {
    let module_len = env.get_array_length(&module)? as usize;
    if module_len > PROTOCOL_MAX_MODULE_BYTES {
        return Err("caller-supplied module exceeds the native limit".into());
    }
    let mut signed_bytes = vec![0_i8; module_len];
    env.get_byte_array_region(&module, 0, &mut signed_bytes)?;
    let module_bytes: Vec<u8> = signed_bytes.into_iter().map(|byte| byte as u8).collect();
    let module_bytes = if module_bytes
        .iter()
        .copied()
        .find(|byte| !byte.is_ascii_whitespace())
        == Some(b'(')
    {
        wat::parse_bytes(&module_bytes)?.into_owned()
    } else {
        module_bytes
    };
    let request: String = env.get_string(&request)?.into();
    run_protocol_host_call_request_with_module(&request, &module_bytes)
}

#[cfg(feature = "spike-probes")]
fn run_host_call() -> Result<(), Box<dyn std::error::Error>> {
    let engine = Engine::default();
    let module = Module::new(
        &engine,
        wat::parse_str(
            r#"
                (module
                    (import "host" "add_one" (func $add_one (param i32) (result i32)))
                    (func (export "run") (param i32) (result i32)
                        local.get 0
                        call $add_one
                    )
                )
            "#,
        )?,
    )?;

    let mut linker = Linker::new(&engine);
    linker.func_wrap(
        "host",
        "add_one",
        |mut caller: wasmtime::Caller<'_, HostState>, value: i32| -> i32 {
            caller.data_mut().host_calls += 1;
            value + 1
        },
    )?;

    let mut store = Store::new(&engine, HostState { host_calls: 0 });
    let instance = linker.instantiate(&mut store, &module)?;
    let run = instance.get_typed_func::<i32, i32>(&mut store, "run")?;
    let result = run.call(&mut store, 41)?;

    assert_eq!(result, 42);
    assert_eq!(store.data().host_calls, 1);
    println!(
        "host call: guest result={result}; host calls={}",
        store.data().host_calls
    );

    Ok(())
}

#[cfg(feature = "spike-probes")]
fn run_guest_error() -> Result<(), Box<dyn std::error::Error>> {
    let engine = Engine::default();
    let module = Module::new(
        &engine,
        wat::parse_str(r#"(module (func (export "fail") unreachable))"#)?,
    )?;
    let mut store = Store::new(&engine, ());
    let instance = Instance::new(&mut store, &module, &[])?;
    let fail = instance.get_typed_func::<(), ()>(&mut store, "fail")?;
    let error = fail.call(&mut store, ());

    assert!(error.is_err(), "guest trap must become a runtime error");
    println!("runtime error: guest trap captured");

    Ok(())
}

#[cfg(feature = "spike-probes")]
fn run_cancellation() -> Result<(), Box<dyn std::error::Error>> {
    let mut config = Config::new();
    config.epoch_interruption(true);
    let engine = Engine::new(&config)?;
    let module = Module::new(
        &engine,
        wat::parse_str(r#"(module (func (export "run") (loop br 0)))"#)?,
    )?;
    let mut store = Store::new(&engine, ());
    store.epoch_deadline_trap();
    store.set_epoch_deadline(1);
    let instance = Instance::new(&mut store, &module, &[])?;
    let run = instance.get_typed_func::<(), ()>(&mut store, "run")?;

    let interrupt_engine = engine.clone();
    let interrupter = thread::spawn(move || {
        thread::sleep(Duration::from_millis(50));
        interrupt_engine.increment_epoch();
    });

    let result = run.call(&mut store, ());
    interrupter.join().expect("interrupter thread must finish");

    assert!(result.is_err(), "infinite guest must be interrupted");
    println!("cancellation: guest execution interrupted");

    Ok(())
}
