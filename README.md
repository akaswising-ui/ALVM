# ALVM Studio

AL is an independent language: Python-influenced vocabulary, Java-like source style, and strict C++-influenced rules. It has its own lexer, parser, AST, semantic analyzer, IR, bytecode, verifier and VM.

## Stage 9: static members

Current C++17 compiler pipeline:

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v8 -> verifier -> ALVM`

Implemented:
- Scalar and array types, arithmetic, strings, comparisons, boolean short-circuiting and structured control flow.
- Typed functions, parameters, returns and recursion.
- Classes, objects, constructors, instance fields/methods, `this` and single inheritance.
- Class-level static fields and static methods.
- Static fields are accessed through class names, for example `Counter.value`.
- Static methods are called through class names, for example `Counter.increment(5)`.
- Static field values live in class-owned VM storage, not in each object instance.
- Static field defaults are `0`, empty string and `false`.
- Static fields and static methods are inherited. A derived class access to an inherited static field resolves to the original declaring class storage.
- Static methods have no hidden `this` receiver. Calling `this` from a static method is rejected.
- Instance methods/fields must be accessed through an object; static members must be accessed through their class name.
- Static vs instance name conflicts and inherited member collisions are rejected.
- Static method parameters and return values use the existing type checker and VM call-frame system.
- Binary `.room` v8 stores static field type metadata and static method flags. The verifier validates static field references and call stack shape.

## Example

```al
room Main {
    public static void main() {
        Counter.value = 10;
        Counter.increment(5);
        AL.print(Counter.value);
        AL.print(Counter.twice(Counter.value));
    }

    class Counter {
        static int value;

        static void increment(int amount) {
            Counter.value = Counter.value + amount;
        }

        static int twice(int number) {
            return number * 2;
        }
    }
}
```

## Current limitations

This is not the full language or IDE. Access modifiers `public`, `private` and `protected` are still syntax-only, not enforced. Constructors are not overloaded. Static fields are scalar only and cannot have initializer expressions. Static methods cannot use `this`. Static field initialization is default-only. The following remain pending:
- Access-control enforcement and visibility rules.
- Static initializer blocks and explicit static field initializers.
- Method overriding/virtual dispatch, interfaces, abstract classes, explicit `super(args)`.
- Object arrays, object-valued fields, function/method overloading, generics and enums.
- Exceptions, modules/imports and full standard libraries.
- Files/network/database APIs, graphics/game/audio APIs.
- Graphical ALVM Studio editor/project explorer/terminal/debugger.
- `.AL` packaging and Android APK build/signing.

## Build

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
./build/alvm compile examples/static_members.al
./build/alvm run examples/static_members.room
ctest --test-dir build --output-on-failure
```

## .room v8

Little-endian format: `ALRM` magic, version 8, class table including instance and static field type layouts, then function table including owner class, return type, static flag, parameter count, local count and instructions. New static field bytecodes access class storage via a packed class-id/field-slot operand. The loader rejects v1-v7. Recompile earlier `.al` sources; the VM runs bytecode only.
