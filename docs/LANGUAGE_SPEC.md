# AL Language Specification: Stage 3

## Language identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. It has its own compiler pipeline and bytecode VM. The implementation is not a wrapper around Python, Java or JavaScript.

Native program declaration: `room Main`. The earlier `class Main` spelling remains accepted for compatibility.

## Executable grammar

The following grammar describes the implemented subset, not the planned full language.

```ebnf
program       = ("room" | "class"), identifier, "{",
                ["public"], ["static"], ("void" | "int"), "main", "(", ")", block, "}" ;
block         = "{", { statement }, "}" ;
statement     = block | variableDecl | assignment | arrayAssignment | printStmt | returnStmt
              | ifStmt | whileStmt | forStmt ;
variableDecl  = type, identifier, "=", expression, ";" ;
type          = ("int" | "string" | "boolean"), ["[", "]"] | "var" ;
assignment    = identifier, "=", expression, ";" ;
arrayAssignment = identifier, "[", expression, "]", "=", expression, ";" ;
printStmt     = "AL", ".", "print", "(", expression, ")", ";" ;
returnStmt    = "return", ";" ;
ifStmt        = "if", "(", expression, ")", block, ["else", block] ;
whileStmt     = "while", "(", expression, ")", block ;
forStmt       = "for", "(", (variableDeclNoSemi | assignment), ";",
                expression, ";", assignment, ")", block ;
expression    = logicalOr ;
logicalOr     = logicalAnd, { "||", logicalAnd } ;
logicalAnd    = equality, { "&&", equality } ;
equality      = comparison, { ("==" | "!="), comparison } ;
comparison    = additive, { ("<" | ">" | "<=" | ">="), additive } ;
additive      = multiplicative, { ("+" | "-"), multiplicative } ;
multiplicative= unary, { ("*" | "/" | "%"), unary } ;
unary         = ("!" | "-"), unary | postfix ;
postfix       = primary, { "[", expression, "]" | ".", "length" } ;
primary       = integer | string | "true" | "false" | identifier
              | "[", expression, { ",", expression }, "]"
              | "(", expression, ")" ;
```

## Types and semantics

- Scalar types are `int`, `string`, `boolean`. Array types are `int[]`, `string[]`, and `boolean[]`.
- `var` infers its type from the initializer. Empty array literals are not supported because their element type cannot be inferred.
- Array literals must be non-empty and every element must have the same scalar type.
- Variables must be declared before use. A name cannot be declared twice in the same scope. Nested blocks can shadow outer names.
- Assignments must match the declared type.
- Arrays support indexed reads `values[index]`, indexed writes `values[index] = value`, and the read-only `.length` property.
- Array indexes must be integers. Negative or out-of-range indexes raise a runtime error.
- Array assignment copies the array reference, not the elements. Two variables assigned the same array refer to the same mutable array.
- `AL.print` prints the value and a newline. Printing an array currently displays `[array length=n]`.
- `int` supports `+`, `-`, `*`, `/`, `%`, unary minus and integer comparisons.
- `string + string` concatenates strings.
- `==` and `!=` require operands of the same type. Array equality currently checks reference identity.
- `&&` and `||` require booleans and use short-circuit evaluation.
- `!` requires boolean. A condition in `if`, `while` or `for` must be boolean.
- Integer overflow and division by zero are reported as runtime errors.
- The current entry method is `main()`. User-defined methods and value-returning functions are not implemented yet.
- Blocks are required after `if`, `else`, `while` and `for`.
- The `for` initializer is a declaration or assignment; the increment clause is an assignment.

## Compiler and runtime

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowerer -> .room v3 -> verifier -> ALVM`

The bytecode verifier performs control-flow stack-depth analysis before execution. The VM reads only compiled `.room` files.

## .room v3

Little-endian encoding:
- 4-byte `ALRM` magic.
- uint32 version = 3.
- uint32 instruction count.
- Each instruction: uint8 opcode, uint64 operand, uint32 payload length, payload bytes.

Stage 3 intentionally rejects Stage 1 v1 and Stage 2 v2 `.room` files. Recompile their `.al` source to generate version 3.

## Planned, not yet executable

User functions and recursion, OOP, interfaces, inheritance, generics, exceptions, modules, file/network/database APIs, graphics/game/audio APIs, `.AL` packaging, Android integration and graphical IDE are future stages. Their names in a roadmap do not mean they work in the current VM.
