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

- C++17 native lexer, parser, AST, semantic/type analysis, IR lowering, bytecode serialization, verifier and ALVM execution.
- `.room` version 8 with a real instruction stream and validation.
- Integer, string and boolean scalar values; their primitive arrays support literals, indexing, mutation, length, foreach and sized allocation with default initialization.
- Functions, recursion, global overloads, classes, fields, objects, constructors, inheritance, explicit base-constructor calls, instance method overloads and derived-to-base assignment.
- Conditional and loop control flow, switch/case, break and continue.
- Android ALVM Studio shell with bundled offline UI, local workspace picker and native C++ compiler/VM bridge.

## Incomplete or not implemented in the native AL language

- OOP: virtual overriding/dynamic dispatch, interfaces, abstract classes, static members, access-control enforcement, object arrays and generic types.
- Arrays/collections: multidimensional arrays, object arrays, resizable lists, maps, sets and generalized element types.
- Runtime: exceptions, resource/finalization model, module loader, garbage collector and broader runtime type information.
- System libraries: AL-native file APIs, database APIs and networking APIs. Android workspace file selection is an IDE feature, not the same as an AL File library.
- UI/game: AL-native window and view toolkit, buttons, text fields, images, touch/gesture event model, graphics renderer, physics, audio and native game loop. The existing bundled Chess/Game panels are Studio previews and are not native AL APIs.
- Packaging: a defined `.AL` application container and compiler-driven packaging of AL projects into installable Android APKs. The current Android APK is the ALVM Studio IDE itself, not a user-created AL application.

## Implementation order

1. **Array and type system:** sized primitive arrays (Stage 14), then object arrays and multidimensional arrays with verifier coverage.
2. **OOP dispatch:** override signatures, virtual method table metadata, runtime dispatch and inheritance/polymorphism tests.
3. **Runtime safety:** exception model, stack traces with source locations, heap/resource management and runtime type metadata.
4. **Core standard library:** strings/math/collections first, then permission-scoped file APIs using explicit Android capability grants.
5. **Application UI framework (Stage 15 initial slice implemented):** AL `UI.screen`, `UI.text`, `UI.button`, integer UI state and AL event callbacks render through native Java Android Views. Still to build: editable text fields, custom layout trees, image widgets, full event/lifecycle system. Keep ALVM Studio itself offline; any network permission must belong to a generated app that explicitly requests it.
6. **Graphics/game/audio:** 2D renderer, touch/keyboard input, frame loop, collision helpers and audio playback, each backed by working native implementations and tests.
7. **Database and networking:** local database first; networking as an optional, permission-gated capability rather than a background dependency of the IDE.
8. **Application packaging:** `.AL` manifest/container, resources and module validation, then Android project generation and APK build with clear signing/build diagnostics.

## Release rule

A capability is marked implemented only after the native compiler/VM or Android host implementation exists, automated tests pass, and its documentation names limitations. Library Store snippets remain labelled Planned until that condition is met.
