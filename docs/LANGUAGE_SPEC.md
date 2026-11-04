# AL Language Specification: Stage 2

## Language identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. It has its own compiler pipeline and bytecode VM. The implementation is not a wrapper around Python, Java or JavaScript.

Native program declaration: `room Main`. The earlier `class Main` spelling remains accepted for compatibility.

## Executable grammar

The following grammar describes the implemented subset, not the planned full language.

```ebnf
program       = ("room" | "class"), identifier, "{",
                ["public"], ["static"], ("void" | "int"), "main", "(", ")", block, "}" ;
block         = "{", { statement }, "}" ;
statement     = block | variableDecl | assignment | printStmt | returnStmt
              | ifStmt | whileStmt | forStmt ;
variableDecl  = ("int" | "string" | "boolean" | "var"), identifier, "=", expression, ";" ;
assignment    = identifier, "=", expression, ";" ;
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
unary         = ("!" | "-"), unary | primary ;
primary       = integer | string | "true" | "false" | identifier
              | "(", expression, ")" ;
```

## Types and semantics

- Supported declared types are `int`, `string`, `boolean` and inferred `var`.
- Variables must be declared before use. A name cannot be declared twice in the same scope. Nested blocks can shadow outer names.
- `var` takes the initializer's type. Assignments must match the declared type.
- `int` supports `+`, `-`, `*`, `/`, `%`, unary minus and integer comparisons.
- `string + string` concatenates strings.
- `==` and `!=` require operands of the same type.
- `&&` and `||` require booleans and use short-circuit evaluation.
- `!` requires boolean. A condition in `if`, `while` or `for` must be boolean.
- `AL.print` prints the value and a newline.
- Integer overflow and division by zero are reported as runtime errors.
- The current entry method is `main()`. User-defined methods and value-returning functions are not implemented yet.
- Blocks are required after `if`, `else`, `while` and `for`.
- The `for` initializer is a declaration or assignment; the increment clause is an assignment.

## Compiler and runtime

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowerer -> .room v2 -> verifier -> ALVM`

The bytecode verifier performs control-flow stack-depth analysis before execution. The VM reads only compiled `.room` files.

## .room v2

Little-endian encoding:
- 4-byte `ALRM` magic.
- uint32 version = 2.
- uint32 instruction count.
- Each instruction: uint8 opcode, uint64 operand, uint32 payload length, payload bytes.

Stage 2 intentionally rejects Stage 1 version 1 `.room` files. Recompile their `.al` source to generate version 2.

## Planned, not yet executable

Arrays and collections, user functions and recursion, OOP, interfaces, inheritance, generics, exceptions, modules, file/network/database APIs, graphics/game/audio APIs, `.AL` packaging, Android integration and graphical IDE are future stages. Their names in a roadmap do not mean they work in the current VM.
