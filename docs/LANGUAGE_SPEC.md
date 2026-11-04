# AL Language Specification: Stage 1

AL uses approachable Python-influenced vocabulary, Java-like block and method writing style, and strict C++-influenced rules. Only the subset below is executable today.

## Grammar

```ebnf
program = ("room" | "class"), identifier, "{", ["public"], ["static"],
          ("void" | "int"), "main", "(", ")", "{", { statement }, "}", "}" ;
statement = variableDecl | assignment | printStmt | returnStmt ;
variableDecl = ("int" | "var" | "string"), identifier, "=", expression, ";" ;
assignment = identifier, "=", expression, ";" ;
printStmt = "AL", ".", "print", "(", expression, ")", ";" ;
returnStmt = "return", [expression], ";" ;
expression = term, { ("+" | "-"), term } ;
term = primary, { ("*" | "/"), primary } ;
primary = integer | string | identifier | "(", expression, ")" ;
```

## Semantics

- Variables must be declared before use. Duplicate local names are rejected.
- Arithmetic currently operates on integers. Division by zero raises a runtime error.
- AL.print writes a value followed by a newline.
- Every statement ends in a semicolon.
- Full static type inference/checking is not yet implemented.
- Reserved future features are not accepted as executable syntax in Stage 1.