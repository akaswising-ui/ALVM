# ALVM Studio

AL is an independent language and runtime project:
- Vocabulary and keyword spelling: Python-influenced.
- Source writing style: Java-like.
- Rules: strict and C++-influenced.
- Implementation: its own lexer, parser, AST, semantic analyzer, IR, bytecode, verifier and VM. It is not another language under a renamed extension.

The native top-level declaration is `room Main`. `class Main` remains an early compatibility spelling.

## Stage 5 implementation

Current engine: C++17 compiler and bytecode VM.

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowering -> binary .room v5 -> verifier -> ALVM`

Implemented:
- Line/column lexer diagnostics, comments, escaped strings, integer/string/boolean literals and multi-character operators.
- Scalar and array type checks: `int`, `string`, `boolean`, `int[]`, `string[]`, `boolean[]`, plus inferred `var`.
- Arithmetic, string concatenation, comparisons, equality, logical operators and short-circuit evaluation.
- `if/else`, `while`, `for`.
- Typed arrays with literals, indexing, mutation, `.length`, aliasing and bounds checks.
- User-defined functions, typed parameters, return values, direct calls and recursion.
- Class declarations inside a room, scalar instance fields, object creation with `new ClassName()`, field reads/writes, `this`, instance method calls and object references.
- Compile-time validation of field names, field types, method names, method argument counts and method argument types.
- Per-function local slots and VM call frames. Recursive call depth is limited to 1024.
- Binary `.room` v5 class/function tables, verifier control-flow checks, call target/local checks and runtime object-field guards.
- CLI commands: `compile`, `run`, `disasm`.
- End-to-end tests for control flow, arrays, functions, recursion, object state, aliasing and invalid types.

## Object example

```al
room Main {
    public static void main() {
        Player player = new Player();
        player.addHealth(25);
        AL.print(player.health);
    }

    class Player {
        int health;

        public void addHealth(int amount) {
            this.health = this.health + amount;
        }
    }
}
```

Class fields currently support scalar `int`, `string` and `boolean` types. Fields receive defaults of `0`, empty string and `false`. There are no custom constructors yet. Object assignment copies the reference, so aliases share field changes. Instance methods use a hidden `this` local.

Access modifiers (`public`, `private`, `protected`) and `static` are accepted syntactically but are not enforced. All class methods currently use instance-call semantics.

## Not implemented yet

This is not a complete language, IDE or Android app. Pending features include:
- Inheritance, `extends`, `implements`, interfaces, polymorphism, abstract classes and enforced access control.
- Custom constructors, static class methods, class-level/static fields and object arrays.
- Function/method overloading, lambdas, closures, generics and enums.
- Exceptions and modules/imports.
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
./build/alvm compile examples/objects.al
./build/alvm run examples/objects.room
ctest --test-dir build --output-on-failure
```

The object example demonstrates typed fields, instance methods, `this`, field mutation and reference aliasing.

## .room v5

Little-endian fields: 4-byte `ALRM` magic; uint32 version 5; uint32 class count; each class has a length-prefixed UTF-8 name, uint32 field count and uint32 scalar type tags; then uint32 function count; each function has name, signed owner-class id, return type, parameter count, local count and instruction count; each instruction has uint8 opcode, uint64 operand and length-prefixed payload.

Versions 1 through 4 are not accepted by the v5 loader. Recompile the original `.al` files. The VM executes compiled `.room` bytecode and never interprets source at runtime.

## Roadmap

1. Inheritance, interfaces, constructors, static members and object arrays.
2. Overloading, generics, enums, exceptions and modules.
3. Standard library APIs for system, files, networking and data.
4. Graphics/game/audio APIs and a real-time runtime loop.
5. Graphical IDE, project model, diagnostics and debugging.
6. `.AL` packaging, Android integration and tested APK pipeline.
