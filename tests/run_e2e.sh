#!/usr/bin/env bash
set -euo pipefail
BIN="$1"
ROOT="$2"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

"$BIN" compile "$ROOT/examples/hello.al" -o "$TMP/hello.room"
HELLO_OUT="$("$BIN" run "$TMP/hello.room")"
test "$HELLO_OUT" = "30"

"$BIN" compile "$ROOT/examples/control_flow.al" -o "$TMP/control.room"
CONTROL_OUT="$("$BIN" run "$TMP/control.room")"
EXPECTED="$(printf 'sum is ten\n10')"
test "$CONTROL_OUT" = "$EXPECTED"

"$BIN" compile "$ROOT/examples/short_circuit.al" -o "$TMP/logic.room"
LOGIC_OUT="$("$BIN" run "$TMP/logic.room")"
test "$LOGIC_OUT" = "short circuit ok"

if "$BIN" compile "$ROOT/tests/type_error.al" -o "$TMP/invalid.room" 2>/dev/null; then
  echo "FAIL: type error was accepted" >&2
  exit 1
fi
test ! -e "$TMP/invalid.room"

"$BIN" disasm "$TMP/control.room" | grep -q 'op='
echo "PASS: AST -> semantic analysis -> IR -> .room v2 -> verifier -> ALVM"
