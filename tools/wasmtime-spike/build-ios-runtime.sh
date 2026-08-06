#!/usr/bin/env bash
set -euo pipefail

spike_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cargo_bin="${CARGO:-cargo}"

targets=(
  "aarch64-apple-ios"
  "aarch64-apple-ios-sim"
  "x86_64-apple-ios"
)

for target in "${targets[@]}"; do
  "${cargo_bin}" build --target "${target}" --lib --release --manifest-path "${spike_root}/Cargo.toml"
done

device_library="${spike_root}/target/aarch64-apple-ios/release/libwasmtime_spike.a"
simulator_arm64_library="${spike_root}/target/aarch64-apple-ios-sim/release/libwasmtime_spike.a"
simulator_x86_64_library="${spike_root}/target/x86_64-apple-ios/release/libwasmtime_spike.a"
simulator_library="${spike_root}/target/ios-simulator/release/libwasmtime_spike.a"

for library in "${device_library}" "${simulator_arm64_library}" "${simulator_x86_64_library}"; do
  test -f "${library}" || {
    echo "Rust iOS runtime library was not produced: ${library}" >&2
    exit 1
  }
done

mkdir -p "$(dirname "${simulator_library}")"
lipo -create "${simulator_arm64_library}" "${simulator_x86_64_library}" -output "${simulator_library}"

for library in "${device_library}" "${simulator_library}"; do
  # Xcode 26.3's nm cannot read LLVM 22 object metadata emitted by newer Rust
  # toolchains. Check the archive for the exported ABI name without parsing
  # every object member; the final Xcode linker remains the authoritative check.
  grep -a -q "beakokit_runtime_protocol_call_with_module_and_host" "${library}" || {
    echo "Host callback ABI export was not found in ${library}" >&2
    exit 1
  }
  echo "Verified ${library}"
done
