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

"$ALVM_BIN" compile "$ROOT/examples/window_gate.al" -o "$WORK/window-gate.room"
WINDOW_OPEN="$("$RUST_RUNNER" run "$WORK/window-gate.room")"
printf '%s\n' "$WINDOW_OPEN" | grep -q '"ok":true'
printf '%s\n' "$WINDOW_OPEN" | grep -q '"visible":true'
printf '%s\n' "$WINDOW_OPEN" | grep -q '"title":"ALWindowGate"'
printf '%s\n' "$WINDOW_OPEN" | grep -q '"id":"status"'

"$ALVM_BIN" compile "$ROOT/tests/window_without_change.al" -o "$WORK/window-hidden.room"
WINDOW_HIDDEN="$("$RUST_RUNNER" run "$WORK/window-hidden.room")"
printf '%s\n' "$WINDOW_HIDDEN" | grep -q '"ok":true'
printf '%s\n' "$WINDOW_HIDDEN" | grep -q '"visible":false'
printf '%s\n' "$WINDOW_HIDDEN" | grep -q '"id":"hidden-status"'

"$ALVM_BIN" compile "$ROOT/tests/window_conditional_not_reached.al" -o "$WORK/window-conditional.room"
WINDOW_CONDITIONAL="$("$RUST_RUNNER" run "$WORK/window-conditional.room")"
printf '%s\n' "$WINDOW_CONDITIONAL" | grep -q '"ok":true'
printf '%s\n' "$WINDOW_CONDITIONAL" | grep -q '"visible":false'
printf '%s\n' "$WINDOW_CONDITIONAL" | grep -q '"id":"conditional-status"'

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

"$ALVM_BIN" compile "$ROOT/examples/tap_the_target.al" -o "$WORK/tap-the-target.room"
TAP_INITIAL="$("$RUST_RUNNER" run "$WORK/tap-the-target.room")"
printf '%s\n' "$TAP_INITIAL" | grep -q '"ok":true'
printf '%s\n' "$TAP_INITIAL" | grep -q '"title":"TAP THE TARGET"'
TAP_WIN="$("$RUST_RUNNER" run "$WORK/tap-the-target.room" \
  --event onTap target --event onTap target --event onTap target --event onTap target --event onTap target \
  --event onTap target --event onTap target --event onTap target --event onTap target --event onTap target)"
printf '%s\n' "$TAP_WIN" | grep -q '"ok":true'
printf '%s\n' "$TAP_WIN" | grep -q '"id":"score","kind":"text","text":"10"'
printf '%s\n' "$TAP_WIN" | grep -q '"id":"status","kind":"text","text":"YOU WIN!"'
TAP_RESET="$("$RUST_RUNNER" run "$WORK/tap-the-target.room" --event onTap target --event restartGame reset)"
printf '%s\n' "$TAP_RESET" | grep -q '"id":"score","kind":"text","text":"0"'

"$ALVM_BIN" compile "$ROOT/examples/meteor_rush.al" -o "$WORK/meteor-rush.room"
METEOR_INITIAL="$("$RUST_RUNNER" run "$WORK/meteor-rush.room")"
printf '%s\n' "$METEOR_INITIAL" | grep -q '"ok":true'
printf '%s\n' "$METEOR_INITIAL" | grep -q '"title":"METEOR RUSH"'
printf '%s\n' "$METEOR_INITIAL" | grep -q '"frameRate":20'
METEOR_TOUCH="$("$RUST_RUNNER" run "$WORK/meteor-rush.room" --event onTouch '@touch|playZone|250|200' --event update frame)"
printf '%s\n' "$METEOR_TOUCH" | grep -q '"ok":true'
printf '%s\n' "$METEOR_TOUCH" | grep -q '"id":"ship"'
printf '%s\n' "$METEOR_TOUCH" | grep -q '"x":250'

printf 'packaged asset\n' > "$WORK/asset.txt"
"$ALPACK_BIN" create "$WORK/Floating.AL" FloatingPoint 1.0.0 "$WORK/floating-point.room" "$WORK/asset.txt"
PACKAGE_RESULT="$("$ALPACK_BIN" verify "$WORK/Floating.AL")"
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"ok":true'
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"moduleCount":1'
printf '%s\n' "$PACKAGE_RESULT" | grep -q '"assetCount":1'

"$ALVM_BIN" compile "$ROOT/examples/canvas_text.al" -o "$WORK/canvas-text.room"
CANVAS_TEXT="$("$RUST_RUNNER" run "$WORK/canvas-text.room")"
python3 - "$CANVAS_TEXT" <<'PY'
import json
import sys

result = json.loads(sys.argv[1])
assert result.get("ok") is True, result
items = result.get("ui", {}).get("elements", [])
label = next((item for item in items if item.get("id") == "title"), None)
assert label is not None, result
assert label.get("kind") == "drawText", label
assert label.get("text") == "ALVM Canvas Text", label
assert (label.get("x"), label.get("y"), label.get("w"), label.get("color")) == (
    16, 42, 22, 4294967295
), label
PY

"$ALVM_BIN" compile "$ROOT/examples/sprite_physics.al" -o "$WORK/sprite-physics.room"
SPRITE_PHYSICS="$("$RUST_RUNNER" run "$WORK/sprite-physics.room")"
python3 - "$SPRITE_PHYSICS" <<'PY'
import json
import sys

result = json.loads(sys.argv[1])
assert result.get("ok") is True, result
items = result.get("ui", {}).get("elements", [])
hero = next((item for item in items if item.get("id") == "hero"), None)
assert hero is not None, result
assert hero.get("kind") == "sprite", hero
assert hero.get("text") == "sprites/hero.png", hero
assert (hero.get("x"), hero.get("y"), hero.get("w"), hero.get("h")) == (10, 21, 32, 32), hero
PY

"$ALVM_BIN" compile "$ROOT/examples/multi_module_link.al" -o "$WORK/multi-module.room"
MODULE_LINK="$("$RUST_RUNNER" run "$WORK/multi-module.room")"
printf '%s\n' "$MODULE_LINK" | grep -q '"ok":true'
printf '%s\n' "$MODULE_LINK" | grep -q '"output":"42"'

"$ALVM_BIN" compile "$ROOT/examples/game_engine_foundation.al" -o "$WORK/game-engine.room"
ENGINE_INITIAL="$("$RUST_RUNNER" run "$WORK/game-engine.room")"
ENGINE_CPP_OUTPUT="$("$ALVM_BIN" run "$WORK/game-engine.room")"
ENGINE_CPP_UI="$("$ALVM_BIN" ui "$WORK/game-engine.room")"
python3 - "$ENGINE_INITIAL" "$ENGINE_CPP_OUTPUT" "$ENGINE_CPP_UI" <<'PY'
import json
import sys

result = json.loads(sys.argv[1])
cpp_output = sys.argv[2]
cpp_ui = json.loads(sys.argv[3])
assert cpp_output == result.get("output"), {
    "cpp_output": cpp_output,
    "rust_output": result.get("output"),
}
assert cpp_ui == result.get("ui"), {
    "cpp_ui": cpp_ui,
    "rust_ui": result.get("ui"),
}
assert result.get("ok") is True, result
ui = result.get("ui", {})
items = ui.get("elements", [])
by_id = {item.get("id"): item for item in items}
assert by_id["world"]["kind"] == "node", by_id.get("world")
assert by_id["hero"]["parent"] == "world", by_id.get("hero")
assert (by_id["hero"]["columns"], by_id["hero"]["rows"], by_id["hero"]["frameCount"]) == (4, 2, 8), by_id["hero"]
assert by_id["hero"]["animationFps"] == 12, by_id["hero"]
assert by_id["cubeA"]["kind"] == "cube3D", by_id.get("cubeA")
assert by_id["cubeA"]["rotationY"] == 25.0, by_id["cubeA"]
assert by_id["cubeA"]["scale3D"] == 1.25, by_id["cubeA"]
assert ui["camera3D"]["focal"] == 240, ui
assert ui["frameRate"] == 30, ui
assert result.get("output") == "true", result
PY
ENGINE_KEY="$("$RUST_RUNNER" run "$WORK/game-engine.room" --event onKey LEFT)"
python3 - "$ENGINE_KEY" <<'PY'
import json
import sys
result = json.loads(sys.argv[1])
assert result.get("ok") is True, result
items = result.get("ui", {}).get("elements", [])
label = next((item for item in items if item.get("id") == "keyLabel"), None)
assert label and label.get("text") == "LEFT", result
PY

echo "Rust CI Runtime multi-module compiler link, scene graph, sprite-sheet animation, rigid-body collisions, keyboard callback and 3D foundation tests passed."
