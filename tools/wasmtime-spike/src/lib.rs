use std::panic::{catch_unwind, AssertUnwindSafe};
use std::thread;
use std::time::Duration;

use wasmtime::{Config, Engine, Instance, Linker, Module, Store};

pub mod protocol;

struct HostState {
    host_calls: u32,
}

pub fn run_probe() -> Result<(), Box<dyn std::error::Error>> {
    protocol::run_roundtrip_probe()?;
    run_host_call()?;
    run_guest_error()?;
    run_cancellation()?;

    Ok(())
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
