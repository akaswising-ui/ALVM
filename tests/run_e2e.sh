#!/usr/bin/env bash
set -euo pipefail
BIN="$1"
ROOT="$2"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

"$BIN" compile "$ROOT/examples/hello.al" -o "$TMP/hello.room"
test "$("$BIN" run "$TMP/hello.room")" = "30"

"$BIN" compile "$ROOT/examples/control_flow.al" -o "$TMP/control.room"
EXPECTED="$(printf 'sum is ten\n10')"
test "$("$BIN" run "$TMP/control.room")" = "$EXPECTED"

"$BIN" compile "$ROOT/examples/short_circuit.al" -o "$TMP/logic.room"
test "$("$BIN" run "$TMP/logic.room")" = "short circuit ok"

"$BIN" compile "$ROOT/examples/arrays.al" -o "$TMP/arrays.room"
ARRAY_EXPECTED="$(printf '10\n3\n18\n7\nAL Bytecode\n2\nflags ok')"
test "$("$BIN" run "$TMP/arrays.room")" = "$ARRAY_EXPECTED"

if "$BIN" compile "$ROOT/tests/type_error.al" -o "$TMP/type-invalid.room" 2>/dev/null; then
  echo "FAIL: scalar type error was accepted" >&2
  exit 1
fi
if "$BIN" compile "$ROOT/tests/array_type_error.al" -o "$TMP/array-invalid.room" 2>/dev/null; then
  echo "FAIL: array type error was accepted" >&2
  exit 1
fi
test ! -e "$TMP/type-invalid.room"
test ! -e "$TMP/array-invalid.room"

"$BIN" compile "$ROOT/tests/bounds_error.al" -o "$TMP/bounds.room"
if "$BIN" run "$TMP/bounds.room" >/dev/null 2>&1; then
  echo "FAIL: out-of-bounds array access was accepted" >&2
  exit 1
fi

"$BIN" disasm "$TMP/arrays.room" | grep -q 'op='
echo "PASS: typed arrays, bounds checks, AST, type analysis, IR and .room v3"
