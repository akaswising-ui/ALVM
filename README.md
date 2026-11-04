# ALVM Studio

AL is an independent language and runtime project. Its identity is fixed as:
- **Vocabulary and keyword spelling:** Python-influenced and approachable.
- **Source writing style:** Java-like braces, declarations and methods.
- **Language rules:** strict and C++-influenced.
- **Implementation:** its own lexer, parser, AST, semantic checker, IR, bytecode, verifier and VM. It is not Python, Java or JavaScript with a renamed extension.

The native top-level declaration is `room Main`. `class Main` is accepted as an early compatibility spelling.

## Stage 2 implementation

The current engine is a C++17 command-line compiler and virtual machine. The implemented path is:

`.al source -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowering -> binary .room -> verifier -> ALVM runtime`

Working features:
- Lexer with line/column positions, line and block comments, integer/string/boolean literals, escaped strings and multi-character operators.
- AST for declarations, assignments, expressions, print, return, blocks, if/else, while and for.
- Static type checks for `int`, `string`, `boolean` and inferred `var`.
- Arithmetic, string concatenation, comparisons, equality, logical operators and unary operators.
- Short-circuit evaluation for `&&` and `||`.
- `if/else`, `while` and `for` bytecode control flow.
- Explicit IR generation and a separate bytecode-lowering pass.
- Binary `.room` v2 format with ALRM magic, version, instruction table, 64-bit operands and bounded string constants.
- Bytecode control-flow stack verifier, runtime type guards, overflow checks and division-by-zero checks.
- CLI commands: `compile`, `run` and `disasm`.
- Automated positive, negative and control-flow end-to-end tests.

## Not implemented yet

The project is not a complete language, IDE or Android app. These major features are still pending:
- Arrays, indexing, collections and generics.
- User-defined functions, parameters, call frames, recursion and overloads.
- Classes/objects, constructors, inheritance, interfaces, polymorphism and access control.
- Exceptions and modules/imports.
- Standard libraries for files, networking and databases.
- Graphics, UI, audio, input, physics and game APIs.
- Graphical ALVM Studio editor, project explorer, terminal and debugger.
- `.AL` application packaging and Android APK build/signing.

Do not mark a feature complete until implementation and tests support it.

## Build and run

Requirements: C++17 compiler, CMake 3.16+ and Bash for the end-to-end tests.

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
./build/alvm compile examples/hello.al
./build/alvm run examples/hello.room
./build/alvm disasm examples/hello.room
ctest --test-dir build --output-on-failure
```

The hello example prints `30`. The control-flow example exercises loops and conditional branches.

## .room v2 binary format

All integer fields are little-endian:
1. Four-byte magic: `ALRM`.
2. uint32 format version: `2`.
3. uint32 instruction count.
4. For each instruction: uint8 opcode, uint64 operand, uint32 UTF-8 payload length, payload bytes.

Version 1 files are not currently accepted by the Stage 2 loader. The VM loads and executes the compiled `.room` bytecode; it does not interpret `.al` source at runtime.

## Next engineering stages

1. Arrays, element types, indexing, mutation and bounds-safe runtime operations.
2. Functions, parameter/return checking, call frames and recursion.
3. Complete object model: classes, fields, methods, constructors and inheritance.
4. Exceptions, modules and standard library.
5. Graphics/game/audio APIs and a real-time runtime loop.
6. Graphical editor/IDE, project model, diagnostics and debugging.
7. `.AL` packaging, Android integration and a tested APK pipeline.
