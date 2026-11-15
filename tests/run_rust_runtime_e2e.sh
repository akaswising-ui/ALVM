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

"$ALVM_BIN" compile "$ROOT/examples/stdlib_core.al" -o "$WORK/stdlib-core.room"
STDLIB_RESULT="$("$RUST_RUNNER" run "$WORK/stdlib-core.room")"
printf '%s\n' "$STDLIB_RESULT" | grep -q '"ok":true'
printf '%s\n' "$STDLIB_RESULT" | grep -Fq '"output":"7\n3\n8.5\n9\n8\n9\n9\n32\nALVM\nalvm\n4\ntrue\ntrue\ntrue\nALVM Language"'

"$ALVM_BIN" compile "$ROOT/examples/touch_loop.al" -o "$WORK/touch-loop.room"
GAME_MAIN="$("$RUST_RUNNER" run "$WORK/touch-loop.room")"
printf '%s\n' "$GAME_MAIN" | grep -q '"ok":true'
printf '%s\n' "$GAME_MAIN" | grep -q '"frameCallback":"update"'
printf '%s\n' "$GAME_MAIN" | grep -q '"frameRate":30'
printf '%s\n' "$GAME_MAIN" | grep -q '"output":"true"'
GAME_EVENTS="$("$RUST_RUNNER" run "$WORK/touch-loop.room" --event onTouch '@touch|move|45|67' --event update frame --event stopGame stop)"
printf '%s\n' "$GAME_EVENTS" | grep -q '"ok":true'
printf '%s\n' "$GAME_EVENTS" | grep -q '"id":"touch","kind":"text","text":"45"'
printf '%s\n' "$GAME_EVENTS" | grep -q '"id":"frames","kind":"text","text":"1"'
printf '%s\n' "$GAME_EVENTS" | grep -q '"frameRate":0'
"$ALVM_BIN" compile "$ROOT/examples/treasure_grid.al" -o "$WORK/treasure-grid.room"
TREASURE_MAIN="$("$RUST_RUNNER" run "$WORK/treasure-grid.room")"
printf '%s\n' "$TREASURE_MAIN" | grep -q '"ok":true'
printf '%s\n' "$TREASURE_MAIN" | grep -q '"title":"TREASURE GRID"'
TREASURE_EVENTS="$("$RUST_RUNNER" run "$WORK/treasure-grid.room" --event pickTile tile2 --event pickTile tile4 --event pickTile tile7 --event pickTile tile14 --event pickTile tile16)"
printf '%s\n' "$TREASURE_EVENTS" | grep -q '"ok":true'
printf '%s\n' "$TREASURE_EVENTS" | grep -q '"id":"score","kind":"text","text":"13"'
printf '%s\n' "$TREASURE_EVENTS" | grep -q '"id":"moves","kind":"text","text":"5"'
TREASURE_RESET="$("$RUST_RUNNER" run "$WORK/treasure-grid.room" --event pickTile tile2 --event restartGame reset)"
printf '%s\n' "$TREASURE_RESET" | grep -q '"id":"score","kind":"text","text":"0"'
printf '%s\n' "$TREASURE_RESET" | grep -q '"id":"tile2","kind":"button","text":"2"'

printf 'packaged asset\n' > "$WORK/asset.txt"
"$ALPACK_BIN" create "$WORK/Floating.AL" FloatingPoint 1.0.0 "$WORK/floating-point.room" "$WORK/asset.txt"
PACKAGE_RESULT="$("$ALPACK_BIN" verify "$WORK/Floating.AL")"
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"ok":true'
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"moduleCount":1'
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"assetCount":1'


"$ALVM_BIN" compile "$ROOT/examples/scientific_calculator.al" -o "$WORK/scientific-calculator.room"
SCIENTIFIC_INITIAL="$("$RUST_RUNNER" run "$WORK/scientific-calculator.room")"
printf '%s\n' "$SCIENTIFIC_INITIAL" | grep -q '"ok":true'
printf '%s\n' "$SCIENTIFIC_INITIAL" | grep -q '"title":"AL Scientific Calculator"'
SCIENTIFIC_DECIMAL="$("$RUST_RUNNER" run "$WORK/scientific-calculator.room" \
  --event onKey 1 --event onKey 2 --event onKey decimal --event onKey 5 \
  --event onKey plus --event onKey 7 --event onKey decimal --event onKey 2 --event onKey 5 --event onKey equals)"
printf '%s\n' "$SCIENTIFIC_DECIMAL" | grep -q '"ok":true'
printf '%s\n' "$SCIENTIFIC_DECIMAL" | grep -q '"id":"display","kind":"text","text":"19.75"'
SCIENTIFIC_POWER="$("$RUST_RUNNER" run "$WORK/scientific-calculator.room" --event onKey 2 --event onKey power --event onKey 3 --event onKey equals)"
printf '%s\n' "$SCIENTIFIC_POWER" | grep -q '"id":"display","kind":"text","text":"8"'
SCIENTIFIC_SQRT="$("$RUST_RUNNER" run "$WORK/scientific-calculator.room" --event onKey 9 --event onKey sqrt)"
printf '%s\n' "$SCIENTIFIC_SQRT" | grep -q '"id":"display","kind":"text","text":"3"'
SCIENTIFIC_FACTORIAL="$("$RUST_RUNNER" run "$WORK/scientific-calculator.room" --event onKey 5 --event onKey factorial)"
printf '%s\n' "$SCIENTIFIC_FACTORIAL" | grep -q '"id":"display","kind":"text","text":"120"'
SCIENTIFIC_MEMORY="$("$RUST_RUNNER" run "$WORK/scientific-calculator.room" \
  --event onKey 1 --event onKey 2 --event onKey decimal --event onKey 5 \
  --event onKey mPlus --event onKey clearAll --event onKey mr)"
printf '%s\n' "$SCIENTIFIC_MEMORY" | grep -q '"id":"display","kind":"text","text":"12.5"'
printf '%s\n' "$SCIENTIFIC_MEMORY" | grep -q '"id":"memory","kind":"text","text":"M: 12.5"'


"$ALVM_BIN" compile "$ROOT/examples/calculator_full.al" -o "$WORK/calculator-full.room"
CALCULATOR_FULL_ADD="$("$RUST_RUNNER" run "$WORK/calculator-full.room" --event onKey 8 --event onKey multiply --event onKey 7 --event onKey equals)"
printf '%s\\n' "$CALCULATOR_FULL_ADD" | grep -q '"ok":true'
printf '%s\\n' "$CALCULATOR_FULL_ADD" | grep -q '"text":"56"'
CALCULATOR_FULL_FACTORIAL="$("$RUST_RUNNER" run "$WORK/calculator-full.room" --event onKey 5 --event onKey factorial)"
printf '%s\\n' "$CALCULATOR_FULL_FACTORIAL" | grep -q '"text":"120"'
CALCULATOR_FULL_REPEAT="$("$RUST_RUNNER" run "$WORK/calculator-full.room" --event onKey 7 --event onKey plus --event onKey 5 --event onKey equals --event onKey equals)"
printf '%s\\n' "$CALCULATOR_FULL_REPEAT" | grep -q '"text":"17"'

echo "Rust CI Runtime game-loop/touch/collision, full calculator and .AL package end-to-end tests passed."
