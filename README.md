# ALVM Studio

AL is an independent programming language with its own grammar, lexer, parser, semantic/type analysis, IR, bytecode, verifier and runtime.

## Language identity

- Vocabulary: AL's own English keyword spellings, with selected familiar spellings influenced by Python.
- Source style: Java-like class declarations, braces, semicolons, access modifiers and typed variables.
- Rules: C++-influenced strict typing and compile-time validation.
- Execution: the C++17 compiler emits binary .room bytecode. The Rust CI Runtime validates and executes bytecode; it does not interpret AL source.

AL is not a renamed Python, Java or C++ runtime.

## Execution pipeline

.al source -> lexer -> parser/AST -> semantic analysis -> type checking -> IR -> compiler -> binary .room v10 -> verifier -> Rust CI Runtime -> output

Android uses a Java host/JNI bridge and the same C++ compiler and Rust runtime. The browser IDE contains a visual preview; native Android Run/Compile/Execute actions use the real compiler and runtime.

## Implemented language features

- Scalar types: int, string, boolean, float and double.
- Arrays: int[], string[], boolean[], float[], double[]; literals, sized allocation, indexing, assignment, length and foreach.
- Integer, float and double arithmetic, comparisons and unary minus. Mixed float/double arithmetic is rejected; no implicit conversion yet.
- Variables, scopes, conditions, while/do-while/for/foreach loops, switch, break, continue and return.
- Functions, recursion, overloading, classes, fields, objects, constructors, inheritance, instance methods and explicit base-constructor calls.
- Simple-variable compound assignments += -= *= /= and prefix/postfix ++ --.
- Native UI calls and basic 2D shapes through AL UI APIs.

## Bytecode and runtime

- Binary .room v10: magic ALRM, versioned structure, typed classes/functions, instruction stream and verifier.
- Rust CI Runtime 2.1.0 supports .room v10 and the existing AL runtime operations.
- Version 9 .room files are intentionally rejected by the v10 runtime. Recompile their .al sources.

## AL application packages

ALPK v1 .AL packages are ZIP archives containing manifest.json, modules/main.room and optional assets/ files.
The Android Studio Package .AL button compiles the open source file with the native compiler, creates a single-module package and opens the Android save picker. The Rust CLI can package assets:

    cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
    cmake --build build --parallel
    cargo build --manifest-path runtime/ci-runtime/Cargo.toml --bin alpack
    ./build/alvm compile examples/floating_point.al -o build/main.room
    runtime/ci-runtime/target/debug/alpack create build/FloatingPoint.AL FloatingPoint 1.0.0 build/main.room assets/icon.png
    runtime/ci-runtime/target/debug/alpack verify build/FloatingPoint.AL

ALPK v1 has one entry module. External libraries, dependencies and multiple-module linking are not implemented. .AL packaging does not yet generate a standalone Android APK.

## Test commands

    cargo test --manifest-path runtime/ci-runtime/Cargo.toml --all-targets
    cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
    cmake --build build --parallel
    ctest --test-dir build --output-on-failure
    bash tests/run_rust_runtime_e2e.sh build/alvm "$PWD" runtime/ci-runtime/target/debug/ci-runtime runtime/ci-runtime/target/debug/alpack

## Still incomplete

Interfaces, abstract classes, virtual overriding/dynamic dispatch, generics, enums, additional primitive types, maps/sets/lists, exception handling, module imports, full standard libraries, multi-module package linking, external dependencies, full native game loop, direct touch/input API, physics, audio, complete graphics/sprite APIs and AL-to-Android-APK generation remain planned or partial. The Library Store may list a module before its runtime implementation exists; a catalog card is not proof of executable support.
