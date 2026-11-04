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

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v7 -> verifier -> ALVM`

Switch and loop control lower to existing jump instructions. The .room v7 binary layout remains unchanged.

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
- The compiler lowers foreach to ordinary array length, indexed read, arithmetic and jump instructions. No special foreach opcode is embedded in .room v7.
- Empty arrays are not yet expressible as array literals; a future array allocation feature will make zero-element iteration directly testable.
