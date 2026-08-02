$ErrorActionPreference = "Stop"

$spikeRoot = $PSScriptRoot
$ndkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk\28.2.13676358"
$toolchainBin = Join-Path $ndkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin"
$cargo = Join-Path $env:USERPROFILE ".cargo\bin\cargo.exe"
$nm = Join-Path $toolchainBin "llvm-nm.exe"

if (-not (Test-Path -LiteralPath $cargo)) {
    throw "Cargo was not found: $cargo"
}
if (-not (Test-Path -LiteralPath $nm)) {
    throw "NDK llvm-nm was not found: $nm"
}

$targets = @(
    @{ Name = "aarch64-linux-android"; Abi = "arm64-v8a"; Linker = "aarch64-linux-android21-clang.cmd"; Env = "AARCH64_LINUX_ANDROID" },
    @{ Name = "x86_64-linux-android"; Abi = "x86_64"; Linker = "x86_64-linux-android21-clang.cmd"; Env = "X86_64_LINUX_ANDROID" }
)

Push-Location $spikeRoot
try {
    foreach ($target in $targets) {
        $linker = Join-Path $toolchainBin $target.Linker
        if (-not (Test-Path -LiteralPath $linker)) {
            throw "Android NDK linker was not found: $linker"
        }

        Set-Item -Path "Env:CARGO_TARGET_$($target.Env)_LINKER" -Value $linker
        Set-Item -Path "Env:CC_$($target.Name.Replace('-', '_'))" -Value $linker
        Set-Item -Path "Env:AR_$($target.Name.Replace('-', '_'))" -Value (Join-Path $toolchainBin "llvm-ar.exe")

        & $cargo build --target $target.Name --lib --features android-production-jni
        if ($LASTEXITCODE -ne 0) {
            throw "Production Android runtime build failed for $($target.Name)"
        }

        $library = Join-Path $spikeRoot "target\$($target.Name)\debug\libwasmtime_spike.so"
        if (-not (Test-Path -LiteralPath $library)) {
            throw "Production runtime library was not produced: $library"
        }
        $symbol = & $nm -g $library | Select-String "beakokit_runtime_protocol_call_with_module"
        if (-not $symbol) {
            throw "Production runtime export was not found in $library"
        }
        $jniSymbol = & $nm -g $library | Select-String "Java_org_akkirrai_beakokit_runtime_NativeSourceRuntimeBridge_protocolModuleCall"
        if (-not $jniSymbol) {
            throw "Production JNI export was not found in $library"
        }
        $hostJniSymbol = & $nm -g $library | Select-String "Java_org_akkirrai_beakokit_runtime_NativeSourceRuntimeBridge_protocolModuleCallWithHost"
        if (-not $hostJniSymbol) {
            throw "Production host callback JNI export was not found in $library"
        }
        Write-Host "Verified $($target.Abi): $library"
    }
} finally {
    Pop-Location
}
