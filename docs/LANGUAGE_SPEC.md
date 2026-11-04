# AL Language Specification: Stage 7

## Identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. It owns its compiler pipeline and bytecode VM.

## Constructor syntax

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

A constructor is declared with the same name as its class and no return type. Constructor parameters use the supported scalar or class types. The call form is `new ClassName(arguments)`.

## Constructor semantics

- If a class declares no constructor, the compiler synthesizes a public zero-argument constructor.
- If a class declares one constructor, that signature becomes the only accepted allocation signature for that class.
- Constructor argument count and types are checked during semantic analysis.
- Constructors have a hidden `this` parameter and execute in a normal VM call frame.
- The allocated object reference is duplicated before constructor invocation, so the `new` expression evaluates to the initialized object.
- Constructors have void return semantics. Returning a value is rejected.
- Derived constructors automatically call their base class constructor with no arguments before executing the derived constructor body.
- A derived class cannot compile when its base class constructor requires arguments. Explicit `super(arguments)` calls are not implemented.
- Constructor overloading is not implemented. A class can define exactly one constructor.
- Classes with no explicit constructor still get a generated zero-argument constructor, including derived classes.

## Inherited language features

Stage 7 retains:
- Scalar and array types, expressions, arithmetic, comparisons and boolean short-circuiting.
- `if/else`, `while`, `for`.
- Typed functions, parameters, returns and recursion.
- Classes, scalar fields, object creation, field access, instance methods and `this`.
- Single inheritance using `extends`, inherited fields/methods and derived-to-base assignment.
- Typed arrays, indexing, mutation, bounds checking and `.length`.
- Runtime overflow and division-by-zero checks.

Class fields remain scalar `int`, `string` and `boolean` with default values. Field initializer expressions, static members, enforced access control, method overriding, virtual dispatch, interfaces, abstract classes, object arrays, generics, enums, exceptions, modules, standard library modules, graphics/game/audio APIs, GUI IDE, `.AL` packaging and Android support are not yet implemented.

## Compiler and bytecode

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowerer -> .room v7 -> verifier -> ALVM`

The v7 bytecode instruction set includes `DUP`, which duplicates the stack top and is verified to require a value. The binary format keeps the v6 class hierarchy table and function table while changing the version to 7. The loader rejects .room v1-v6 files. Recompile source files.

The VM loads compiled bytecode and does not interpret `.al` source text.
