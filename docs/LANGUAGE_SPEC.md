# AL Language Specification: Stage 15

## AL Language Design Principles

### Language identity

AL is an independent language with its own lexer, parser, semantic analysis, compiler, bytecode format, virtual machine and runtime.

- **Keyword vocabulary:** AL-defined readable English keyword spellings selected for the language. AL has its own deterministic grammar, parser and compiler.
- **Source-code writing style:** Java/C++-inspired braces, semicolons, class declarations, typed variables and method declarations.
- **Language rules:** C++-inspired strictness, including explicit types, compile-time validation, deterministic grammar and rejection of invalid operations.
- **Execution:** .al source is compiled to verified .room bytecode; ALVM executes bytecode and never interprets the original source as a shortcut.

The language design is not a clone of Python, Java or C++. Their influence is limited to selected vocabulary and familiar syntax conventions. The compiler, runtime and bytecode are AL-specific.


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

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v9 -> verifier -> ALVM`

Switch and loop control lower to existing jump instructions. Stage 14 separately adds the `NewArraySized` instruction to `.room` v8.

## Not implemented

`foreach`, `do`-less switch expressions, switch pattern matching, labels/goto, virtual overriding, interfaces, abstract classes, static members, enforced access control, generics, enums, exceptions, modules, full standard library modules, editable text fields, richer layout containers, touch/gesture APIs, graphics/game/audio APIs, `.AL` packaging and building a user AL program into an Android APK are future features.


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

## AL-native UI API (Stage 15)

The native Android runtime supports a declarative screen API:

```al
UI.screen("AL App");
UI.text("display", "0");
UI.button("one", "1", "onButton");
    }

    void onButton(string buttonId) {
        UI.setText("display", buttonId);
    }
}
```

- `UI.screen(string title)` sets the screen title.
- `UI.text(string id, string value)` creates or replaces a text view.
- `UI.button(string id, string label, string callbackName)` creates a button. The callback must be a global `void` function with one `string` parameter.
- `UI.setText(string id, stringOrInt value)` updates an existing text view.
- `UI.setInt(string key, int value)` stores integer state; `UI.getInt(string key, int defaultValue)` reads it.
- `UI.clear()` removes all current components and integer state.
- Button clicks pass the button ID to the AL callback. The C++ VM executes the callback, while the Android Java host creates and updates native Android Views.

The `.room` format is version 9. The `UiCall` opcode carries UI commands to the VM. `examples/calculator.al` is a complete AL-authored calculator sample; neither its layout nor arithmetic is preloaded in the Android host.

## Stage 16: Native 2D drawing API

AL programs can create a native Android 2D drawing surface:

```al
UI.game("My 2D Game");
UI.rect("ground", 0, 400, 320, 80, 4278255360);
UI.circle("player", 80, 360, 18, 4294901760);
UI.line("platform", 10, 350, 300, 350, 4294967295);
UI.remove("player");
```

- The logical drawing surface is 320 by 480 units and scales to the available Android view.
- `UI.game(string title)` sets the game title and clears current UI elements.
- `UI.rect(string id, int x, int y, int width, int height, int argb)` draws a filled rectangle.
- `UI.circle(string id, int centerX, int centerY, int radius, int argb)` draws a filled circle.
- `UI.line(string id, int x1, int y1, int x2, int y2, int argb)` draws a line.
- Colors are ARGB integer values. For example, opaque red is 4294901760.
- Reusing an ID updates that drawing object. `UI.remove(string id)` removes it.
- The C++ ALVM executes drawing calls and serializes scene objects; Android renders them using a native Canvas-backed View.
- This stage provides a static 2D scene renderer. A timed game loop, sprite/image loading, touch callbacks, physics, and collision APIs are not implemented yet.

## Compound assignment and increment/decrement

The compiler accepts the following statement forms for simple variables:

```al
int score = 10;
score += 5;
score -= 2;
score *= 3;
score /= 2;
score++;
++score;
score--;
--score;
```

- `+=`, `-=`, `*=` and `/=` are lowered by the compiler to a variable load, the corresponding existing arithmetic instruction, and a store.
- Prefix and postfix `++` / `--` are supported as statements and are lowered to addition or subtraction by one.
- These forms use the ordinary AL type checker. For example, string `+=` string uses the existing string-concatenation operation; increment/decrement require integer operands.
- Compound assignment and increment/decrement on array elements or object fields are rejected with an explicit compiler diagnostic. They are not silently approximated.
- This feature does not add bytecode opcodes or change the `.room` format; it uses existing ALVM instructions.
