# CI Runtime: Rust ALVM bytecode engine

## Responsibilities

CI Runtime is the shared runtime identity used by ALVM Studio and Conshite. This repository implements the ALVM bytecode adapter. The Conshite adapter is maintained in the Conshite repository; the two adapters are not yet one universal binary. The shared target is a versioned runtime API, module ABI, loader and platform service layer.

## ALVM execution pipeline

1. The C++17 compiler parses AL source, performs semantic/type analysis, lowers it to bytecode and writes a binary `.room` v9 file.
2. The Rust CI Runtime reads the `.room` file, validates the header, metadata, references, control-flow jumps and operand-stack depths, then interprets the verified instruction stream.
3. The Android Java host calls the Rust native runtime through JNI. Java renders native Android Views for AL UI instructions; it does not interpret AL bytecode or implement AL program logic.

The Android Run, Execute and UI-event paths use the Rust CI Runtime. The existing C++ VM remains in the desktop CLI regression path. It is not the Android app's active bytecode executor.

## Runtime versions and compatibility

- Bundled Rust runtime: **2.1.0**.
- Runtime API: **1**.
- Supported bytecode: **.room v10**.
- Android ABIs: `arm64-v8a` and `armeabi-v7a`.
- Installed modules live in app-private `files/ci-runtime/versions/` folders.
- Active version and rollback history live in `files/ci-runtime/active.json`.
- The bundled Rust implementation is linked into the APK as the fallback engine.
- A compatible Rust runtime module can be installed or rolled back without replacing the APK. Changes to the JNI ABI, runtime API, compiler interface or .room format require an APK update.

An incompatible or damaged active module is rejected and the loader selects the first valid previous module, or the bundled Rust runtime.

## Rust runtime module ABI

A `.ciruntime` update is a ZIP package with exactly three root entries:

- `manifest.json`
- `android/arm64-v8a/libci_runtime.so`
- `android/armeabi-v7a/libci_runtime.so`

Each native library is compiled from the Rust CI Runtime crate for its Android ABI. The manifest records the version, runtime API, supported .room format range and SHA-256 hashes for both libraries.

The Rust shared library exports these C ABI entry points:

- `ci_runtime_plugin_api_version()`
- `ci_runtime_plugin_version()`
- `ci_runtime_plugin_self_test()`
- `ci_runtime_plugin_run_room_json(...)`
- `ci_runtime_plugin_free_string(...)`

The host verifies the manifest, ABI-specific SHA-256 digest, exported API version, compiled module version and self-test before activating a package.

## Building and publishing a Rust runtime update

Install Rust, the Android NDK and the Android Rust targets, then run:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
tools/package_ci_runtime.sh 2.1.0
```

The script builds both Android shared libraries from `runtime/ci-runtime` and creates:

- `dist/ci-runtime-2.0.1.ciruntime`
- `dist/ci-runtime-2.0.1.ciruntime.sha256`

A release is published by pushing a tag such as `ci-runtime-v2.0.1`, or by manually running the **Publish Rust CI Runtime release** workflow with a version number.

## Installing and rolling back on Android

1. Open the Runtime item in the Studio sidebar.
2. Choose **DOWNLOAD LATEST RUNTIME** to check this repository's GitHub Releases, or **INSTALL FROM FILE** to select a local `.ciruntime` package.
3. The Rust manager checks the ZIP layout, manifest, API version, .room compatibility, ABI-specific SHA-256 hash, native module version and module self-test.
4. Only after validation does it move the module into its version folder and atomically activate it.
5. The next Run, Execute or UI event uses the selected module. The Studio VM status shows the installed app version and CI Runtime version separately.
6. Choose **ROLLBACK** to switch to the previous valid version or the bundled Rust runtime.

The release downloader accepts HTTPS release assets only from this repository's GitHub Releases. WebView page networking remains disabled; downloads are performed by the Android host.

## Security note

A `.ciruntime` package contains native executable shared-library code. Install only packages built by this repository's trusted workflow or supplied by a maintainer you trust. SHA-256 detects package corruption and mismatched contents; it does not authenticate the publisher. The runtime module executes inside the application process and is not sandboxed from the app.

The host limits package, manifest and shared-library sizes, rejects unexpected ZIP entries and activates updates atomically. A failed installation leaves the active version unchanged.

## Limits

The Rust runtime executes verified .room bytecode; it does not parse AL source. The compiler remains C++17. The Android Java layer remains responsible for Android lifecycle and native View rendering. A runtime module update cannot add an opcode, change the .room binary format, modify the compiler or change the JNI ABI without a compatible APK release.
