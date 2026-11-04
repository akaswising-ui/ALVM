# ALVM Studio

AL is an independent language: Python-influenced vocabulary, Java-like source style, and strict C++-influenced rules. It has its own lexer, parser, AST, semantic analyzer, IR, bytecode, verifier and VM.

## Stage 7: constructors

Current compiler pipeline:

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v7 -> verifier -> ALVM`

Implemented:
- Scalar and array types, arithmetic, boolean operations, conditions and loops.
- Typed functions, parameters, return values and recursion.
- Classes, scalar fields, objects, methods, `this` and object references.
- Single inheritance, inherited fields/methods and derived-to-base assignment.
- Explicit constructors with typed parameters.
- Automatic zero-argument constructors when no constructor is declared.
- Static checks for constructor argument count and types.
- A newly allocated object is preserved while its constructor executes.
- Derived constructors automatically invoke their base class zero-argument constructor before their own body.
- A derived class is rejected if its base class has only a parameterized constructor. Explicit `super(...)` arguments are not supported yet.
- Duplicate constructors are rejected; constructor overloading is not implemented.
- Binary `.room` v7 adds a `DUP` instruction for preserving an object reference during constructor invocation.
- Regression tests cover explicit/default constructors, base initialization, wrong argument counts/types and unsupported constructor chains.

## Example

```al
room Main {
    public static void main() {
        Player p = new Player(100, "ALVM");
        AL.print(p.health);
    }

    class Player {
        int health;
        string name;

        Player(int startingHealth, string playerName) {
            this.health = startingHealth;
            this.name = playerName;
        }
    }
}
```

## Not implemented

Constructor overloading, explicit `super(...)`, field initializers, method overriding/virtual dispatch, interfaces, abstract classes, static members, enforced access control, object arrays, generics, enums, exceptions, modules, full standard libraries, graphics/game/audio APIs, graphical IDE, `.AL` packaging and Android APK generation remain pending.

## Build

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
./build/alvm compile examples/constructors.al
./build/alvm run examples/constructors.room
ctest --test-dir build --output-on-failure
```

The `.room` v7 format stores class hierarchy metadata and a function table. The loader rejects v1-v6 files; recompile their `.al` source. The VM executes compiled bytecode only.
