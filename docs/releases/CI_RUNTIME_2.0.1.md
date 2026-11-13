# CI Runtime 2.0.1

This release packages the Rust CI Runtime as a hot-update module for ALVM Studio.

- Runtime API: 1
- Supported bytecode: ALRM .room v9
- Android ABIs: arm64-v8a and armeabi-v7a
- Bundled APK fallback remains runtime 2.0.0
- Hot update: 2.0.1

## Changes

- Corrected package and native-library size-limit diagnostics to report the actual enforced limits.
- Corrected the FFI panic fallback so it reports the version of the compiled runtime module.
- Added regression tests for the size limits and runtime-version fallback.

The release does not change the AL compiler, JNI ABI, Runtime API or .room format. Existing .room v9 programs remain compatible.
