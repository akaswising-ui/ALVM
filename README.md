# ALVM Studio

AL is an independent language: Python-influenced vocabulary, Java-like source style, and strict C++-influenced rules. It has its own lexer, parser, AST, semantic analyzer, IR, bytecode, verifier and VM.

## Stage 6: inheritance

The current C++17 compiler pipeline is:

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v6 -> verifier -> ALVM`

Implemented:
- Scalar types, typed arrays, arithmetic, conditions, loops and short-circuit boolean operators.
- Typed functions, parameters, return values, nested calls and recursion.
- Classes, scalar instance fields, object creation, field access, `this`, instance methods and object references.
- Single inheritance using `class Dog extends Animal`.
- Inherited fields and methods; a derived object may be used as a base-type variable, parameter or return value.
- Runtime class ancestry checks for inherited method receivers.
- Inherited field layouts are flattened in the object representation.
- Compile-time rejection of unknown base classes, invalid downcasts, inherited field collisions and method overrides.
- `.room` v6 class hierarchy metadata and verifier checks.

Parent classes must be declared before child classes in the same room. Multi-generation inheritance works when this ordering is followed. Method overriding and virtual dispatch are not implemented, so a child cannot redeclare an inherited method. Static members, access control, constructors, interfaces and abstract classes remain pending.

## Example

```al
room Main {
    public static void main() {
        Dog dog = new Dog();
        dog.setAge(7);
        Animal animal = dog;
        AL.print(animal.getAge());
        AL.print(dog.bark());
    }

    class Animal {
        int age;
        public void setAge(int value) { this.age = value; }
        public int getAge() { return this.age; }
    }

    class Dog extends Animal {
        int trained;
        public int bark() { return 1; }
    }
}
```

## Not implemented

Function/method overloading, virtual overriding, interfaces, abstract classes, constructors, static fields/methods, enforced access modifiers, object arrays, generics, enums, exceptions, modules, complete standard libraries, graphics/game/audio APIs, GUI IDE, `.AL` application packaging and Android APK generation are still pending.

## Build

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
./build/alvm compile examples/inheritance.al
./build/alvm run examples/inheritance.room
ctest --test-dir build --output-on-failure
```

## .room v6 format

Little-endian format: `ALRM` magic, version 6, class count; each class stores its name, signed base-class id (-1 for none), flattened field count and field type tags; then function count and function entries. A function stores its name, owner class id, return type, parameter count, local count and instructions. Each instruction stores opcode, 64-bit operand and length-prefixed payload.

Base class ids must point to an earlier class entry. The loader rejects invalid parent references and old .room v1-v5 files. Recompile the original `.al` source. The VM executes compiled bytecode only.
