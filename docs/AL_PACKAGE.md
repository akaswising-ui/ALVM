# ALPK v1: .AL application package

An .AL file is a ZIP archive with a versioned JSON manifest and compiled AL bytecode.

## Layout

    Example.AL
    ├── manifest.json
    ├── modules/
    │   └── main.room
    └── assets/
        └── optional files supplied to alpack

The manifest contains format=ALPK, formatVersion=1, name, appVersion, entry=modules/main.room, roomFormat=10, modules, assets, libraries and dependencies.

## Build and verify

First compile AL source into binary bytecode:

    ./build/alvm compile Main.al -o build/main.room

Build a package with optional assets:

    cargo build --manifest-path runtime/ci-runtime/Cargo.toml --bin alpack
    runtime/ci-runtime/target/debug/alpack create build/MyApp.AL MyApp 1.0.0 build/main.room assets/icon.png
    runtime/ci-runtime/target/debug/alpack verify build/MyApp.AL

The packager checks the .room header, v10 format and bytecode verifier before writing the archive. The verifier rejects duplicate or unexpected ZIP paths, path traversal, oversized entries, manifest/archive mismatches and incompatible bytecode.

The Android IDE Package .AL button performs native compilation, creates a single-module package with an empty asset list, and saves it using Android's document picker.

## v1 limits

ALPK v1 supports one main module and optional asset files through the CLI. It does not link multiple modules, resolve external libraries/dependencies, produce an Android project, sign an APK or install the packaged app. Those features are not implemented.