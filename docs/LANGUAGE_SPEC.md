# AL Language Specification: Stage 5

## Identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. It owns its compiler pipeline and bytecode VM. It is not a wrapper around Python, Java or JavaScript.

Native declaration: `room Main`; earlier `class Main` root syntax remains accepted for compatibility.

## Implemented grammar

This describes the implemented subset only.

```ebnf
program          = ("room" | "class"), identifier, "{",
                   { functionDecl | classDecl }, "}" ;
classDecl        = "class", identifier, "{", { fieldDecl | methodDecl }, "}" ;
fieldDecl        = scalarType, identifier, ";" ;
methodDecl       = { "public" | "private" | "protected" | "static" },
                   type, identifier, "(", [ parameterList ], ")", block ;
functionDecl     = { "public" | "private" | "protected" | "static" },
                   type, identifier, "(", [ parameterList ], ")", block ;
parameterList    = parameter, { ",", parameter } ;
parameter        = scalarType, identifier ;
type             = scalarType, [ "[", "]" ] | classType | "void" ;
scalarType       = "int" | "string" | "boolean" ;
classType        = identifier ;
block            = "{", { statement }, "}" ;
statement        = block | variableDecl | assignment | arrayAssignment
                 | fieldAssignment | expressionStatement | printStmt | returnStmt
                 | ifStmt | whileStmt | forStmt ;
variableDecl     = (scalarType, [ "[", "]" ] | classType | "var"),
                   identifier, "=", expression, ";" ;
assignment       = identifier, "=", expression, ";" ;
arrayAssignment  = identifier, "[", expression, "]", "=", expression, ";" ;
fieldAssignment  = identifier, ".", identifier, "=", expression, ";" ;
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
postfix          = primary, { "[", expression, "]" | ".", identifier,
                                [ "(", [ argumentList ], ")" ] | ".", "length" } ;
primary          = integer | string | "true" | "false" | identifier
                 | "this" | "new", classType, "(", ")"
                 | functionCall | arrayLiteral | "(", expression, ")" ;
functionCall     = identifier, "(", [ argumentList ], ")" ;
argumentList     = expression, { ",", expression } ;
arrayLiteral     = "[", expression, { ",", expression }, "]" ;
```

## Types and semantics

- Scalar types: `int`, `string`, `boolean`; arrays: `int[]`, `string[]`, `boolean[]`; class types are named by the class identifier.
- `var` infers a type from its initializer. Empty array literals and arrays of objects are not supported.
- Class declarations appear inside the room. Classes must be declared in the room source; their names are pre-registered before member parsing.
- Class fields currently support scalar `int`, `string` and `boolean` only. Default values are `0`, empty string and `false`. Fields cannot have explicit initializer expressions yet.
- Objects are created with `new ClassName()`; user-defined constructors are not implemented.
- Field reads use `object.field`; writes use `object.field = expression;`. Field name and assigned type are checked at compile time.
- Instance methods use `object.method(args)`. Arguments are type-checked. The hidden `this` local references the receiver inside a method.
- Object assignment copies a reference; two variables can refer to the same object. Object equality currently uses reference identity.
- Class fields/methods may be tagged with `public`, `private`, `protected` or `static`, but those modifiers are parsed only. Access restriction and static method/field semantics are not implemented. All methods use instance-call semantics.
- Inheritance, interfaces, polymorphism and abstract classes are not implemented.
- Function names and method names must be unique within their scope; overloading is not implemented.
- `main` must be a global `void main()` with no parameters.
- Functions support typed scalar and array parameters/returns; calls can be nested and recursive. Object parameters and return values are type-checked too.
- Maximum VM call-frame depth is 1024.
- Non-void functions must return a matching value on every recognized path. Analysis recognizes direct/sequential returns and if/else where both branches return; it does not prove loop termination.
- Arrays are non-empty, homogeneous, typed literals. Indexes must be integers and are checked at runtime. `.length` returns int.
- Variables must be declared before use; nested scopes may shadow outer variables.
- Integer arithmetic supports `+`, `-`, `*`, `/`, `%`, unary minus and comparisons. `string + string` concatenates.
- Boolean `&&` and `||` short-circuit. Conditions must be boolean.
- Integer overflow and division by zero are runtime errors. `AL.print` writes a value and newline; objects currently print as `[object]`.

## Compiler and .room v5

Pipeline: `.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowerer -> .room v5 -> verifier -> ALVM`.

The v5 file stores class metadata followed by the function table. Integers are little-endian. Each class stores a name and scalar field type tags. Each function stores a name, owner class id (-1 for global functions), return type, parameter count, local count and instruction count; instructions contain opcode, 64-bit operand and length-prefixed payload.

The verifier checks class/function names, valid method owners, local slots, class allocation targets, call targets, control-flow targets, stack depths and return stack requirements. The VM runs compiled bytecode only.

Stage 5 rejects .room v1-v4. Recompile source files.

## Planned, not yet executable

Constructors, inheritance, interfaces, polymorphism, abstract classes, enforced access control, static members, object arrays, overloading, generics, enums, exceptions, modules, standard libraries, file/network/database APIs, graphics/game/audio APIs, `.AL` packaging, Android integration and graphical IDE are future features.
