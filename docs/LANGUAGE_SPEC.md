# AL Language Specification: Stage 4

## Identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. It owns its compiler pipeline and bytecode VM. It is not a wrapper around Python, Java or JavaScript.

Native declaration: `room Main`; earlier `class Main` syntax remains accepted.

## Implemented grammar

This grammar documents the implemented subset, not the final planned language.

```ebnf
program          = ("room" | "class"), identifier, "{", { functionDecl }, "}" ;
functionDecl     = { "public" | "private" | "protected" | "static" },
                   type, identifier, "(", [ parameterList ], ")", block ;
parameterList    = parameter, { ",", parameter } ;
parameter        = scalarType, identifier ;
type             = scalarType, [ "[", "]" ] | "void" ;
scalarType       = "int" | "string" | "boolean" ;
block            = "{", { statement }, "}" ;
statement        = block | variableDecl | assignment | arrayAssignment
                 | expressionStatement | printStmt | returnStmt
                 | ifStmt | whileStmt | forStmt ;
variableDecl     = (scalarType, [ "[", "]" ] | "var"), identifier, "=", expression, ";" ;
assignment       = identifier, "=", expression, ";" ;
arrayAssignment  = identifier, "[", expression, "]", "=", expression, ";" ;
expressionStatement = expression, ";" ;
printStmt        = "AL", ".", "print", "(", expression, ")", ";" ;
returnStmt       = "return", [ expression ], ";" ;
ifStmt           = "if", "(", expression, ")", block, [ "else", block ] ;
whileStmt        = "while", "(", expression, ")", block ;
forStmt          = "for", "(", (variableDeclNoSemi | assignment), ";",
                   expression, ";", assignment, ")", block ;
expression       = logicalOr ;
logicalOr        = logicalAnd, { "||", logicalAnd } ;
logicalAnd       = equality, { "&&", equality } ;
equality         = comparison, { ("==" | "!="), comparison } ;
comparison       = additive, { ("<" | ">" | "<=" | ">="), additive } ;
additive         = multiplicative, { ("+" | "-"), multiplicative } ;
multiplicative   = unary, { ("*" | "/" | "%"), unary } ;
unary            = ("!" | "-"), unary | postfix ;
postfix          = primary, { "[", expression, "]" | ".", "length" } ;
primary          = integer | string | "true" | "false" | identifier
                 | functionCall | arrayLiteral | "(", expression, ")" ;
functionCall     = identifier, "(", [ expression, { ",", expression } ], ")" ;
arrayLiteral     = "[", expression, { ",", expression }, "]" ;
```

## Types and semantics

- Scalar types: `int`, `string`, `boolean`. Array types: `int[]`, `string[]`, `boolean[]`. `var` infers from the initializer.
- Function parameters and return values support the scalar and array types. Parameters require explicit types.
- `main` must be `void main()` with no parameters.
- Function names are unique within the room; overloading is not implemented.
- Calls are direct within the same room and can be nested or recursive. Runtime call-frame depth is limited to 1024.
- Argument count and types are checked before bytecode generation.
- A non-void function must return a value of the declared type. Current analysis recognizes direct returns, sequential-block returns, and an if/else where both branches return; it does not prove loop termination.
- Void functions may use `return;` but not return a value. Non-void functions cannot use an empty return.
- Modifiers `public`, `private`, `protected`, `static` are accepted as syntax only; access control and instance methods are not implemented.
- Arrays can be passed to and returned from functions. Array values are references, so aliases share mutation.
- Array literals must be non-empty and homogeneous. Negative/out-of-range indexes are runtime errors. `.length` returns int.
- Variables must be declared before use. Duplicate names in a scope are rejected; nested blocks can shadow names.
- `int` supports arithmetic and comparisons; `string + string` concatenates.
- `==` and `!=` require the same operand type. Array equality checks reference identity.
- Boolean `&&` and `||` short-circuit. Conditions in `if`, `while` and `for` must be boolean.
- Integer overflow and division by zero are runtime errors. `AL.print` writes a value and newline.

## Compiler and .room v4

Pipeline: `.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowerer -> .room v4 -> verifier -> ALVM`.

The v4 file stores a function table. All integer fields are little-endian: `ALRM` magic, uint32 version 4, uint32 function count; each function stores a length-prefixed name, uint8 return-type tag, uint32 parameter count, uint32 local count, uint32 instruction count, followed by instructions of uint8 opcode, uint64 operand, and length-prefixed payload.

The verifier checks function names, local slots, call targets, control-flow targets, stack depths and return-stack requirements. The VM executes compiled bytecode only.

Stage 4 rejects older .room v1/v2/v3 files. Recompile source files to produce v4.

## Planned, not yet executable

Overloading, lambdas/closures, classes/objects, fields, constructors, inheritance, interfaces, generics, enums, exceptions, modules, standard libraries, files/network/database APIs, graphics/game/audio APIs, `.AL` packaging, Android integration and GUI IDE remain future features.
