# AL Language Specification: Stage 15

## AL Language Design Principles

### Language identity

AL is an independent language with its own lexer, parser, semantic analysis, compiler, bytecode format, virtual machine and runtime.

- **Keyword spellings and vocabulary:** AL uses familiar English keyword spellings influenced by Python. These spellings are part of AL's own language vocabulary; they are not Python syntax or a Python interpreter.
- **Source-code writing style:** Java-like class declarations, braces, semicolons, access modifiers, typed variables and method declarations.
- **Language rules:** C++-influenced strictness, including explicit types, compile-time validation, deterministic grammar and rejection of invalid operations.
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

`foreach`, `do`-less switch expressions, switch pattern matching, labels/goto, virtual overriding, interfaces, abstract classes, static members, enforced access control, generics, enums, exceptions, modules, remaining standard library modules (collections, files, network, database, audio and APIs beyond the Stage 18 Math/String slice), editable text fields, richer layout containers, touch/gesture APIs, graphics/game/audio APIs, `.AL` packaging and building a user AL program into an Android APK are future features.


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

The native Android runtime supports a declarative screen API. A program must explicitly request its native window with `change window`:

```al
class Main {
    public static void main() {
        change window "ALApp";
        UI.screen("AL App");
        UI.text("display", "0");
        UI.button("one", "1", "onButton");
    }

    void onButton(string buttonId) {
        UI.setText("display", buttonId);
    }
}
```

- `change window "WindowName";` is a dedicated AL statement. It accepts one quoted identifier literal matching `[A-Za-z][A-Za-z0-9_]*`; spaces, hyphens, expressions and non-string values are compile-time errors. The statement opens the native Android window and supplies its initial name.
- The window remains hidden until this statement executes. A program that only calls `UI.screen`, `UI.game`, `UI.text` or other view APIs does not open a window.
- `UI.screen(string title)` sets the screen title but does not open a native window.
- `UI.text(string id, string value)` creates or replaces a text view.
- `UI.button(string id, string label, string callbackName)` creates a button. The callback must be a global `void` function with one `string` parameter.
- `UI.setText(string id, stringOrInt value)` updates an existing text view.
- `UI.setInt(string key, int value)` stores integer state; `UI.getInt(string key, int defaultValue)` reads it.
- `UI.clear()` removes all current components and integer state.
- Button clicks pass the button ID to the AL callback. The C++ VM executes the callback, while the Android Java host creates and updates native Android Views.

The `.room` format is version 10. The `change window` statement lowers to the existing `UiCall` opcode with the `openWindow` operation; no new bytecode opcode or format migration is required. `examples/calculator.al` is a complete AL-authored calculator sample; neither its layout nor arithmetic is preloaded in the Android host.

## Stage 16: Native 2D drawing API

AL programs can create a native Android 2D drawing surface:

```al
change window "My2DGame";
UI.game("My 2D Game");
UI.rect("ground", 0, 400, 320, 80, 4278255360);
UI.circle("player", 80, 360, 18, 4294901760);
UI.line("platform", 10, 350, 300, 350, 4294967295);
UI.remove("player");
```

- The logical drawing surface is 320 by 480 units and scales to the available Android view.
- `UI.game(string title)` sets the game title and clears current UI elements; it does not open the native window.
- The Android host displays accumulated native UI only when the AL program executes `change window`. If that statement is absent, UI calls may update runtime state but the native window remains hidden.
- `UI.rect(string id, int x, int y, int width, int height, int argb)` draws a filled rectangle.
- `UI.circle(string id, int centerX, int centerY, int radius, int argb)` draws a filled circle.
- `UI.line(string id, int x1, int y1, int x2, int y2, int argb)` draws a line.
- Colors are ARGB integer values. For example, opaque red is 4294901760.
- Reusing an ID updates that drawing object. `UI.remove(string id)` removes it.
- The C++ ALVM executes drawing calls and serializes scene objects; Android renders them using a native Canvas-backed View.
- This stage provides a static 2D scene renderer. A timed game loop, sprite/image loading, touch callbacks, physics, and collision APIs are not implemented yet.

### Positioned canvas text

`UI.textAt(string id, string value, int x, int y, int size, int argb)` draws text at the supplied 320×480 logical-canvas coordinates. The `y` value is the text baseline, `size` must be between 6 and 96 logical pixels, and `argb` is a 32-bit ARGB color stored in an AL integer. Reusing an ID updates the existing text object. It is rendered in the same native Canvas layer as `UI.rect`, `UI.circle` and `UI.line`.

```al
change window "CanvasText";
UI.game("Canvas text");
UI.rect("background", 0, 0, 320, 480, 4278190335);
UI.textAt("title", "Hello from AL", 16, 44, 22, 4294967295);
```

The C++ compiler/VM and Rust CI Runtime implement identical `.room v10` `UiCall` behavior. Text is painted natively by Android's Canvas-backed Game2DView, not by a browser preview.

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

## Program entry declaration spelling

The preferred program form is Java-like:

```al
class Main {
    public static void main() {
        int result = 10 + 20;
        AL.print(result);
    }
}
```

The first `class Main { ... }` is the program container. The parser also accepts the older `room Name { ... }` and `Room Name { ... }` container spellings for compatibility. A `class Name { ... }` declaration inside the program container declares an AL object type. The container spelling does not turn AL into Java; AL's grammar, compiler, bytecode and runtime remain independent.

## Stage 17: floating-point scalar values and arrays

The compiler and ALVM support:

```al
float ratio = 1.5f;
double distance = 125.75;
float[] samples = new float[4];
double[] precise = [1.25, 2.5, 3.75];
```

- `float` is IEEE-754 binary32. A decimal literal with an `f` or `F` suffix has AL type `float`.
- `double` is IEEE-754 binary64. A decimal literal without an `f` suffix, or with a `d` / `D` suffix, has AL type `double`.
- Decimal literals use a decimal point and digits on both sides. Exponent notation is not implemented in this stage.
- Arithmetic `+`, `-`, `*`, `/`, `%`, unary minus and numeric comparisons work for matching operand types.
- Mixed numeric operations such as `float + double` are compile-time errors. There is no implicit numeric widening or narrowing yet.
- Float and double arrays support literals, sized allocation, default initialization, indexed reads/writes, `.length` and `foreach`.
- Float and double are supported as scalar class fields and function return types.
- Float payloads are serialized as IEEE-754 binary32 bits in the low 32 bits of the instruction's signed i64 argument. Double payloads use the complete 64-bit argument.
- This upgrade uses `.room` version 10. Version 9 bytecode is intentionally rejected and must be recompiled from `.al` source.

## Stage 18: AL.Math and AL.String core library

The following APIs are implemented by the C++ compiler and both the C++ desktop VM and Rust CI Runtime. They use the existing \`UiCall\` bytecode instruction in \`.room v10\`; no source code is executed by the runtime.

\`\`\`al
class Main {
    public static void main() {
        int absolute = AL.Math.abs(-7);
        int low = AL.Math.min(8, 3);
        double root = AL.Math.sqrt(81.0);
        int rounded = AL.Math.round(8.5);

        string upper = AL.String.upper("alvm");
        int bytes = AL.String.length("ALVM");
        boolean found = AL.String.contains("ALVM Studio", "Studio");
        string renamed = AL.String.replace("ALVM Studio", "Studio", "Language");

        AL.print(absolute);
        AL.print(low);
        AL.print(root);
        AL.print(rounded);
        AL.print(upper);
        AL.print(bytes);
        AL.print(found);
        AL.print(renamed);
    }
}
\`\`\`

- \`AL.Math.abs(int|float|double)\` preserves its argument type. The minimum signed \`int\` value raises overflow.
- \`AL.Math.min\` and \`AL.Math.max\` require two arguments of exactly the same numeric type.
- \`AL.Math.sqrt(double)\` returns \`double\` and rejects negative inputs.
- \`AL.Math.pow(double, double)\` returns \`double\`.
- \`AL.Math.floor\`, \`AL.Math.ceil\` and \`AL.Math.round\` each accept \`double\` and return \`int\`; values outside the signed 64-bit range fail.
- \`AL.String.length(string)\` returns UTF-8 byte length, not Unicode code-point or grapheme count.
- \`AL.String.upper\` and \`AL.String.lower\` perform ASCII-only case conversion.
- \`AL.String.contains\`, \`AL.String.startsWith\` and \`AL.String.endsWith\` return \`boolean\`.
- \`AL.String.replace(source, search, replacement)\` replaces all non-overlapping matches. An empty search string returns the source unchanged.
- Unknown library names and invalid argument types/counts are compile-time errors. Runtime checks remain in place for malformed bytecode.

This is the first native standard-library slice. It does not imply that \`AL.Collections\`, \`AL.Files\`, \`AL.Network\`, \`AL.Database\`, \`AL.Audio\` or the remaining \`AL.Math\` / \`AL.String\` APIs are implemented.

## Native game-loop, canvas touch and collision APIs

The Android native UI bridge supports these AL calls:

```al
change window "MyTouchGame";
UI.touchArea("move", 0, 0, 320, 480, "onTouch");
UI.frameLoop("update", 30);
boolean hit = UI.collides("player", "wall");
UI.stopLoop();
```

- `UI.touchArea(id, x, y, width, height, callback)` registers a logical-canvas hit area. The callback must be a top-level `void function(string event)`. Canvas coordinates are reported through `UI.getInt("touchX", 0)` and `UI.getInt("touchY", 0)` during the callback.
- `UI.frameLoop(callback, fps)` schedules the named top-level `void function(string event)` at 1–60 frames per second. The event value is `"frame"`. The Android host pauses the loop when the Activity pauses or the game dialog closes.
- `UI.stopLoop()` cancels the frame callback.
- `UI.collides(leftId, rightId)` returns true when two registered rectangle, sprite or circle bounding boxes overlap. This is axis-aligned bounding-box overlap detection, not collision response. Lines and touch areas are not collidable.
- `UI.sprite(id, assetPath, x, y, width, height)` adds an image element to the native 320×480 canvas. `assetPath` is relative to the Android application's asset directory, for example `sprites/player.png`; absolute paths, backslashes, empty path segments and `.` / `..` traversal segments are rejected. Width and height must be 1–3200 logical pixels. A missing packaged image is shown as a visible placeholder rather than crashing the app.
- `UI.setVelocityY(id, velocity)` sets the vertical velocity in logical pixels per second for an existing rectangle, circle or sprite. The velocity must be a finite `double` within ±100000.
- `UI.physicsStep(id, gravity, deltaSeconds)` advances that object's vertical motion using a semi-implicit Euler step. Both values must be finite `double` values; gravity is limited to ±10000 and deltaSeconds to 0–0.1 seconds. This is a vertical gravity integrator, not a full rigid-body physics solver; horizontal velocity, restitution, friction and collision response are not implemented.
- The APIs are implemented in the C++ desktop VM and Rust CI Runtime, use existing `UiCall` bytecode and keep `.room v10` unchanged. Android Game2DView and the standalone Player render sprite assets from the APK's `assets/` directory.


## Stage 22: native game-engine foundation (partial)

These additional calls use the existing `UiCall` instruction and keep `.room v10` unchanged. The C++ compiler, C++ desktop VM and Rust CI Runtime share the same argument contract.

```al
class Main {
    public static void main() {
        change window "EngineDemo";
        UI.game("AL Game");

        UI.node("world");
        UI.transform("world", 0, 0, 1.0, 1.0, 0.0);

        UI.spriteSheet("hero", "sprites/hero.png", 16, 24, 32, 32, 4, 2);
        UI.animateSprite("hero", 8, 12, true);
        UI.attach("hero", "world");

        UI.rect("player", 24, 90, 28, 28, 4294901760);
        UI.physicsBody("player", 1.0, 0.0, 0.0);
        UI.physicsWorldStep(0.016, 0.0, 180.0, 0.25);

        UI.keyArea("left", "LEFT", "onKey");

        UI.camera3D(0, 0, -300, 0, 0, 240);
        UI.cube3D("cube", 0, 0, 0, 64, 4294967295);
        UI.transform3D("cube", 0, 0, 0, 0, 20, 0, 1.0);
        UI.material3D("cube", 0.8);
    }
}

void onKey(string key) {
    AL.print(key);
}
```

- `UI.node(id)` creates a scene node. `UI.attach(childId, parentNodeId)` sets a parent after both items exist. Cycles and scene chains deeper than 64 are rejected. `UI.detach(id)` removes the parent link.
- `UI.transform(id, x, y, scaleX, scaleY, rotationDegrees)` sets a 2D drawable or node transform. Scale must be between 0.05 and 20. Parent-node transforms are applied by the Android Canvas renderer.
- `UI.spriteSheet(id, assetPath, x, y, width, height, columns, rows)` registers a sprite sheet. Grid dimensions are 1–64, with at most 1024 cells. The asset path follows the same relative-path safety rules as `UI.sprite`.
- `UI.animateSprite(id, frameCount, fps, loop)` enables 1–60 FPS sprite animation. `UI.setSpriteFrame(id, frame)` selects a static grid frame.
- `UI.keyArea(id, key, callback)` registers `ANY`, `LEFT`, `RIGHT`, `UP`, `DOWN`, `SPACE`, `ENTER`, `ESCAPE`, or A–Z. Callback must be a top-level `void function(string event)`.
- `UI.physicsBody(id, mass, velocityX, velocityY)` adds a body for a rectangle, circle or sprite. Mass zero is static. `UI.physicsWorldStep(deltaSeconds, gravityX, gravityY, restitution)` advances all registered bodies. Delta is 0–0.1 seconds, restitution 0–1 and gravity components are bounded to ±10000. Current collision resolution uses axis-aligned boxes and a simple normal impulse; friction, rotation-aware shapes, continuous collision detection and a full rigid-body solver are not implemented.
- `UI.camera3D(x, y, z, yawDegrees, pitchDegrees, focalLength)` sets the camera. Yaw is limited to ±180°, pitch to ±89°, focal length to 32–2000.
- `UI.cube3D(id, x, y, z, size, color)` adds a cube primitive. `UI.transform3D(id, x, y, z, rotationX, rotationY, rotationZ, scale)` sets its Euler rotation and uniform scale (0.05–20). `UI.material3D(id, lightIntensity)` sets the simple face-lighting multiplier (0–1). `UI.collides3D(leftId, rightId)` performs an axis-aligned cube overlap test.
- Android and the generated Player currently draw 3D cubes through perspective-projected Android Canvas polygons. This is a 3D foundation, not a GPU mesh renderer: there is no arbitrary mesh import, depth buffer, texture material pipeline, shadowing or rotation-aware 3D collision.
- `UI.frameLoop(callback, fps)` remains the deterministic callback/timing entry point. Use it to call `UI.physicsWorldStep`; the Android host pauses the loop with the Activity lifecycle.

## Local source imports (partial module linking)

The C++ command-line compiler and Android JNI compiler accept a top-level source directive such as:

```al
class Main {
    import "modules/math_utils.al";

    public static void main() {
        AL.print(addScore(20, 22));
    }
}
```

Imports are expanded before parsing, bounded to 16 nested levels, 1 MiB per imported file and 4 MiB total source. Only relative `.al` paths inside the project source folder are accepted. Absolute paths, `.` / `..` traversal, symlink escapes, missing modules and cycles are rejected. Imported functions are compiled into the same `.room` entry module. This is not yet an independent multi-module `.AL` linker: ALPK v1 still contains `modules/main.room`, and pinned external dependency resolution is not implemented.
