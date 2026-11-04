# ALVM Studio

AL is an independent language: Python-influenced vocabulary, Java-like source style, and strict C++-influenced rules. It has its own lexer, parser, AST, semantic analyzer, IR, bytecode, verifier and VM.

## Stage 9: array foreach iteration

AL now supports typed `foreach` loops over `int[]`, `string[]` and `boolean[]` arrays. The array expression is evaluated once, iteration follows ascending index order, and `break` / `continue` target the correct enclosing loop. The compiler rejects non-array sources and explicit element-type mismatches. The loop is lowered to existing array-length, array-get and jump instructions, so `.room` v7 does not need an ABI change.

```al
int[] scores = [10, 20, 30];
foreach (int score : scores) {
    AL.print(score);
}
```

## Stage 8: control flow

Current C++17 compiler pipeline:

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode -> .room v7 -> verifier -> ALVM`

Implemented:
- Scalar and array types, arithmetic, string operations, comparisons and boolean short-circuiting.
- Typed functions, parameters, return values and recursion.
- Classes, fields, objects, constructors, instance methods, `this`, inheritance and derived-to-base assignment.
- `if/else`, `while`, `for`, `do/while` and typed `foreach` array iteration.
- `switch/case/default` for `int`, `string` and `boolean` expressions.
- `break` exits the nearest loop or switch.
- `continue` jumps to the condition of a `while`/do-while loop or the increment clause of a `for` loop.
- Switch cases use fall-through semantics. Use `break;` to stop at the end of a case.
- The compiler rejects `break` outside loops/switches, `continue` outside loops, duplicate default labels and mismatched case types.
- Branches are lowered into jumps and validated by the existing bytecode stack-depth verifier.
- End-to-end regression tests cover numeric and string switches, loop continue/break, typed foreach iteration and negative cases.

## Example

```al
room Main {
    public static void main() {
        int option = 2;
        switch (option) {
            case 1:
                AL.print("one");
                break;
            case 2:
                AL.print("two");
                break;
            default:
                AL.print("other");
        }

        int count = 0;
        do {
            count = count + 1;
            if (count == 2) {
                continue;
            }
            if (count == 4) {
                break;
            }
        } while (count < 10);
        AL.print(count);
    }
}
```

## Still pending

Function/method overloading, constructors with `super(arguments)`, method overriding/virtual dispatch, interfaces, abstract classes, static members, enforced access control, object arrays, generics, enums, exceptions, modules, full standard libraries, graphics/game/audio APIs, graphical IDE, `.AL` packaging and Android APK generation remain pending.

## Build

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
./build/alvm compile examples/control_flow_complete.al
./build/alvm run examples/control_flow_complete.room
ctest --test-dir build --output-on-failure
```

The `.room` binary format remains v7 because this stage uses existing jump instructions and adds no bytecode opcodes. Recompile source when the file format changes in later stages.
