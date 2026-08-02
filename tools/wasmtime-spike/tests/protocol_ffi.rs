use wasmtime_spike::{
    beakokit_runtime_protocol_call, beakokit_runtime_protocol_call_with_module,
    PROTOCOL_CALL_BUFFER_TOO_SMALL, PROTOCOL_CALL_INVALID_REQUEST, PROTOCOL_CALL_OK,
    PROTOCOL_MAX_MODULE_BYTES, PROTOCOL_MAX_REQUEST_BYTES,
};

fn request() -> Vec<u8> {
    br#"{"requestId":"ffi-probe-1","operation":"SEARCH","payload":{"query":"frieren","limit":20,"offset":0,"sort":"RELEVANCE","typeAliases":[],"statusAliases":[],"includedGenreAliases":[],"excludedGenreAliases":[],"yearFrom":null,"yearTo":null},"protocolVersion":1}"#.to_vec()
}

fn details_request() -> Vec<u8> {
    br#"{"requestId":"ffi-details-1","operation":"DETAILS","payload":{"id":"title-1"},"protocolVersion":1}"#.to_vec()
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
