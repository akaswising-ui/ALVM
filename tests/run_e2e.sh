#!/usr/bin/env bash
set -euo pipefail
BIN="$1"; ROOT="$2"; TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
"$BIN" compile "$ROOT/examples/hello.al" -o "$TMP/test.room"
OUT="$("$BIN" run "$TMP/test.room")"
test "$OUT" = "30"
"$BIN" disasm "$TMP/test.room" | grep -q 'opcode'
echo "PASS: .al -> binary .room -> verifier -> ALVM; output 30"