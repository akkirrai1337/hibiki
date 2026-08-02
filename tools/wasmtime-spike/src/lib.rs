use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr::null_mut;
use std::thread;
use std::time::Duration;

use jni::objects::{JClass, JString};
use jni::JNIEnv;
use wasmtime::{Caller, Config, Engine, Instance, Linker, Module, Store};

pub mod protocol;

pub const PROTOCOL_CALL_OK: i32 = 0;
pub const PROTOCOL_CALL_INVALID_REQUEST: i32 = -2;
pub const PROTOCOL_CALL_BUFFER_TOO_SMALL: i32 = -3;
pub const PROTOCOL_CALL_RUNTIME_FAILURE: i32 = -4;

struct HostState {
    host_calls: u32,
}

pub fn run_probe() -> Result<(), Box<dyn std::error::Error>> {
    protocol::run_roundtrip_probe()?;
    run_protocol_guest_call()?;
    run_protocol_host_call()?;
    run_host_call()?;
    run_guest_error()?;
    run_cancellation()?;

    Ok(())
}

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

    let engine = Engine::default();
    let module = Module::new(&engine, wat::parse_str(guest)?)?;
    let mut store = Store::new(&engine, ());
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

fn escape_wat_string(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}

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

fn run_protocol_host_call_request(
    request_json: &str,
) -> Result<String, Box<dyn std::error::Error>> {
    let engine = Engine::default();
    let module = Module::new(
        &engine,
        wat::parse_str(
            r#"
                (module
                    (import "host" "call" (func $host_call (param i32 i32) (result i64)))
                    (memory (export "memory") 2)
                    (global $heap (mut i32) (i32.const 4096))
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
                        local.get 0
                        local.get 1
                        call $host_call
                    )
                )
            "#,
        )?,
    )?;

    let mut linker = Linker::new(&engine);
    linker.func_wrap(
        "host",
        "call",
        |mut caller: Caller<'_, ()>, ptr: i32, len: i32| -> Result<i64, wasmtime::Error> {
            if ptr < 0 || len < 0 {
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
            memory
                .write(&mut caller, 0, &response_bytes)
                .map_err(|error| wasmtime::Error::msg(error.to_string()))?;
            Ok(response_bytes.len() as i64)
        },
    )?;

    let mut store = Store::new(&engine, ());
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
    let mut response_bytes = vec![0; response_len];
    memory.read(&store, response_ptr, &mut response_bytes)?;
    let response: protocol::Response = serde_json::from_slice(&response_bytes)?;
    response.validate_for_request(&request)?;
    Ok(String::from_utf8(response_bytes)?)
}

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
pub extern "C" fn beakokit_runtime_probe() -> i32 {
    match catch_unwind(AssertUnwindSafe(run_probe)) {
        Ok(Ok(())) => 0,
        Ok(Err(_)) | Err(_) => -1,
    }
}

/// C ABI used by Swift/Objective-C and other native hosts for one protocol call.
/// The caller owns both buffers; `response_len` receives the required/written size.
#[no_mangle]
pub unsafe extern "C" fn beakokit_runtime_protocol_call(
    request_ptr: *const u8,
    request_len: usize,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32 {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if response_len.is_null() || (request_ptr.is_null() && request_len != 0) {
            return PROTOCOL_CALL_INVALID_REQUEST;
        }
        unsafe { *response_len = 0 };
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

/// JNI shim used only by the temporary Android instrumentation harness.
#[no_mangle]
pub unsafe extern "system" fn Java_org_akkirrai_hibiki_WasmtimeRuntimeSmokeTest_probe(
    _env: *mut core::ffi::c_void,
    _receiver: *mut core::ffi::c_void,
) -> i32 {
    beakokit_runtime_probe()
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_akkirrai_wasmtime_WasmtimeRuntimeSmokeActivity_probe(
    _env: *mut core::ffi::c_void,
    _receiver: *mut core::ffi::c_void,
) -> i32 {
    beakokit_runtime_probe()
}

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

fn protocol_response_from_jni(
    env: &mut JNIEnv,
    request: JString,
) -> Result<String, Box<dyn std::error::Error>> {
    let request: String = env.get_string(&request)?.into();
    run_protocol_host_call_request(&request)
}

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
