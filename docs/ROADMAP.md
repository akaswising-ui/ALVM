# ALVM Studio development roadmap

## Library Store inventory

The Library Store contains **12 module cards** after the missing modules were added. Existing modules were retained; only the absent audio and system modules were added.

| AL library area | Existing Library Store entry |
|---|---|
| AL.Core | AL Compiler + AL Virtual Machine |
| AL.Math | Standard Library |
| AL.String | Standard Library |
| AL.Collections | Standard Library |
| AL.Files | File System Library |
| AL.Network | Networking Library |
| AL.Database | Database Library |
| AL.UI | GUI Framework |
| AL.Graphics | Graphics Library |
| AL.Game | Game Development Library |
| AL.Audio | Newly added |
| AL.System | Newly added |

The Website Development Library remains an additional tool outside this 12-area AL runtime library set. The Library Store cards and snippets are catalog/documentation unless a native implementation is explicitly listed below. Adding a catalog entry does not make its API executable.


This roadmap distinguishes the native AL language engine from the Android IDE shell and from browser-only previews. A UI example or snippet is not an executable AL standard-library API.

## Current implemented native foundation

- C++17 native lexer, parser, AST, semantic/type analysis, IR lowering and bytecode serialization; the desktop CLI retains the C++ VM for regression coverage.
- Android ALVM Studio executes verified .room v10 instructions with the Rust CI Runtime through JNI; Java renders native Android Views and does not interpret bytecode.
- `.room` version 10 with a real instruction stream, structural validation and operand-stack verification.
- Integer, string and boolean scalar values; their primitive arrays support literals, indexing, mutation, length, foreach and sized allocation with default initialization.
- Functions, recursion, global overloads, classes, fields, objects, constructors, inheritance, explicit base-constructor calls, instance method overloads and derived-to-base assignment.
- Conditional and loop control flow, switch/case, break and continue.
- Android ALVM Studio shell with bundled offline UI, local workspace picker and native C++ compiler/VM bridge.

## CI Runtime on Android

- Android Run, Execute and AL UI callbacks use the CI Runtime ALVM adapter; the C++17 compiler remains unchanged and produces .room bytecode.
- The CI Runtime bytecode reader validates the ALRM header, v9 layout, counts, opcodes, references, jump targets and stack-depth flow before execution.
- Implemented runtime operations include scalar values, arrays, functions/calls, objects/fields, arithmetic, control flow and the existing UI calls used by the Android native View bridge.
- Runtime engine modules are versioned in app-private storage. .ciruntime update packages are checked for layout, version/API compatibility, .room compatibility, SHA-256 integrity, native ABI exports and self-test before activation.
- The CI Runtime Manager provides manual local install and rollback to a previous runtime or bundled fallback. No network update service is enabled.
- The Rust CI Runtime is bundled with the APK as its fallback engine. Only a compatible CI Runtime module can be hot-updated without an APK release; compiler, Java host, native ABI or .room-format changes require a new APK.
- The Android APK targets minSdk 24. Runtime update packages contain executable native Rust shared libraries and must come from a trusted publisher.

## Incomplete or not implemented in the native AL language

- OOP: virtual overriding/dynamic dispatch, interfaces, abstract classes, static members, access-control enforcement, object arrays and generic types.
- Arrays/collections: multidimensional arrays, object arrays, resizable lists, maps, sets and generalized element types.
- Runtime: exceptions, resource/finalization model, module loader, garbage collector and broader runtime type information.
- System libraries: AL-native file APIs, database APIs and networking APIs. Android workspace file selection is an IDE feature, not the same as an AL File library.
- UI/game: AL-native window and view toolkit, buttons, text fields, images, full multitouch/gesture system, sprite/assets renderer, rigid-body physics solver and audio. The existing bundled Chess/Game panels are Studio previews and are not native AL APIs.
- Packaging: a defined `.AL` application container and compiler-driven packaging of AL projects into installable Android APKs. The current Android APK is the ALVM Studio IDE itself, not a user-created AL application.

## Recently implemented: compound assignment and increment/decrement\n\n- Added `+=`, `-=`, `*=`, `/=` for simple variables.\n- Added prefix and postfix `++` / `--` as statement forms.\n- The compiler lowers these constructs to existing AL arithmetic/load/store instructions; `.room` v9 and the Rust runtime ABI remain unchanged.\n- Array-element and object-field compound updates are explicitly rejected until their single-evaluation lowering is implemented.\n\n## Implementation order

1. **Array and type system:** sized primitive arrays (Stage 14), then object arrays and multidimensional arrays with verifier coverage.
2. **OOP dispatch:** override signatures, virtual method table metadata, runtime dispatch and inheritance/polymorphism tests.
3. **Runtime safety:** exception model, stack traces with source locations, heap/resource management and runtime type metadata.
4. **Core standard library:** strings/math/collections first, then permission-scoped file APIs using explicit Android capability grants.
5. **Application UI framework (Stage 15 initial slice implemented):** AL `UI.screen`, `UI.text`, `UI.button`, integer UI state and AL event callbacks render through native Java Android Views. Still to build: editable text fields, custom layout trees, image widgets, full event/lifecycle system. Keep ALVM Studio itself offline; any network permission must belong to a generated app that explicitly requests it.
6. **Graphics/game/audio:** 2D renderer, touch/keyboard input, frame loop, collision helpers and audio playback, each backed by working native implementations and tests.
7. **Database and networking:** local database first; networking as an optional, permission-gated capability rather than a background dependency of the IDE.
8. **Application packaging:** ALPK v1 is implemented for one main .room module, a manifest and optional assets (CLI); the IDE can export the single-module package. Multi-module linking, external libraries/dependencies and building generated AL applications into APKs remain planned.

## Recently implemented: native touch, frame loop and basic collision slice

- `UI.touchArea` registers touch hit regions; Android `Game2DView` converts device touches into 320×480 logical coordinates and invokes the named AL callback. Callback code can read `touchX` and `touchY` through the existing integer UI state API.
- `UI.frameLoop` schedules an AL update callback at 1–60 FPS through an Android main-thread Handler; the loop pauses with the Activity and stops when the game dialog closes or `UI.stopLoop()` is called.
- `UI.collides` performs rectangle/rectangle and circle/rectangle axis-aligned bounding-box overlap tests in both C++ and Rust runtimes. This is collision detection support, not a physics solver.
- C++ compiler/VM and Rust CI Runtime end-to-end tests cover callback registration, frame metadata, touch coordinate delivery, collision results and loop cancellation.

## Release rule

A capability is marked implemented only after the native compiler/VM or Android host implementation exists, automated tests pass, and its documentation names limitations. Library Store snippets remain labelled Planned until that condition is met.

## Recently implemented: floating-point types and .room v10

- The native C++ compiler recognises `float`, `double`, decimal literals and strict same-type numeric operations.
- The C++ VM and Rust CI Runtime execute binary32 and binary64 pushes, arithmetic, comparisons and unary negation.
- Float/double arrays support literals, sized allocation, default initialization, indexing, assignment and length.
- Scalar float/double class fields and function return types are supported.
- Mixed float/double operations are rejected. Exponent-form literals, implicit numeric conversion, float/double array class fields and remaining primitive types are still pending.
- The compiler and runtime now use binary `.room` v10. Existing v9 programs must be recompiled from source.


## Recently implemented: ALPK v1 application packages

- The Rust alpack tool creates and verifies .AL ZIP archives.
- Required entries: manifest.json and modules/main.room. Optional CLI assets are stored under assets/.
- The packager verifies the main module as executable .room v10 bytecode before creating the archive.
- The Android IDE Package .AL button compiles source using the native C++ compiler, writes the manifest and module, then opens Android's document picker to save the .AL file.
- Package verification rejects unexpected paths, duplicate entries, mismatched manifest contents, unsupported room formats and oversized archives.
- ALPK v1 contains one entry module. External libraries, dependency resolution and multiple linked modules are not implemented; libraries and dependencies must remain empty.
- Creating an .AL package is not the same as generating an Android APK. APK generation/signing for user apps remains planned.
