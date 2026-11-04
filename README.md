# ALVM Studio

The ALVM repository was empty when inspected. This branch creates the first editable Stage 1 compiler/VM foundation; it does not overwrite a previous implementation.

## AL language identity

- Word/keyword spelling: Python-influenced and approachable.
- Writing style: Java-like braces, declarations and method structure.
- Rules: strict, C++-influenced typing and compile-time discipline.
- AL is not Python, Java or JavaScript under a different extension. It has its own lexer, parser, compiler, bytecode, verifier and VM.

The native top-level declaration is `room Main`. The early `class Main` spelling is accepted for compatibility.

## Implemented in Stage 1

- C++17 lexer and parser with line/column diagnostics.
- `room Main { public static void main() { ... } }` entry point.
- `int`, `var`, and `string` local declarations.
- Integer literals, string literals, variable reads and assignments.
- Integer arithmetic: `+`, `-`, `*`, `/`, unary minus and parentheses.
- `AL.print(expression);`.
- Real binary `.room` format: ALRM magic, version, instruction count, opcode, operand and constant payload.
- Bytecode loader, validation, stack verifier and bytecode interpreter.
- CLI: compile, run, disasm.
- Automated end-to-end test for output 30.

## Not implemented yet

Conditions, loops, functions/recursion, arrays, classes/objects, inheritance, interfaces, exceptions, modules, graphics/game APIs, .AL packaging, and graphical IDE. These are planned, not claimed as working.

## Build and run

Requirements: C++17 compiler, CMake 3.16+, Bash for the supplied test.

```bash
cmake -S . -B build
cmake --build build --parallel
./build/alvm compile examples/hello.al
./build/alvm run examples/hello.room
./build/alvm disasm examples/hello.room
ctest --test-dir build --output-on-failure
```

Expected output: `30`.

## Execution pipeline

`hello.al` -> Lexer -> Parser -> compiler/code generator -> binary .room -> verifier -> ALVM -> runtime output.

The VM executes the compiled .room file. It does not re-read or interpret .al source at runtime.

## .room v1

Little-endian fields: 4-byte ALRM magic; uint32 version (1); uint32 instruction count; then each instruction has uint8 opcode, uint32 operand, uint32 UTF-8 constant length, and constant bytes. Opcode definitions are in src/alvm.cpp.

## Roadmap

1. Lexer/parser/AST and complete basic syntax.
2. Explicit IR and expanded bytecode.
3. Harden room format, verifier and VM.
4. Conditions and loops.
5. Functions, call frames, recursion and overloads.
6. Classes and object model.
7. Arrays and collections.
8. Exceptions and standard libraries.
9. Graphics/game APIs.
10. .AL packaging and graphical IDE.

Do not mark a feature complete until implementation and tests support it.