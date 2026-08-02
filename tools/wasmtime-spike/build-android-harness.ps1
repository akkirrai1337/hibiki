$ErrorActionPreference = "Stop"

$spikeRoot = $PSScriptRoot
$ndkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk\28.2.13676358"
$toolchainBin = Join-Path $ndkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin"
$linker = Join-Path $toolchainBin "x86_64-linux-android21-clang.cmd"
$archiver = Join-Path $toolchainBin "llvm-ar.exe"

if (-not (Test-Path -LiteralPath $linker)) {
    throw "Android NDK linker was not found: $linker"
}

$env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER = $linker
$env:CC_x86_64_linux_android = $linker
$env:AR_x86_64_linux_android = $archiver

Push-Location $spikeRoot
try {
    & cargo build --target x86_64-linux-android --features android-harness
    if ($LASTEXITCODE -ne 0) { throw "cargo build failed" }

    $library = Join-Path $spikeRoot "target\x86_64-linux-android\debug\libwasmtime_spike.so"
    $jniDirectory = Join-Path $spikeRoot "android-harness\app\src\main\jniLibs\x86_64"
    if (-not (Test-Path -LiteralPath $library)) {
        throw "Native runtime library was not produced: $library"
    }
    New-Item -ItemType Directory -Force -Path $jniDirectory | Out-Null
    Copy-Item -LiteralPath $library -Destination (Join-Path $jniDirectory "libwasmtime_spike_v2.so") -Force

    & "$spikeRoot\..\..\gradlew.bat" -p "$spikeRoot\android-harness" :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android harness build failed" }
} finally {
    Pop-Location
}
