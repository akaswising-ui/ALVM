# AL Language Specification: Stage 8

## Control-flow syntax

```al
switch (expression) {
    case value:
        statements
        break;
    default:
        statements
}

do {
    statements
} while (condition);
```

The language also supports `break;` and `continue;` inside their valid contexts.

## Switch semantics

- The switch expression must have type `int`, `string` or `boolean`.
- Every case expression must have the same type as the switch expression.
- A switch can contain at most one `default` label.
- The switch expression is evaluated once and stored in a compiler-generated local slot.
- Case expressions are tested in source order using equality.
- If a case matches, execution begins at that case body.
- Case bodies fall through into the next case unless they execute `break;` or otherwise leave the switch.
- If no case matches, execution transfers to the default body. Without a default, the switch ends.
- `break;` exits the nearest enclosing loop or switch.

## Loop control

- `do { ... } while (condition);` executes its body at least once. Its condition must be boolean.
- `continue;` in a `while` loop transfers to its condition check.
- `continue;` in a `do/while` loop transfers to the trailing condition check.
- `continue;` in a `for` loop transfers to the increment expression, then to the condition check.
- `break;` is valid only inside a loop or switch.
- `continue;` is valid only inside a loop. A switch nested inside a loop does not change the continue target; continue still applies to the nearest enclosing loop.
- A switch nested in a loop has its own break target, so break exits the switch and not the outer loop.

## Existing language features

Stage 8 keeps the previous stages' supported scalar and array types, functions and recursion, classes and objects, constructors, single inheritance, field/method access, arithmetic, boolean operations, `if/else`, `while`, `for`, and typed mutable arrays.

The current C++17 pipeline is:

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v8 -> verifier -> ALVM`

Switch and loop control lower to existing jump instructions. The .room v8 binary layout remains unchanged.

## Not implemented

`foreach`, `do`-less switch expressions, switch pattern matching, labels/goto, method overloading, constructors with explicit `super(arguments)`, virtual overriding, interfaces, abstract classes, static members, enforced access control, generics, enums, exceptions, modules, full standard library modules, graphics/game/audio APIs, GUI IDE, `.AL` packaging and Android APK integration are future features.


## Foreach array iteration

Syntax:

```al
foreach (int item : values) {
    AL.print(item);
}
```

- The source expression must have type `int[]`, `string[]` or `boolean[]`.
- The iteration variable must match the array element type. `var` infers that type.
- The source expression is evaluated once before iteration begins.
- Elements are visited in ascending index order from zero through `length - 1`.
- The iteration variable is scoped to the foreach statement and cannot be referenced after it.
- `break;` exits the foreach loop.
- `continue;` advances to the next element.
- The compiler lowers foreach to ordinary array length, indexed read, arithmetic and jump instructions. No special foreach opcode is embedded in .room v8.
- Empty arrays are not yet expressible as array literals; a future array allocation feature will make zero-element iteration directly testable.


## Global function overloading

- Two or more global functions may use the same name when their ordered parameter type lists differ.
- A function call is resolved using the static types of its arguments.
- A candidate is viable only when its arity matches and each argument is assignable to the corresponding parameter type.
- Among viable candidates, the compiler selects the candidate with the greatest number of exact argument-to-parameter type matches.
- If no candidate is viable, compilation fails with a no-matching-overload diagnostic.
- If multiple candidates tie for the best score, compilation fails with an ambiguous-overload diagnostic.
- Return type alone cannot distinguish overloads. Repeating the same parameter type list is a duplicate signature, even if the return type differs.
- `main()` is a unique entry point and cannot be overloaded or called as an ordinary function.
- The compiler assigns each overload a stable internal symbol; the source-level function name remains unchanged.
- This stage implements global function overloading. Method and constructor overloading are not yet implemented.


## Instance method overloading

- A class may declare multiple instance methods with the same name when their ordered parameter type lists differ.
- Calls are resolved from the receiver's static class type and argument types.
- A candidate is viable only when its arity matches and each argument is assignable to the corresponding parameter type.
- The compiler prefers the viable candidate with the greatest number of exact argument-to-parameter type matches.
- No viable candidate or a tie for the best score is a compile-time error.
- Return type alone cannot distinguish overloads. A repeated parameter type list is a duplicate signature.
- A derived class inherits all visible overload signatures from its base class. A new overload with a different parameter list may be added in the derived class.
- A same-signature method in a derived class is rejected until virtual overriding is implemented.
- The selected implementation is stored as a collision-resistant internal symbol in IR and bytecode.
- Constructor overloading is not part of this stage.


## Constructor overloading

- A class may declare multiple constructors when their ordered parameter type lists differ.
- An object creation expression resolves its constructor using the static types of its arguments.
- A candidate is viable only when arity matches and each argument is assignable to the corresponding parameter type.
- The compiler prefers the viable constructor with the greatest number of exact argument-to-parameter type matches.
- No viable constructor or a tie for the best score is a compile-time error.
- Return type does not apply to constructors. Repeating the same parameter type list is a duplicate constructor signature.
- If a class declares no constructor, the parser supplies a zero-argument constructor.
- A derived constructor automatically calls the base class zero-argument constructor. Compilation fails if the base class has no zero-argument constructor.
- Explicit base-constructor calls such as super(arguments) are not implemented yet.
- Each constructor is assigned a collision-resistant internal symbol in IR and bytecode.


## Explicit base-constructor invocation

- A derived-class constructor may begin with `super(arguments);`.
- The `super` call must be the first statement in the constructor body.
- `super` is invalid in a class without a base class and outside a constructor.
- The argument list is resolved against the base class constructor overloads using static argument types and the standard exact-match preference.
- No matching or ambiguous base constructor is a compile-time error.
- If the explicit call is omitted, the compiler automatically invokes the base class zero-argument constructor.
- If no zero-argument base constructor exists and no explicit `super(arguments)` is supplied, compilation fails.
- The selected base constructor is emitted as a normal bytecode call with the current object as receiver.


## Stage 14: sized primitive-array allocation

Syntax:

```al
int[] scores = new int[5];
string[] labels = new string[2];
boolean[] flags = new boolean[3];
```

- The element type must currently be `int`, `string` or `boolean`.
- The length expression must have compile-time type `int`; it is evaluated once at runtime.
- Runtime lengths from 0 through 100000 are accepted. Negative or larger lengths raise a runtime error.
- Elements are initialized to `0`, the empty string, or `false`, respectively.
- The resulting value supports indexed reads, indexed writes, `.length` and typed `foreach` iteration.
- The compiler emits `NewArraySized`; the VM does not execute source text.
- The opcode is serialized in `.room` version 8. Version 7 files are rejected with an explicit version error and must be recompiled.
- Object arrays, multidimensional arrays, array literals of length zero, and collection types remain unsupported.
