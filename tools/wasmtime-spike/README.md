# Wasmtime runtime spike

This is an isolated Windows-only experiment for the external-source runtime.
It verifies a host call, guest runtime errors, and epoch-based cancellation.

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
