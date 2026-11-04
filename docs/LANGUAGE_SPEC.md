# AL Language Specification: Stage 6

## Identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. Its compiler and VM are independent implementations.

## Implemented syntax

```al
room Main {
    public static void main() {
        Dog dog = new Dog();
        dog.setAge(7);
        Animal animal = dog;
        AL.print(animal.getAge());
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

## Inheritance rules

- A class may extend one class: `class Derived extends Base`.
- The base class must be declared earlier in the same room. Forward base references are rejected.
- Multi-generation inheritance is supported when each ancestor appears before its descendant.
- The compiler copies inherited field slots and method signatures into the derived class layout.
- Derived instances are assignable to a base class type. This applies to local variables, function parameters and function returns.
- Base-to-derived assignment is rejected. There are no explicit casts yet.
- Inherited fields and methods are available through derived-typed expressions.
- Runtime method receiver checks walk the class ancestry, so a derived instance can be passed to an inherited base method.
- The current implementation rejects field hiding and method overriding. Virtual dispatch is not implemented.
- Object references are shared when assigned. Object equality uses reference identity.

## Existing supported subset

- Scalar types: `int`, `string`, `boolean`; arrays: `int[]`, `string[]`, `boolean[]`; user-defined class types.
- Typed functions, parameters, returns, recursion and nested direct calls. Main must be global `void main()`.
- Classes, scalar fields, `new ClassName()`, `object.field`, `object.field = value`, `this` and instance methods.
- Arrays are non-empty homogeneous scalar literals with integer indexing, mutation and `.length`; bounds are checked at runtime.
- Arithmetic, string concatenation, comparison, logical operators, boolean short-circuit, `if/else`, `while`, `for`.
- Integer overflow and division by zero are runtime errors.
- Class fields support scalar types only and default to 0, empty string and false. Custom constructors and field initializers are not implemented.
- Access modifiers and `static` are parsed as syntax only. Access enforcement and static members are not implemented.
- Maximum VM call-frame depth is 1024.

## Compiler and bytecode

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowering -> .room v6 -> bytecode verifier -> ALVM`

The binary v6 file stores class metadata followed by function metadata and code. Integers are little-endian. Each class entry stores a length-prefixed name, signed base-class id (-1 for none), flattened field count and scalar field type tags. Each function stores a name, owner class id (-1 for global), return type, parameter count, local count and instructions.

The verifier rejects invalid class names, invalid/forward base references, invalid method owners, bad local slots, invalid allocations/calls, invalid jumps and stack-depth errors. The VM executes compiled bytecode; it does not interpret source text.

Stage 6 rejects .room v1-v5 files. Recompile the source to create v6 bytecode.

## Not implemented

Method overriding and virtual dispatch, interfaces, `implements`, abstract classes, constructors, static members, enforced access control, object arrays, object-valued fields, overloading, generics, enums, exceptions, modules, full standard libraries, file/network/database APIs, graphics/game/audio APIs, GUI IDE, `.AL` packaging and Android APK integration are roadmap items, not executable Stage 6 features.
