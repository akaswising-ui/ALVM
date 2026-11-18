# ALVM Studio

AL is an independent programming language with its own grammar, lexer, parser, semantic/type analysis, IR, bytecode, verifier and runtime.

The consolidated status, dependencies and acceptance criteria for all remaining subsystems are tracked in [docs/MASTER_IMPLEMENTATION_PLAN.md](docs/MASTER_IMPLEMENTATION_PLAN.md). This is the master feature list; its status table distinguishes implemented, partial and planned work.

## Language identity

- **Keyword spellings/vocabulary:** AL uses its own grammar with familiar English spellings influenced by Python.
- **Writing style:** Java-like class declarations, braces, semicolons, access modifiers and typed variables.
- **Rules:** C++-influenced strict typing, explicit type checks and compile-time rejection of invalid operations.
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
- Native UI calls, 2D shapes, logical-coordinate touch areas, a 1–60 FPS Android frame callback and basic AABB collision checks.

## AL Code Options catalogue

The Studio Library Store exposes exactly 10,000 generated, searchable source snippets in ten groups: variables, arithmetic, arrays, conditions, loops, switch, functions, classes/objects, output and native UI/2D calls. Entries are paginated, searchable by category, can be added to the project snippet collection, and export as an `AL-Library-Snippets.txt` reference file (not a compilable `.al` program).

These 10,000 entries are parameterized source templates built only from syntax and native calls already implemented by the C++ AL compiler and Rust CI Runtime. They are not 10,000 new built-in functions, libraries, keywords or VM instructions. Planned library cards such as Network, Database, Audio and advanced Graphics remain planned until their compiler/runtime implementations and tests exist.

## Explicit AL native window gate

A native application window is shown only when the AL program executes `change window "AppTitle";`. `UI.screen(...)`, `UI.game(...)` and view-building calls do not open it by themselves. The statement is parsed and type-checked by the native C++ AL compiler, lowered to the existing `.room v10` `UiCall` instruction, and handled by the Rust CI Runtime. The Android host displays the window only when the runtime returns `visible: true`.

```al
class Main {
    public static void main() {
        change window "MyApp";
        UI.screen("My App");
        UI.text("status", "Ready");
    }
}
```

## Implemented core standard-library slice

The native compiler and both VM runtimes support the following AL-native calls through the existing \`UiCall\` bytecode instruction in \`.room v10\`; no source code is executed by the runtime:

- \`AL.Math.abs(int|float|double)\`: returns the same numeric type; integer overflow is rejected.
- \`AL.Math.min\` / \`AL.Math.max\`: two arguments of the same numeric type.
- \`AL.Math.sqrt(double)\` and \`AL.Math.pow(double, double)\`: return \`double\`; negative square-root input is rejected.
- \`AL.Math.floor\` / \`ceil\` / \`round(double)\`: return \`int\` and reject results outside the signed 64-bit range.
- \`AL.String.length(string)\`: returns UTF-8 byte length.
- \`AL.String.upper\` / \`lower\`: ASCII case conversion.
- \`AL.String.contains\` / \`startsWith\` / \`endsWith\`: return \`boolean\`.
- \`AL.String.replace(string, search, replacement)\`: replaces all non-overlapping matches; an empty search string leaves the source unchanged.

The compiler rejects unsupported names, argument counts and types at compile time. String length is byte-based, and case conversion is ASCII-only; these functions are not yet a full Unicode text library.

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

Interfaces, abstract classes, virtual overriding/dynamic dispatch, generics, enums, additional primitive types, maps/sets/lists, exception handling, module imports, remaining standard libraries (collections, files, network, database and audio), multi-module package linking, external dependencies, advanced native input devices, rigid-body physics, audio, complete graphics/sprite APIs and AL-to-Android-APK generation remain planned or partial. The Library Store may list a module before its runtime implementation exists; a catalog card is not proof of executable support.
