#![cfg(feature = "spike-probes")]

use wasmtime_spike::{
    beakokit_runtime_protocol_call, beakokit_runtime_protocol_call_with_module,
    beakokit_runtime_protocol_call_with_module_and_host, BeakokitHostCall,
    PROTOCOL_CALL_BUFFER_TOO_SMALL, PROTOCOL_CALL_INVALID_REQUEST, PROTOCOL_CALL_OK,
    PROTOCOL_CALL_RUNTIME_FAILURE,
    PROTOCOL_MAX_MODULE_BYTES, PROTOCOL_MAX_REQUEST_BYTES,
};

fn request() -> Vec<u8> {
    br#"{"requestId":"ffi-probe-1","operation":"SEARCH","payload":{"query":"frieren","limit":20,"offset":0,"sort":"RELEVANCE","typeAliases":[],"statusAliases":[],"includedGenreAliases":[],"excludedGenreAliases":[],"yearFrom":null,"yearTo":null},"protocolVersion":1}"#.to_vec()
}

fn details_request() -> Vec<u8> {
    br#"{"requestId":"ffi-details-1","operation":"DETAILS","payload":{"id":"title-1"},"protocolVersion":1}"#.to_vec()
}

unsafe extern "C" fn counting_host_callback(
    user_data: *mut core::ffi::c_void,
    _request_ptr: *const u8,
    _request_len: usize,
    _response_ptr: *mut u8,
    _response_capacity: usize,
    _response_len: *mut usize,
) -> i32 {
    let calls = &*(user_data as *const std::sync::atomic::AtomicUsize);
    calls.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    PROTOCOL_CALL_RUNTIME_FAILURE
}

unsafe extern "C" fn details_host_callback(
    _user_data: *mut core::ffi::c_void,
    request_ptr: *const u8,
    request_len: usize,
    response_ptr: *mut u8,
    response_capacity: usize,
    response_len: *mut usize,
) -> i32 {
    if response_len.is_null() || (request_ptr.is_null() && request_len != 0) {
        return PROTOCOL_CALL_INVALID_REQUEST;
    }
    let expected_request = details_request();
    let request = if request_len == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(request_ptr, request_len)
    };
    if request != expected_request {
        return PROTOCOL_CALL_INVALID_REQUEST;
    }
    let response = br#"{"requestId":"ffi-details-1","payload":{"id":"title-1","originalName":"Title","synonyms":[],"genres":[],"screenshots":[],"studios":[],"ratings":[],"mainCharacters":[],"similarAnime":[],"franchiseAnime":[],"relatedAnime":[]},"errorCode":null,"errorMessage":null,"protocolVersion":1}"#;
    *response_len = response.len();
    if response_ptr.is_null() || response_capacity < response.len() {
        return PROTOCOL_CALL_BUFFER_TOO_SMALL;
    }
    std::ptr::copy_nonoverlapping(response.as_ptr(), response_ptr, response.len());
    PROTOCOL_CALL_OK
}

#[test]
fn c_abi_writes_wasm_backed_response_into_caller_buffer() {
    let request = request();
    let mut response = vec![0; 512];
    let mut response_len = 0;

    let status = unsafe {
        beakokit_runtime_protocol_call(
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_OK);
    let response = String::from_utf8(response[..response_len].to_vec()).unwrap();
    assert!(response.contains("ffi-probe-1"));
}

#[test]
fn c_abi_handles_details_response() {
    let request = details_request();
    let mut response = vec![0; 2048];
    let mut response_len = 0;

    let status = unsafe {
        beakokit_runtime_protocol_call(
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_OK);
    let response = String::from_utf8(response[..response_len].to_vec()).unwrap();
    assert!(response.contains("ffi-details-1"));
    assert!(response.contains("\"id\":\"title-1\""));
}

#[test]
fn c_abi_reports_required_size_without_overwriting_small_buffer() {
    let request = request();
    let mut response = [0xA5; 1];
    let mut response_len = 0;

    let status = unsafe {
        beakokit_runtime_protocol_call(
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_BUFFER_TOO_SMALL);
    assert!(response_len > response.len());
    assert_eq!(response, [0xA5; 1]);
}

#[test]
fn c_abi_rejects_malformed_request_without_writing_response() {
    let request = br#"{"requestId":"broken"}"#;
    let mut response = [0xA5; 16];
    let mut response_len = 123;

    let status = unsafe {
        beakokit_runtime_protocol_call(
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_INVALID_REQUEST);
    assert_eq!(response_len, 0);
    assert_eq!(response, [0xA5; 16]);
}

#[test]
fn c_abi_rejects_missing_length_output_pointer() {
    let request = request();
    let mut response = [0xA5; 16];

    let status = unsafe {
        beakokit_runtime_protocol_call(
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            std::ptr::null_mut(),
        )
    };

    assert_eq!(status, PROTOCOL_CALL_INVALID_REQUEST);
    assert_eq!(response, [0xA5; 16]);
}

#[test]
fn c_abi_rejects_request_over_the_native_limit() {
    let request = vec![b' '; PROTOCOL_MAX_REQUEST_BYTES + 1];
    let mut response = [0xA5; 16];
    let mut response_len = 123;

    let status = unsafe {
        beakokit_runtime_protocol_call(
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_INVALID_REQUEST);
    assert_eq!(response_len, 0);
    assert_eq!(response, [0xA5; 16]);
}

#[test]
fn c_abi_executes_caller_supplied_wasm_module() {
    let module = wat::parse_str(include_str!("../fixtures/minimal-source.wat")).unwrap();
    let request = details_request();
    let mut response = vec![0; 2048];
    let mut response_len = 0;

    let status = unsafe {
        beakokit_runtime_protocol_call_with_module(
            module.as_ptr(),
            module.len(),
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_OK);
    let response = String::from_utf8(response[..response_len].to_vec()).unwrap();
    assert!(response.contains("ffi-details-1"));
}

#[test]
fn c_abi_roundtrips_a_guest_host_callback() {
    let module = wat::parse_str(include_str!("../fixtures/minimal-source.wat")).unwrap();
    let request = details_request();
    let mut response = vec![0; 2048];
    let mut response_len = 0;

    let status = unsafe {
        beakokit_runtime_protocol_call_with_module_and_host(
            module.as_ptr(),
            module.len(),
            request.as_ptr(),
            request.len(),
            Some(details_host_callback as BeakokitHostCall),
            std::ptr::null_mut(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_OK);
    let response = String::from_utf8(response[..response_len].to_vec()).unwrap();
    assert!(response.contains("ffi-details-1"));
    assert!(response.contains("\"id\":\"title-1\""));
}

#[test]
fn c_abi_rejects_an_oversized_guest_host_request_before_allocating() {
    let module = wat::parse_str(
        r#"
        (module
            (import "host" "call" (func $host_call (param i32 i32) (result i64)))
            (memory (export "memory") 1)
            (global $heap (mut i32) (i32.const 4096))
            (func (export "beakokit_reset")
                i32.const 4096
                global.set $heap)
            (func (export "beakokit_alloc") (param i32) (result i32)
                global.get $heap
                global.get $heap
                local.get 0
                i32.add
                global.set $heap)
            (func (export "beakokit_call") (param i32 i32) (result i64)
                i32.const 0
                i32.const 2147483647
                call $host_call)
        )
        "#,
    )
    .unwrap();
    let request = details_request();
    let mut response = vec![0; 2048];
    let mut response_len = 0;
    let host_calls = std::sync::atomic::AtomicUsize::new(0);

    let status = unsafe {
        beakokit_runtime_protocol_call_with_module_and_host(
            module.as_ptr(),
            module.len(),
            request.as_ptr(),
            request.len(),
            Some(counting_host_callback as BeakokitHostCall),
            (&host_calls as *const std::sync::atomic::AtomicUsize)
                .cast_mut()
                .cast(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_RUNTIME_FAILURE);
    assert_eq!(host_calls.load(std::sync::atomic::Ordering::Relaxed), 0);
    assert_eq!(response_len, 0);
}

#[test]
fn c_abi_rejects_module_over_the_native_limit() {
    let module = vec![0; PROTOCOL_MAX_MODULE_BYTES + 1];
    let request = request();
    let mut response = [0xA5; 16];
    let mut response_len = 123;

    let status = unsafe {
        beakokit_runtime_protocol_call_with_module(
            module.as_ptr(),
            module.len(),
            request.as_ptr(),
            request.len(),
            response.as_mut_ptr(),
            response.len(),
            &mut response_len,
        )
    };

    assert_eq!(status, PROTOCOL_CALL_INVALID_REQUEST);
    assert_eq!(response_len, 0);
    assert_eq!(response, [0xA5; 16]);
}
