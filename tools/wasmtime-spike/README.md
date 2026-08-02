# Wasmtime runtime spike

This is an isolated Windows-only experiment for the external-source runtime.
It verifies a host call, guest runtime errors, and epoch-based cancellation.
The library also exports `beakokit_runtime_probe` as a minimal C ABI entry point.
The probe also verifies the guest call ABI: the host resets the guest arena,
allocates request memory through `beakokit_alloc(len)`, writes a versioned JSON
request, and calls `beakokit_call(ptr, len)`. The guest returns a packed
response pointer and length. `beakokit_reset()` starts the next call arena.

It is not part of the application build and must not replace the existing
built-in source path.

Run it from this directory with:

```powershell
& "$env:USERPROFILE\.cargo\bin\cargo.exe" run
```

The Android cross-build was verified with NDK `28.2.13676358`:

```powershell
$ndk = "$env:LOCALAPPDATA\Android\Sdk\ndk\28.2.13676358"
$bin = "$ndk\toolchains\llvm\prebuilt\windows-x86_64\bin"
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = "$bin\aarch64-linux-android21-clang.cmd"
$env:CC_aarch64_linux_android = "$bin\aarch64-linux-android21-clang.cmd"
$env:AR_aarch64_linux_android = "$bin\llvm-ar.exe"
cargo build --target aarch64-linux-android
```

The Android library is emitted as
`target/aarch64-linux-android/debug/libwasmtime_spike.so`. The exported ABI
symbol can be checked with the NDK `llvm-nm` tool before adding a Kotlin/JNI
or Swift bridge.

An independent Android harness under `android-harness/` packages the x86_64
library, installs a tiny Java activity on an emulator, and displays
`Wasmtime JNI probe: OK` when the native probe returns success. It is kept
outside the main application so the existing production build is untouched.
