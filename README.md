# ALVM Studio

AL is an independent language and runtime project:
- Vocabulary and keyword spelling: Python-influenced.
- Source writing style: Java-like.
- Rules: strict and C++-influenced.
- Implementation: its own lexer, parser, AST, semantic analyzer, IR, bytecode, verifier and VM. It is not another language under a renamed extension.

The native top-level declaration is `room Main`. `class Main` remains an early compatibility spelling.

## Stage 4 implementation

The current engine is a C++17 command-line compiler and VM:

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowering -> binary .room v4 -> verifier -> ALVM`

Implemented:
- Line/column lexer diagnostics, comments, escaped strings, integer/string/boolean literals and multi-character operators.
- AST and type checks for `int`, `string`, `boolean`, their array forms, and inferred `var`.
- Arithmetic, string concatenation, comparisons, equality, logical operators, unary operators and short-circuit evaluation.
- `if/else`, `while`, `for`.
- Typed arrays: literals, indexing, mutation, `.length`, aliasing, compile-time element checks and runtime bounds checks.
- User-defined functions in a room, typed parameters, argument type checks, scalar/array return values, direct calls and recursion.
- Per-function local slots, call frames, and a 1024-frame recursion limit.
- Function signature/return analysis and separate AST, semantic, IR and bytecode-lowering passes.
- Binary `.room` v4 function table; verifier checks control flow, locals, call targets and stack depths.
- Runtime integer overflow/division-by-zero guards and CLI commands `compile`, `run`, `disasm`.
- End-to-end tests for control flow, arrays, calls, recursion, type errors, missing returns and bounds failures.

## Function syntax

```al
room Main {
    public static void main() {
        AL.print(add(10, 20));
        AL.print(factorial(5));
    }
    public static int add(int a, int b) {
        return a + b;
    }
    public static int factorial(int n) {
        if (n <= 1) { return 1; }
        else { return n * factorial(n - 1); }
    }
}
```

Function names must be unique. Overloading is not implemented. Calls are direct calls within the same room. `public`, `private`, `protected` and `static` are currently accepted syntactically, but access control and instance-method semantics are not implemented.

## Not implemented yet

This is not a complete language, IDE or Android app. Pending features include:
- Function overloading, lambdas, closures and named/default arguments.
- Classes/objects with fields, constructors, instance methods, inheritance, interfaces, polymorphism and enforced access control.
- Generics, enums, exceptions, modules/imports.
- Additional numeric types (`float`, `double`, `long`, `short`, `byte`), `char` and more collections.
- Standard libraries for math, strings, files, networking and databases.
- Graphics, UI, audio, input, physics and game APIs.
- Graphical ALVM Studio editor, project explorer, terminal, debugger and Android integration.
- `.AL` packaging and Android APK build/signing.

Do not mark a feature complete until implementation and tests support it.

## Build and run

Requirements: C++17 compiler, CMake 3.16+ and Bash for tests.

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
./build/alvm compile examples/hello.al
./build/alvm run examples/hello.room
./build/alvm compile examples/functions.al
./build/alvm run examples/functions.room
ctest --test-dir build --output-on-failure
```

Expected function example output: `30`, `120`, `Hello AL`, `15`.

## .room v4

Little-endian fields: 4-byte `ALRM` magic; uint32 version 4; uint32 function count; then each function has length-prefixed UTF-8 name, uint8 return type, uint32 parameter count, uint32 local count, uint32 instruction count; each instruction has uint8 opcode, uint64 operand, and length-prefixed UTF-8 payload.

Versions 1, 2 and 3 are not accepted by the v4 loader. Recompile the original `.al` sources. The VM executes compiled `.room` bytecode and does not interpret source text.

## Roadmap

1. Function overloading and stronger control-flow/return analysis.
2. Classes, fields, constructors, instance methods and inheritance.
3. Interfaces, enums, generics, exceptions, modules and standard library.
4. Graphics/game/audio APIs and a real-time runtime loop.
5. Graphical IDE, project model, diagnostics and debugging.
6. `.AL` packaging, Android integration and a tested APK pipeline.
