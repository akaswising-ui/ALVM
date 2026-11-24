# ALVM Studio: consolidated Stages 19–24

**Purpose:** Keep the remaining ALVM Studio work in one release checklist. This document is a single scope for implementation and verification, not a declaration that these features already exist.

**Completion rule:** A feature is complete only when AL source compiles through the native C++ compiler, emits valid versioned `.room` bytecode, passes the Rust CI Runtime verifier and executes with matching behavior, has automated positive and negative tests, and works through the Android host when it is a platform feature. A Library Store entry, parser-only syntax, mock preview or untested native stub is not completion.

## Repository baseline at the start of this plan

- The compiler/VM pipeline and Rust CI Runtime execute verified `.room v10` bytecode.
- Existing automated CI runs Rust unit tests, C++ build/CTest and C++ compiler-to-Rust runtime end-to-end tests.
- OOP currently has classes, fields, constructors, inheritance and overloads, but interfaces, abstract classes, virtual dispatch, enforced access control and generics remain incomplete.
- The graphics/game path has native 2D UI/shapes, sprites, touch areas, a frame callback, AABB overlap checks and vertical gravity integration. It is not a complete 2D physics engine.
- `.AL` uses ALPK v1 with one entry module and optional assets. Multi-module linking and external dependency resolution are not implemented.
- APK generation can build a single-source debug APK in configured desktop/CI environments. Real emulator/device installation and launch validation and release signing remain release gates.
- A complete 3D rendering pipeline is not established.

These baseline statements must be refreshed from code and test evidence before each release. Do not change a status to Implemented merely because this document lists the target.

---

## Stage 19: OOP completion

### Required implementation
1. Add interface declarations, method signatures, `implements` lists and compile-time conformance checking.
2. Add abstract classes and abstract methods. Reject direct construction of abstract types and reject concrete subclasses with unimplemented abstract members.
3. Add virtual/override methods with stable method slots and runtime dispatch metadata. A base-typed reference must call the most-derived valid override.
4. Implement static fields/methods and enforce public/private/protected visibility during semantic analysis.
5. Implement generic class and function type parameters, bounds, nested type arguments and a deterministic strategy (monomorphisation or explicitly specified runtime type metadata).
6. Preserve current overload resolution, constructors, inheritance and field/method rules. Reject unsupported generic shapes rather than silently erasing type safety.

### Cross-layer work
- Update grammar, AST, symbol/type model, semantic checker, IR, compiler, `.room` metadata/opcodes if needed, verifier, C++ VM, Rust CI Runtime and Android/JNI metadata handling.
- If bytecode layout or instruction meaning changes, document the format migration and compatibility policy in `docs/ROOM_FORMAT.md`; never silently reinterpret v10 files.

### Acceptance tests
- Positive: interface implementation, inherited interface, abstract base with concrete subclass, virtual override through base reference, static member access, valid protected access, generic function/class calls and overload selection.
- Negative: missing interface method, invalid override, abstract construction, incomplete concrete subclass, private/protected access violation, generic bound violation, ambiguous type inference and invalid type arguments.
- Assert identical values, errors and exit status across C++ desktop execution and Rust CI Runtime.

**Status:** Planned / partial until all acceptance tests pass.

---

## Stage 20: memory management and exceptions

### Required implementation
1. Specify typed exception values and standard exception types, preserving source file, line and column.
2. Implement `try`, `catch`, `finally` and `throw` through parser, semantic analysis, IR, bytecode, verifier and both runtimes.
3. Implement stack unwinding across nested calls. `finally` must execute on normal completion, return and exception propagation, according to one documented rule.
4. Specify object/array ownership and collection semantics. Choose a cycle-safe approach (tracing GC or a demonstrated ownership model) before claiming garbage collection.
5. Add deterministic close/dispose for file, database, network, image and audio resources, including Android lifecycle interruption and error paths.

### Acceptance tests
- Nested throw/catch, rethrow, uncaught exception with source-aware stack trace, finally on all exits and exceptions raised inside finally.
- Malformed exception-handler ranges and invalid stack states rejected by the `.room` verifier.
- Allocation stress, cyclic-reference tests where cycles are supported, repeated create/destroy tests and resource leak checks.
- Compare observable errors, cleanup order and process status between C++ and Rust.

**Status:** Planned until the memory model and all runtime/test layers are implemented.

---

## Stage 21: standard library

### Required modules
1. **AL.Collections:** typed `List<T>`, `Map<K,V>` and `Set<T>`; add/get/set/remove/contains/size/clear and safe iteration.
2. **AL.Files:** app-private sandbox read/write/list/create/delete, bounded streams, UTF-8 behavior and explicit permission/capability checks.
3. **AL.System:** documented allow-list of app/device/runtime information; sensitive permissions require Android user consent.
4. **AL.Network:** HTTPS requests, TLS certificate validation, timeout, cancellation, bounded response size, status/headers/body and explicit network permission.
5. **AL.Database:** SQLite open/close, schema migration, parameterized query execution, row mapping and transactions. User values must never be concatenated into SQL.
6. **AL.Audio:** packaged asset loading, play/pause/resume/stop, volume, completion/error callbacks, interruption handling and Android lifecycle cleanup.
7. Finish the documented AL.Core, AL.Math and AL.String surface. Add date/time/random/conversion APIs only when both runtimes can support them consistently.

### Safety and acceptance tests
- Collection bounds, empty values, duplicate keys, iteration mutation and generic type mismatch.
- Filesystem sandbox escape, path traversal, symlink policy, oversized files, invalid UTF-8 and denied permissions.
- Network TLS failure, timeout, cancellation, offline mode, oversized response and permission denial.
- SQL parameterization, injection payloads, rollback, transaction boundaries and migration failures.
- Audio missing assets, repeated playback, interruption, pause/resume and resource release.
- Every API must have compiler type-check tests and equivalent C++/Rust runtime results. Android-only APIs additionally require host instrumentation tests.

**Status:** Planned / partial; a catalogue card is not an executable API.

---

## Stage 22: graphics and game engine

### Complete 2D engine
1. Sprite loading, asset handles, animation clips, frame selection, playback speed, looping and lifecycle cleanup.
2. Scene graph with parent/child nodes, stable IDs, transforms, visibility, z-order, attach/detach and safe scene transitions.
3. Position/rotation/scale transforms, coordinate conversion, clipping and camera/view bounds.
4. Deterministic game loop with delta-time policy, fixed-step physics option, frame scheduling, pause/resume and clean stop.
5. Touch input with pointer identity and lifecycle cancellation. Add keyboard input where the device/host provides it; unsupported input must be reported rather than simulated.
6. Collision detection and response: document supported collider shapes, broad/narrow phase, overlap events, restitution/friction policy and deterministic resolution. AABB overlap alone does not count as collision response.
7. Keep game rules in AL source. Android host code should render and forward platform events, not hardcode game logic.

### 3D foundation
1. 3D object/mesh lifecycle and position, rotation and scale transforms.
2. Camera, view/projection matrices, viewport and depth handling.
3. Renderer integration appropriate to the Android target, with explicit GPU capability detection and a safe unsupported-device path.
4. Materials, textures, lighting and documented shading limits.
5. 3D collision/query foundation with clearly specified supported shapes; do not advertise a full 3D physics engine unless implemented and tested.
6. Versioned AL APIs and runtime behavior shared by C++ compilation and Rust CI execution. Android native rendering must be exercised on real GPU hardware.

### Acceptance tests
- Two independent playable games written in AL, not hardcoded into the Android host.
- Sprite animation frame/timing tests, scene graph attach/detach and transform propagation tests, deterministic pause/resume tests.
- Input event ordering/cancellation tests, collision and collision-response numeric tests, frame timing tests and asset-load failure tests.
- 3D transform/camera/projection/depth/material/lighting tests plus emulator and physical-device rendering smoke tests.
- Performance and memory tests on a defined low-end Android baseline. Unsupported GPU features must fail cleanly.

**Status:** 2D partial; 3D planned.

---

## Stage 23: packages and APK pipeline

### Multi-module .AL packages
1. Define module declaration/import syntax, exported symbols, visibility, module names and version constraints.
2. Build a dependency graph; detect missing modules, duplicate symbols and unsupported cycles.
3. Compile modules independently and link verified bytecode with deterministic symbol/reference resolution.
4. Extend ALPK manifest with module list, entry point, assets, library versions, dependency hashes, ABI/API requirements and permissions.
5. Verify every module and dependency before execution. Reject archive traversal, duplicate/ambiguous paths, excessive entry sizes, invalid hashes, malformed bytecode and untrusted native payloads.
6. Define external dependency sources, pinned versions, integrity hashes, offline cache behavior and a reproducible lockfile.

### APK generation, installation and signing
1. Extend the single-source APK builder to consume a verified multi-module `.AL` package.
2. Keep debug signing separate from release signing. Release credentials must be supplied and controlled by the developer; never commit secrets or generate fake signing success.
3. Build ABI-specific APKs and validate runtime/ABI compatibility and minimum Android API requirements.
4. In CI, build the APK, install it on an Android emulator, launch the generated app, execute a known AL program and collect logs/artifacts.
5. Add a documented physical-device smoke-test checklist for install, launch, permissions, rendering, input, audio, lifecycle and uninstall/update.
6. Add runtime/app version metadata and explicit compatibility rules. Reject unsupported `.room` versions and mismatched runtime versions with actionable errors.

### Acceptance tests
- Multi-module AL source compiles, links, packages and executes with expected output.
- Missing/duplicate/cyclic dependencies, incompatible versions, bad hashes, malicious archive paths and invalid embedded `.room` are rejected.
- CI produces an installable debug APK, installs it on an emulator, launches it and verifies program output.
- Release APK signing is verified only when a real developer-provided key is configured.
- App/runtime/.room compatibility matrix is tested across supported versions and ABIs.

**Status:** ALPK v1 single-module and single-source debug APK path are partial; multi-module linking and verified installation/release signing remain incomplete.

---

## Stage 24: final validation and release gate

### Required test matrix
1. **C++/Rust behavioral parity:** For every valid fixture compare canonical output, UI event state, return/exit status and errors. For every invalid fixture compare acceptance/rejection and stable diagnostic category. Differences fail CI.
2. **Compiler diagnostics:** Negative fixtures for syntax errors, unknown names, type mismatches, bad overloads, invalid access, invalid generics and unsupported platform APIs. Verify source file/line/column and deterministic messages.
3. **.room verifier security:** Fuzz and mutate headers, versions, type IDs, class/function references, instruction counts, string lengths, jumps, stack depths, local slots, handler ranges, package manifests and trailing bytes. Reject malformed inputs before execution without panic or unbounded allocation.
4. **Memory and crash safety:** Stress allocations and collections; detect leaks, use-after-free, double close, stack overflow, runaway loops and resource leaks. Use sanitizers/debug allocators where supported and Android instrumentation for native resources.
5. **Android emulator tests:** Install, launch, run an AL program, exercise input, graphics, lifecycle, permissions, package loading and APK upgrade/uninstall flows.
6. **Physical-device tests:** Test at least one documented ARM64 Android device and record OS version, ABI, GPU, runtime/app version, test result and logs. Emulator success alone is not physical GPU validation.
7. **Feature coverage:** Every supported syntax form, standard-library API, UI/game primitive and package operation has positive tests, negative tests and runtime parity evidence.
8. **Regression:** Preserve editor, project manager/file manager, Library Store, website builder, Chess UI and existing AL app export features.

### Release decision
- Publish a feature as **Implemented** only when its implementation and required tests are merged and passing.
- Publish the 1.0.0 stable release only when every mandatory stage gate above passes.
- Keep failures, unsupported platforms, ABI/runtime restrictions and partial features visible in the release matrix.
- Do not use a percentage as a substitute for a passing release matrix.

---

## Dependency order and single completion checklist

The six stages are one tracked release scope, but implementation order is constrained:

1. Stage 19 type/object metadata must land before generic collections and virtual dispatch-dependent game objects.
2. Stage 20 ownership and exception cleanup must land before Files, Network, Database, Audio and graphics asset handles.
3. Stage 21 APIs must use the sandbox, permission and resource rules from Stage 20.
4. Stage 22 builds on stable OOP, resource lifetime and input/event APIs.
5. Stage 23 packages only verified modules and versioned runtime-compatible bytecode.
6. Stage 24 validates every preceding stage and is the release gate, not a substitute for implementation.

- [ ] Stage 19: OOP completion, with C++/Rust parity.
- [ ] Stage 20: memory/resource model and exceptions.
- [ ] Stage 21: Collections, Files, System, Network, Database and Audio.
- [ ] Stage 22: complete 2D game engine and 3D foundation.
- [ ] Stage 23: multi-module ALPK, dependency linker, APK install testing and release signing.
- [ ] Stage 24: security, parity, memory/crash, emulator and physical-device test matrix.
- [ ] All mandatory CI jobs pass on the release commit.
- [ ] Documentation and feature status match the tested implementation.
