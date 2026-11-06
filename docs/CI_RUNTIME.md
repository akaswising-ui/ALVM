# CI Runtime: ALVM bytecode adapter

## Responsibilities

CI Runtime is the shared runtime identity used by ALVM Studio and Conshite. This document describes the ALVM Studio bytecode adapter; the Conshite PSP adapter is documented in the Conshite repository. The two app adapters are not yet consolidated into one shared binary or universal engine. The target is a common versioned API, module ABI, loader, and platform service layer.\n\n## ALVM adapter responsibilities\n\nThe Android ALVM Studio app currently uses two execution stages:

1. The C++17 compiler parses and checks AL source and writes a binary .room file.
2. The CI Runtime ALVM adapter, currently implemented in Python 3.11, reads .room v9, validates the bytecode and interprets its instruction stream.

The Android Java host calls the current Python 3.11 implementation through Chaquopy. The existing C++ CLI VM remains in the repository for desktop regression tests; the Android Run, Execute and UI-event paths use the CI Runtime ALVM adapter.

The bundled Python interpreter is Python 3.11. The runtime engine is a separate Python module, stored in app-private storage after installation. Updating the engine module does not require replacing the APK. Updating the embedded Python interpreter or the Java/Android host still requires an APK update.

## Runtime versions

- Bundled fallback: 1.0.0.
- Runtime API: 1.
- Supported bytecode: .room v9.
- Installed engine versions are stored under the app-private files/ci-runtime/versions/ directory.
- Active version and rollback history are stored in files/ci-runtime/active.json.
- The currently active engine is loaded when a .room program is executed.
- If the active module is missing or fails validation, the loader falls back to a previous valid version or the bundled engine.

## Building a runtime update

A runtime update is a ZIP package with the .ciruntime extension. It must contain exactly two files at its root:

- manifest.json
- runtime.py

Build a package from the repository's Python runtime source:

    python tools/package_ci_runtime.py --version 1.0.1

The default output is build/ci-runtime-update.ciruntime. The package manifest records the runtime version, runtime API version, compatible .room version range and SHA-256 hash of runtime.py.

To package another compatible engine implementation:

    python tools/package_ci_runtime.py --version 1.0.2 --source path/to/your_runtime.py --output build/ci-runtime-1.0.2.ciruntime

The module must expose:
- RUNTIME_API_VERSION = 1
- RUNTIME_VERSION matching the manifest version
- run_room(room_path, entry_name="main", event_value="")
- self_test() returning True

## Installing and rolling back on Android

1. Open the Runtime item in the Studio sidebar.
2. Choose DOWNLOAD LATEST RUNTIME. The Android host checks this repository's GitHub Releases for the newest `ci-runtime-vX.Y.Z` release and downloads its `.ciruntime` asset directly.
3. The app checks the package layout, version, runtime API, .room format number, SHA-256 hash, Python syntax and runtime self-test before activating it.
4. The next Run, Execute or UI event uses the active version. The Studio VM status shows the app version and CI Runtime version as separate numbers.
5. Choose INSTALL FROM FILE to install a locally supplied package, or ROLLBACK to switch to the previous installed engine or bundled fallback.

The direct downloader accepts HTTPS release assets only from this repository's GitHub Releases. WebView page networking remains disabled; downloads are performed by the Android host. A runtime release is published by pushing a tag such as `ci-runtime-v1.0.1`, or by manually running the `Publish CI Runtime release` workflow with a version number.

The downloader is only useful after a CI Runtime release has been published. Until then, the app reports that no release exists rather than inventing an available version.

## Security note

A .ciruntime package contains executable Python code. Install only packages you built yourself or received from a trusted maintainer. SHA-256 detects package corruption and mismatched file contents; it does not prove who published the package. Public distribution should add a publisher signature and verify it against a public key embedded in the app before enabling updates from other publishers.

The runtime manager installs files in app-private storage, limits package/file sizes, rejects extra ZIP entries and uses atomic activation. The runtime is not sandboxed from the app process; treat a runtime update as trusted application code.

## Limits

The Android host's Python interpreter and Chaquopy native libraries are bundled in the APK. Only the Python engine module is hot-swappable. An update which changes the runtime API, .room binary format, Chaquopy/Python interpreter version, Android Java bridge or native ABI requires a compatible app release.
