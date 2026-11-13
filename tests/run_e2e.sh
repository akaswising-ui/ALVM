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


"$BIN" compile "$ROOT/examples/array_allocation.al" -o "$TMP/array-allocation.room"
ARRAY_ALLOCATION_EXPECTED="$(printf '5\n10\n0\n50\n\nAL\n0\nfalse\ntrue')"
ARRAY_ALLOCATION_ACTUAL="$("$BIN" run "$TMP/array-allocation.room")"
if [[ "$ARRAY_ALLOCATION_ACTUAL" != "$ARRAY_ALLOCATION_EXPECTED" ]]; then
  printf 'array allocation expected:\n%s\narray allocation actual:\n%s\n' "$ARRAY_ALLOCATION_EXPECTED" "$ARRAY_ALLOCATION_ACTUAL" >&2
  exit 1
fi

"$BIN" compile "$ROOT/tests/array_negative_length.al" -o "$TMP/array-negative-length.room"
if "$BIN" run "$TMP/array-negative-length.room" >/dev/null 2>&1; then
  echo "FAIL: negative array length was accepted" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/overloading.al" -o "$TMP/overloading.room"
OVERLOAD_EXPECTED="$(printf '5\nAL\n10')"
OVERLOAD_ACTUAL="$("$BIN" run "$TMP/overloading.room")"
if [[ "$OVERLOAD_ACTUAL" != "$OVERLOAD_EXPECTED" ]]; then
  printf 'overload expected:\n%s\noverload actual:\n%s\n' "$OVERLOAD_EXPECTED" "$OVERLOAD_ACTUAL" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/functions.al" -o "$TMP/functions.room"
FUNCTION_EXPECTED="$(printf '30\n120\nHello AL\n15')"
test "$("$BIN" run "$TMP/functions.room")" = "$FUNCTION_EXPECTED"

"$BIN" compile "$ROOT/examples/method_overloading.al" -o "$TMP/method-overloading.room"
METHOD_OVERLOAD_EXPECTED="$(printf '9\nAL\n10')"
METHOD_OVERLOAD_ACTUAL="$("$BIN" run "$TMP/method-overloading.room")"
if [[ "$METHOD_OVERLOAD_ACTUAL" != "$METHOD_OVERLOAD_EXPECTED" ]]; then
  printf 'method overload expected:\n%s\nmethod overload actual:\n%s\n' "$METHOD_OVERLOAD_EXPECTED" "$METHOD_OVERLOAD_ACTUAL" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/objects.al" -o "$TMP/objects.room"
OBJECT_EXPECTED="$(printf '75\n90\n95\nALVM\ntrue\n100')"
test "$("$BIN" run "$TMP/objects.room")" = "$OBJECT_EXPECTED"

"$BIN" compile "$ROOT/examples/inheritance.al" -o "$TMP/inheritance.room"
INHERITANCE_EXPECTED="$(printf '7\n1\n7\n7\ntrue\n1')"
test "$("$BIN" run "$TMP/inheritance.room")" = "$INHERITANCE_EXPECTED"

"$BIN" compile "$ROOT/examples/super_constructor.al" -o "$TMP/super-constructor.room"
SUPER_EXPECTED="$(printf '42\nALVM')"
SUPER_ACTUAL="$("$BIN" run "$TMP/super-constructor.room")"
if [[ "$SUPER_ACTUAL" != "$SUPER_EXPECTED" ]]; then
  printf 'super expected:\n%s\nsuper actual:\n%s\n' "$SUPER_EXPECTED" "$SUPER_ACTUAL" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/constructor_overloading.al" -o "$TMP/constructor-overloading.room"
CTOR_OVERLOAD_EXPECTED="$(printf '7\nAL')"
CTOR_OVERLOAD_ACTUAL="$("$BIN" run "$TMP/constructor-overloading.room")"
if [[ "$CTOR_OVERLOAD_ACTUAL" != "$CTOR_OVERLOAD_EXPECTED" ]]; then
  printf 'constructor overload expected:\n%s\nconstructor overload actual:\n%s\n' "$CTOR_OVERLOAD_EXPECTED" "$CTOR_OVERLOAD_ACTUAL" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/constructors.al" -o "$TMP/constructors.room"
CONSTRUCTOR_EXPECTED="$(printf '100\nALVM\n120\n8\n1')"
test "$("$BIN" run "$TMP/constructors.room")" = "$CONSTRUCTOR_EXPECTED"

"$BIN" compile "$ROOT/examples/foreach.al" -o "$TMP/foreach.room"
FOREACH_EXPECTED="$(printf '8\nAL\nVM')"
FOREACH_ACTUAL="$("$BIN" run "$TMP/foreach.room")"
if [[ "$FOREACH_ACTUAL" != "$FOREACH_EXPECTED" ]]; then
  printf 'foreach expected:\n%s\nforeach actual:\n%s\n' "$FOREACH_EXPECTED" "$FOREACH_ACTUAL" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/operators.al" -o "$TMP/operators.room"
OPERATORS_EXPECTED="$(printf '6\\n6\\nALVM')"
OPERATORS_ACTUAL="$("$BIN" run "$TMP/operators.room")"
if [[ "$OPERATORS_ACTUAL" != "$OPERATORS_EXPECTED" ]]; then
  printf 'operator assignment expected:\\n%s\\noperator assignment actual:\\n%s\\n' "$OPERATORS_EXPECTED" "$OPERATORS_ACTUAL" >&2
  exit 1
fi

if "$BIN" compile "$ROOT/tests/compound_assignment_array.al" -o "$TMP/compound-array.room" 2>/dev/null; then
  echo "FAIL: compound array assignment was accepted without implemented lowering" >&2
  exit 1
fi
test ! -e "$TMP/compound-array.room"

"$BIN" compile "$ROOT/examples/control_flow_complete.al" -o "$TMP/control-complete.room"
CONTROL_COMPLETE_EXPECTED="$(printf '10\n12\n4\n20\n2')"
CONTROL_COMPLETE_ACTUAL="$("$BIN" run "$TMP/control-complete.room")"
if [[ "$CONTROL_COMPLETE_ACTUAL" != "$CONTROL_COMPLETE_EXPECTED" ]]; then
  printf 'control-flow expected:\n%s\ncontrol-flow actual:\n%s\n' "$CONTROL_COMPLETE_EXPECTED" "$CONTROL_COMPLETE_ACTUAL" >&2
  exit 1
fi

for fixture in array_allocation_type_error type_error array_type_error function_type_error missing_return object_field_type_error object_method_type_error incompatible_downcast unknown_base_class constructor_wrong_count constructor_wrong_type base_constructor_args duplicate_constructor break_outside continue_outside switch_type_error duplicate_default foreach_type_error foreach_non_array overload_no_match overload_duplicate_signature method_overload_no_match method_overload_duplicate constructor_overload_no_match constructor_overload_duplicate super_wrong_type super_not_first super_without_base; do
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
"$BIN" disasm "$TMP/inheritance.room" | grep -q 'class Dog'
"$BIN" disasm "$TMP/inheritance.room" | grep -q 'function Animal.getAge'
"$BIN" disasm "$TMP/constructors.room" | grep -q 'function Player.$ctor'
"$BIN" compile "$ROOT/examples/calculator.al" -o "$TMP/calculator.room"
UI_INITIAL="$("$BIN" ui "$TMP/calculator.room")"
if [[ "$UI_INITIAL" != *'"title":"AL Calculator"'* || "$UI_INITIAL" != *'"id":"display","text":"0"'* ]]; then
  printf 'AL UI initial render failed:\n%s\n' "$UI_INITIAL" >&2
  exit 1
fi
UI_EVENTS="$("$BIN" ui-event "$TMP/calculator.room" onKey 7 onKey plus onKey 5 onKey equals)"
if [[ "$UI_EVENTS" != *'"id":"display","text":"12"'* ]]; then
  printf 'AL UI button callback sequence failed:\n%s\n' "$UI_EVENTS" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/quiz_battle.al" -o "$TMP/quiz-battle.room"
QUIZ_INITIAL="$("$BIN" ui "$TMP/quiz-battle.room")"
if [[ "$QUIZ_INITIAL" != *'"title":"AL Quiz Battle"'* || "$QUIZ_INITIAL" != *'"id":"question"'* ]]; then
  printf 'AL Quiz Battle initial UI failed:\\n%s\\n' "$QUIZ_INITIAL" >&2
  exit 1
fi
QUIZ_EVENTS="$("$BIN" ui-event "$TMP/quiz-battle.room" onTap b onTap next onTap c onTap next onTap a onTap next)"
if [[ "$QUIZ_EVENTS" != *'"id":"score","text":"30"'* ]]; then
  printf 'AL Quiz Battle answer events failed:\\n%s\\n' "$QUIZ_EVENTS" >&2
  exit 1
fi

"$BIN" compile "$ROOT/examples/2d_shapes.al" -o "$TMP/2d-shapes.room"
GAME_UI="$("$BIN" ui "$TMP/2d-shapes.room")"
if [[ "$GAME_UI" != *'"title":"AL 2D Engine Demo"'* || "$GAME_UI" != *'"kind":"rect"'* || "$GAME_UI" != *'"kind":"circle"'* || "$GAME_UI" != *'"kind":"line"'* || "$GAME_UI" != *'"x":80'* ]]; then
  printf 'AL 2D graphics API failed:\\n%s\\n' "$GAME_UI" >&2
  exit 1
fi

echo "PASS: ALVM compiler, functions, arrays, objects, inheritance, constructors, function, method and constructor overloading, explicit super calls, foreach, switch and loop control"
