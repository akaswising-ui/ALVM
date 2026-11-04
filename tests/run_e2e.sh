#!/usr/bin/env bash
set -euo pipefail
BIN="$1"
ROOT="$2"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

"$BIN" compile "$ROOT/examples/hello.al" -o "$TMP/hello.room"
test "$("$BIN" run "$TMP/hello.room")" = "30"

"$BIN" compile "$ROOT/examples/control_flow.al" -o "$TMP/control.room"
CONTROL_EXPECTED="$(printf 'sum is ten\n10')"
test "$("$BIN" run "$TMP/control.room")" = "$CONTROL_EXPECTED"

"$BIN" compile "$ROOT/examples/short_circuit.al" -o "$TMP/logic.room"
test "$("$BIN" run "$TMP/logic.room")" = "short circuit ok"

"$BIN" compile "$ROOT/examples/arrays.al" -o "$TMP/arrays.room"
ARRAY_EXPECTED="$(printf '10\n3\n18\n7\nAL Bytecode\n2\nflags ok')"
test "$("$BIN" run "$TMP/arrays.room")" = "$ARRAY_EXPECTED"

"$BIN" compile "$ROOT/examples/functions.al" -o "$TMP/functions.room"
FUNCTION_EXPECTED="$(printf '30\n120\nHello AL\n15')"
test "$("$BIN" run "$TMP/functions.room")" = "$FUNCTION_EXPECTED"

"$BIN" compile "$ROOT/examples/objects.al" -o "$TMP/objects.room"
OBJECT_EXPECTED="$(printf '75\n90\n95\nALVM\ntrue\n100')"
test "$("$BIN" run "$TMP/objects.room")" = "$OBJECT_EXPECTED"

for fixture in type_error array_type_error function_type_error missing_return object_field_type_error object_method_type_error; do
  if "$BIN" compile "$ROOT/tests/$fixture.al" -o "$TMP/$fixture.room" 2>/dev/null; then
    echo "FAIL: $fixture was accepted" >&2
    exit 1
  fi
  test ! -e "$TMP/$fixture.room"
done

"$BIN" compile "$ROOT/tests/bounds_error.al" -o "$TMP/bounds.room"
if "$BIN" run "$TMP/bounds.room" >/dev/null 2>&1; then
  echo "FAIL: out-of-bounds array access was accepted" >&2
  exit 1
fi

"$BIN" disasm "$TMP/objects.room" | grep -q 'class Player'
"$BIN" disasm "$TMP/objects.room" | grep -q 'function Player.add'
echo "PASS: functions, recursion, arrays, classes, objects, instance methods and runtime"
