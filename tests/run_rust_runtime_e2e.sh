#!/usr/bin/env bash
set -euo pipefail

ALVM_BIN="${1:?usage: run_rust_runtime_e2e.sh <alvm-compiler> <repo-root> <rust-runtime-runner>}"
ROOT="${2:?missing repository root}"
RUST_RUNNER="${3:?missing Rust CI Runtime executable}"
ALPACK_BIN="${4:?missing AL application packager}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

"$ALVM_BIN" compile "$ROOT/examples/hello.al" -o "$WORK/hello.room"
HELLO="$("$RUST_RUNNER" run "$WORK/hello.room")"
printf '%s\n' "$HELLO" | grep -q '"ok":true'
printf '%s\n' "$HELLO" | grep -q '"output":"30"'

"$ALVM_BIN" compile "$ROOT/examples/calculator.al" -o "$WORK/calculator.room"
CALCULATOR="$("$RUST_RUNNER" run "$WORK/calculator.room" \
  --event onKey 2 --event onKey 3 --event onKey plus \
  --event onKey 4 --event onKey equals)"
printf '%s\n' "$CALCULATOR" | grep -q '"ok":true'
printf '%s\n' "$CALCULATOR" | grep -q '"text":"27"'

"$ALVM_BIN" compile "$ROOT/examples/floating_point.al" -o "$WORK/floating-point.room"
FLOATING="$("$RUST_RUNNER" run "$WORK/floating-point.room")"
printf '%s\n' "$FLOATING" | grep -q '"ok":true'
printf '%s\n' "$FLOATING" | grep -Fq '"output":"3.75\n4.75\n3.75\n2\n3.75\ntrue"'

printf 'packaged asset\n' > "$WORK/asset.txt"
"$ALPACK_BIN" create "$WORK/Floating.AL" FloatingPoint 1.0.0 "$WORK/floating-point.room" "$WORK/asset.txt"
PACKAGE_RESULT="$("$ALPACK_BIN" verify "$WORK/Floating.AL")"
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"ok":true'
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"moduleCount":1'
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"assetCount":1'

echo "Rust CI Runtime and .AL package end-to-end tests passed."
