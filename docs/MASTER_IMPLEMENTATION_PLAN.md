# ALVM Studio master feature implementation plan

This is the single tracking plan for the complete AL language and ALVM Studio target. It consolidates the remaining language/runtime/IDE work so feature requests do not need to be rediscovered or re-described. A row is Implemented only when the native compiler, bytecode/runtime path and automated tests all exist. A Library Store card or browser-only preview does not qualify.

## Architecture contract

AL source uses Python-influenced English keyword spellings, Java-like class/block/semicolon syntax, and C++-influenced strict typing and compile-time rejection. The language remains independent: its own lexer, parser, AST, semantic analyser, IR, bytecode, verifier, VM and runtime. Android must continue to call the native C++ compiler and Rust CI Runtime through the JNI host. Browser preview is explicitly a reduced preview, never a substitute for .room v10.

## Status snapshot

| Area | Status | Current scope / remaining work |
|---|---|---|
| Lexer, parser, AST, semantic analysis, type checking, IR, compiler | Implemented foundation | Add diagnostics/source maps and extend only with tests |
| .room binary format, verifier, ALVM execution | Implemented foundation | Current format v10; any new instruction/type metadata needs a versioned format migration |
| Variables, scalar numeric/string/boolean values, arrays, operators, loops, switch | Implemented subset | More primitive types, multidimensional/object arrays and richer conversions remain |
| Functions, recursion, overloads, classes, fields, constructors, single inheritance | Implemented subset | Virtual dispatch, interfaces, abstract classes, static members, access-control enforcement and generics remain |
| AL.Math | Partially implemented | abs/min/max/sqrt/pow/floor/ceil/round |
| AL.String | Partially implemented | byte length, ASCII upper/lower, contains, startsWith, endsWith and replace |
| AL.Collections | Planned | Typed List, Map and Set, mutation/iteration, value semantics and memory-safety tests |
| Exceptions | Planned | try/catch/finally/throw, typed exception values, call-frame unwinding and source-aware stack traces |
| Memory management | Planned | Heap ownership model, cyclic-reference strategy or tracing GC, deterministic resource close and stress tests |
| Interfaces / abstract classes / polymorphism | Planned | Grammar + semantic constraints + class/interface metadata + verifier-safe dynamic dispatch |
| Generics | Planned | Type parameters, bounds, inference policy, generic methods/classes and monomorphisation or documented reified runtime metadata |
| AL.Files / AL.System | Planned | App-private sandbox, capability/permission checks, path traversal protection, explicit failures and Android host implementation |
| AL.Network | Planned | Permission-gated HTTP client, timeouts, cancellation, TLS validation and offline/runtime tests |
| AL.Database | Planned | SQLite-backed local database API, schema/query parameter binding, transactions and migration tests |
| AL.Audio | Planned | Android native audio resources, lifecycle cleanup, package asset loading and audio-device capability tests |
| AL.UI / AL.Graphics / AL.Game | Partially implemented | Native screen/text/button/2D shape/touch/frame-loop/AABB collision slice exists |
| Complete graphics/game engine | Planned | Images/sprites, asset lifecycle, transforms, scene graph, keyboard/touch input, animation, timing, physics and playable sample games written in AL |
| .AL package | Partially implemented | ALPK v1 single entry module plus optional assets; no multi-module linker or external dependencies |
| Multi-module linking | Planned | Module/import grammar, symbol visibility, dependency graph, cycles, linker, package manifest and runtime module loader |
| AL application APK generation | Planned | Android app template, runtime/library bundling, permissions, asset packaging, Gradle build, signing configuration and installable test APK |
| IDE / Library Store / code catalogue | Partially implemented | Preserve editor/workspace/compile/run/execute/preview; distinguish native actions from reduced browser preview |

## Required implementation tracks

### A. Object model and type system

1. Add interface declarations, required method signatures, implements lists and compile-time conformance checks.
2. Add abstract classes and abstract methods; reject direct abstract instantiation and incomplete concrete subclasses.
3. Add virtual/override declarations, stable method slots and runtime dispatch metadata. A base-typed reference must invoke the derived override.
4. Add static members and enforce public/private/protected access at compile time.
5. Add generic class/function type parameters, bounds, nested type arguments and a deterministic implementation strategy. Reject unsupported generic shapes instead of silently erasing type safety.

Acceptance: positive and negative fixtures for conformance, missing methods, invalid overrides, abstract instantiation, access violations, overload resolution, generic inference/bounds and dynamic dispatch. The C++ CLI VM and Rust CI Runtime must return the same results.

### B. Exceptions and memory/resource model

1. Define AL exception values, standard exception classes and source file/line/column metadata.
2. Implement try/catch/finally/throw semantics in AST, semantic analysis, IR, bytecode verifier and both VMs.
3. Define stack unwinding across nested function calls; finally must run on normal return and exception propagation.
4. Define object/array ownership and collection rules. Select a cycle-safe strategy before claiming garbage collection.
5. Provide deterministic close/dispose for file, database, network, image and audio handles.

Acceptance: nested throw/catch, rethrow, uncaught trace, finally on all exits, allocation stress, cycle tests and resource-leak tests. Verifier rejects malformed exception-handler ranges and invalid stack states.

### C. Collections and standard library

1. Implement typed List<T>, Map<K,V> and Set<T> with generic element checks.
2. Implement add/get/set/remove/contains/size/clear and safe iteration.
3. Implement the remaining AL.Core, AL.Math and AL.String surface with a written signature specification.
4. Add date/time/random and conversion APIs only when both VM runtimes have parity.

Acceptance: bounds/empty/duplicate-key/iteration mutation tests, generic type mismatch tests, serialization policy for package data, and equivalent C++/Rust runtime results.

### D. Files, network and database

1. AL.Files: app-sandbox read/write/list/create/delete, bounded streams, UTF-8 behavior and explicit permission/capability checks.
2. AL.Network: HTTPS requests, TLS validation, timeout, cancellation, response status/headers/body, bounded response size and explicit app network permission.
3. AL.Database: SQLite open/close, schema migration, parameterized query execution, row mapping and transactions. Never concatenate user values into SQL.
4. AL.System: device/app information exposed through a documented allow-list; sensitive Android permissions require user consent.

Acceptance: local Android instrumentation tests, offline tests, denied-permission tests, path traversal tests, SQL injection tests, timeout/cancellation tests and resource-release checks.

### E. Graphics, UI, game and audio

1. Expand AL.UI from the current screen/text/button slice to layout containers, editable controls, event propagation and lifecycle callbacks.
2. Expand AL.Graphics with images, sprites, transform/texture handling, clipping, asset lookup and frame scheduling.
3. Expand AL.Game with scene/entity lifecycle, deterministic update timing, keyboard/touch input, animation, hit testing and physics/collision response.
4. Implement AL.Audio with asset loading, playback, pause/resume/stop, volume, completion callbacks and lifecycle cleanup.
5. Move all application/game behavior into AL source. Android host code renders/forwards platform events; it must not contain hardcoded game rules.

Acceptance: two independent AL-authored sample games, automated rule/state tests, device input tests, frame timing tests, asset-failure tests and audio lifecycle tests. A rendered board or preview is not proof of a playable game engine.

### F. Modules, package linking and installable APKs

1. Define package/import syntax, module names, exports/visibility, version constraints and dependency resolution.
2. Compile modules independently, build a dependency graph and link verified bytecode with patched class/function references.
3. Extend ALPK manifest for multiple modules, entry point, assets, library versions, dependency hashes and permissions.
4. Verify every module and dependency before execution; reject duplicate symbols, cycles where unsupported, path traversal, incompatible ABI/API and untrusted native payloads.
5. Generate a real Android project/APK from an ALPK app, including the native CI Runtime, Java host, manifest permissions, resources and Gradle build. Keep signing credentials under the developer's control.

Acceptance: multi-module compile/link/run example, broken/missing dependency rejection, package integrity tests, APK build in CI, APK installation and end-to-end launch on an Android emulator/device.

## Release gates

- A feature is not marked implemented because its syntax parses or a catalogue card exists.
- Any opcode, type-table or metadata change requires a documented .room version decision, verifier updates and compatibility tests.
- C++ compiler/desktop VM and Rust CI Runtime must agree on valid and invalid programs.
- Android host features require permission, lifecycle, error and resource-cleanup tests.
- Browser preview must label unsupported native behavior and must never export its reduced preview container as native .room.
- The release matrix must show implemented, partial and planned features separately.
- Each milestone updates README, LANGUAGE_SPEC, ROOM_FORMAT (when changed), ROADMAP, examples and automated tests together.

## Current milestone in progress

Stage 18 implements the first native AL.Math / AL.String slice in the C++ compiler, C++ VM and Rust CI Runtime using the existing .room v10 UiCall instruction. Its positive/negative examples and C++/Rust end-to-end tests are part of the repository. This milestone does not implement collections, exceptions, GC, network, database, audio, a full game engine, multi-module linking or APK generation.
