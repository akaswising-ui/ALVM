"""CI Runtime: Python implementation of the ALRM .room v9 bytecode VM.

The compiler remains native C++. This module executes verified bytecode only.
No AL source parser or source-code evaluation is present in this runtime.
"""
from __future__ import annotations

import json
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Any

RUNTIME_API_VERSION = 1
RUNTIME_VERSION = "1.0.0"
ROOM_FORMAT_VERSION = 9

# Values must stay synchronized with enum class Type in src/alvm.cpp.
TYPE_INT = 0
TYPE_STRING = 1
TYPE_BOOL = 2
TYPE_INT_ARRAY = 3
TYPE_STRING_ARRAY = 4
TYPE_BOOL_ARRAY = 5
TYPE_VOID = 6
TYPE_UNKNOWN = 7

# Numeric opcode values must stay synchronized with enum class Op in src/alvm.cpp.
PUSH_INT = 1
PUSH_STRING = 2
PUSH_BOOL = 3
LOAD = 4
STORE = 5
ADD = 6
SUB = 7
MUL = 8
DIV = 9
MOD = 10
NEG = 11
NOT = 12
EQ = 13
NE = 14
LT = 15
GT = 16
LE = 17
GE = 18
AND = 19
OR = 20
JUMP = 21
JUMP_FALSE = 22
PRINT = 23
POP = 24
HALT = 25
NEW_ARRAY = 26
ARRAY_GET = 27
ARRAY_SET = 28
ARRAY_LENGTH = 29
CALL = 30
RET = 31
NEW_OBJECT = 32
GET_FIELD = 33
SET_FIELD = 34
DUP = 35
NEW_ARRAY_SIZED = 36
UI_CALL = 37

I64_MIN = -(1 << 63)
I64_MAX = (1 << 63) - 1
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_CLASSES = 10_000
MAX_FUNCTIONS = 10_000
MAX_LOCALS = 100_000
MAX_INSTRUCTIONS = 1_000_000
MAX_STRING_BYTES = 1_000_000
MAX_ARRAY_ITEMS = 100_000
MAX_CALL_DEPTH = 1024


class RuntimeErrorAL(Exception):
    """A deterministic AL bytecode/runtime error."""


class _Reader:
    def __init__(self, data: bytes):
        if len(data) > MAX_FILE_BYTES:
            raise RuntimeErrorAL(".room file exceeds the 64 MiB safety limit")
        self.data = data
        self.offset = 0

    def take(self, count: int) -> bytes:
        if count < 0 or self.offset + count > len(self.data):
            raise RuntimeErrorAL("Truncated .room file")
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def u8(self) -> int:
        return self.take(1)[0]

    def u32(self) -> int:
        return struct.unpack("<I", self.take(4))[0]

    def i32(self) -> int:
        return struct.unpack("<i", self.take(4))[0]

    def i64(self) -> int:
        return struct.unpack("<q", self.take(8))[0]

    def string(self) -> str:
        size = self.u32()
        if size > MAX_STRING_BYTES:
            raise RuntimeErrorAL(".room string length limit exceeded")
        try:
            return self.take(size).decode("utf-8")
        except UnicodeDecodeError as exc:
            raise RuntimeErrorAL("Invalid UTF-8 string in .room file") from exc


@dataclass
class Instruction:
    op: int
    arg: int
    text: str


@dataclass
class Function:
    name: str
    owner_class_id: int
    return_type: int
    parameter_count: int
    local_count: int
    code: list[Instruction]


@dataclass
class ClassInfo:
    name: str
    base_class_id: int
    field_types: list[int]


@dataclass
class Program:
    classes: list[ClassInfo]
    functions: list[Function]


@dataclass
class ArrayValue:
    items: list[Any]
    # C++ uses size_t(-1) for an empty, not-yet-inferred array.
    element_kind: int = (1 << 64) - 1


@dataclass
class ObjectValue:
    class_id: int
    fields: list[Any]


@dataclass
class UIElement:
    kind: str
    element_id: str
    text: str = ""
    callback: str = ""
    x: int = 0
    y: int = 0
    w: int = 0
    h: int = 0
    color: int = 0xFFFFFFFF

    def as_json(self) -> dict[str, Any]:
        return {
            "kind": self.kind,
            "id": self.element_id,
            "text": self.text,
            "callback": self.callback,
            "x": self.x,
            "y": self.y,
            "w": self.w,
            "h": self.h,
            "color": self.color,
        }


# UI state deliberately survives calls to event handlers, as it does in ALVM.
_ACTIVE_UI_TITLE = "AL Application"
_ACTIVE_UI: list[UIElement] = []
_ACTIVE_UI_INTS: dict[str, int] = {}


def _read_room_bytes(data: bytes) -> Program:
    reader = _Reader(data)
    if reader.take(4) != b"ALRM":
        raise RuntimeErrorAL("Invalid .room magic")
    version = reader.u32()
    if version != ROOM_FORMAT_VERSION:
        raise RuntimeErrorAL(
            f"Unsupported .room version {version}; CI Runtime supports version {ROOM_FORMAT_VERSION}"
        )

    class_count = reader.u32()
    if class_count > MAX_CLASSES:
        raise RuntimeErrorAL("Class count limit exceeded")
    classes: list[ClassInfo] = []
    for index in range(class_count):
        name = reader.string()
        base_id = reader.i32()
        if base_id < -1 or (base_id >= 0 and base_id >= index):
            raise RuntimeErrorAL("Invalid or forward class inheritance reference")
        field_count = reader.u32()
        if field_count > MAX_LOCALS:
            raise RuntimeErrorAL("Class field count limit exceeded")
        field_types = [reader.u32() for _ in range(field_count)]
        if any(item not in (TYPE_INT, TYPE_STRING, TYPE_BOOL) for item in field_types):
            raise RuntimeErrorAL("Unsupported object field type in .room v9")
        classes.append(ClassInfo(name, base_id, field_types))

    function_count = reader.u32()
    if function_count == 0 or function_count > MAX_FUNCTIONS:
        raise RuntimeErrorAL("Invalid function count")
    functions: list[Function] = []
    for _ in range(function_count):
        name = reader.string()
        owner_class_id = reader.i32()
        return_type = reader.u32()
        if owner_class_id < -1 or (owner_class_id >= 0 and owner_class_id >= class_count):
            raise RuntimeErrorAL("Invalid method owner class")
        if not _valid_type(return_type, class_count):
            raise RuntimeErrorAL("Invalid function return type")
        parameter_count = reader.u32()
        local_count = reader.u32()
        if parameter_count > local_count or local_count > MAX_LOCALS:
            raise RuntimeErrorAL("Invalid function local/parameter count")
        instruction_count = reader.u32()
        if instruction_count == 0 or instruction_count > MAX_INSTRUCTIONS:
            raise RuntimeErrorAL("Invalid instruction count")
        code: list[Instruction] = []
        for _ in range(instruction_count):
            op = reader.u8()
            if op < PUSH_INT or op > UI_CALL:
                raise RuntimeErrorAL("Invalid opcode")
            code.append(Instruction(op, reader.i64(), reader.string()))
        functions.append(Function(
            name, owner_class_id, return_type, parameter_count, local_count, code
        ))

    if reader.offset != len(data):
        raise RuntimeErrorAL("Unexpected trailing bytes in .room file")
    return Program(classes, functions)


def _valid_type(value: int, class_count: int) -> bool:
    return value <= TYPE_UNKNOWN or (value >= 100 and value - 100 < class_count)


def _stack_delta(op: int) -> int:
    if op in (PUSH_INT, PUSH_STRING, PUSH_BOOL, LOAD, NEW_OBJECT):
        return 1
    if op in (STORE, JUMP_FALSE, PRINT, POP):
        return -1
    if op in (
        ADD, SUB, MUL, DIV, MOD, EQ, NE, LT, GT, LE, GE, AND, OR, ARRAY_GET
    ):
        return -1
    if op == ARRAY_SET:
        return -3
    if op == SET_FIELD:
        return -2
    if op == DUP:
        return 1
    return 0


def _verify(program: Program) -> None:
    functions = program.functions
    classes = program.classes
    if not functions or functions[0].name != "main" or functions[0].owner_class_id != -1:
        raise RuntimeErrorAL(".room must start with global main function")
    if functions[0].return_type != TYPE_VOID or functions[0].parameter_count != 0:
        raise RuntimeErrorAL("Invalid main function signature")

    class_names: set[str] = set()
    for index, cls in enumerate(classes):
        if not cls.name or cls.name in class_names:
            raise RuntimeErrorAL("Duplicate or empty class name")
        class_names.add(cls.name)
        if cls.base_class_id < -1 or cls.base_class_id >= index:
            raise RuntimeErrorAL("Invalid class inheritance graph")

    function_names: set[str] = set()
    for fn in functions:
        if not fn.name or fn.name in function_names:
            raise RuntimeErrorAL("Empty or duplicate function name")
        function_names.add(fn.name)
        if fn.owner_class_id < -1 or fn.owner_class_id >= len(classes):
            raise RuntimeErrorAL("Invalid method owner class")
        if fn.owner_class_id >= 0 and fn.local_count < fn.parameter_count + 1:
            raise RuntimeErrorAL("Method does not have room for its this parameter")
        if not _valid_type(fn.return_type, len(classes)):
            raise RuntimeErrorAL("Invalid function return type")
        if not fn.code:
            raise RuntimeErrorAL("Empty function body: " + fn.name)

    for fn in functions:
        depths: dict[int, int] = {0: 0}
        pending = [0]
        while pending:
            pc = pending.pop()
            instruction = fn.code[pc]
            op, arg = instruction.op, instruction.arg
            if op in (LOAD, STORE) and (arg < 0 or arg >= fn.local_count):
                raise RuntimeErrorAL("Invalid local slot")
            if op == NEW_OBJECT and (arg < 0 or arg >= len(classes)):
                raise RuntimeErrorAL("Invalid class allocation target")
            if op in (GET_FIELD, SET_FIELD) and (arg < 0 or arg > 100_000):
                raise RuntimeErrorAL("Invalid field index")
            before = depths[pc]
            need = 0
            delta = _stack_delta(op)
            if op in (STORE, JUMP_FALSE, PRINT, POP, ARRAY_LENGTH, GET_FIELD, DUP):
                need = 1
            elif op in (
                ADD, SUB, MUL, DIV, MOD, EQ, NE, LT, GT, LE, GE, AND, OR,
                ARRAY_GET, SET_FIELD
            ):
                need = 2
            elif op == ARRAY_SET:
                need = 3
            elif op == NEW_ARRAY:
                if arg < 0 or arg > MAX_ARRAY_ITEMS:
                    raise RuntimeErrorAL("Invalid array literal size")
                need = int(arg)
                delta = 1 - int(arg)
            elif op == NEW_ARRAY_SIZED:
                if arg not in (TYPE_INT, TYPE_STRING, TYPE_BOOL):
                    raise RuntimeErrorAL("Invalid sized-array element type")
                need = 1
            elif op == UI_CALL:
                if arg < 0 or arg > 6:
                    raise RuntimeErrorAL("Invalid UI call argument count")
                need = int(arg)
                delta = (1 if instruction.text == "getInt" else 0) - int(arg)
            elif op == CALL:
                if arg <= 0 or arg >= len(functions):
                    raise RuntimeErrorAL("Invalid call target")
                target = functions[arg]
                need = target.parameter_count + (1 if target.owner_class_id >= 0 else 0)
                delta = (0 if target.return_type == TYPE_VOID else 1) - need
            elif op == RET:
                need = 0 if fn.return_type == TYPE_VOID else 1
            if before < need:
                raise RuntimeErrorAL(
                    f"Bytecode stack underflow at {fn.name} instruction {pc}"
                )
            if op == RET:
                if before != need:
                    raise RuntimeErrorAL("Invalid stack at RET in function " + fn.name)
                continue
            if op == HALT:
                if fn.name != "main" or before != 0:
                    raise RuntimeErrorAL("Invalid HALT in bytecode")
                continue

            after = before + delta

            def visit(destination: int) -> None:
                if destination < 0 or destination >= len(fn.code):
                    raise RuntimeErrorAL("Invalid jump target")
                if destination not in depths:
                    depths[destination] = after
                    pending.append(destination)
                elif depths[destination] != after:
                    raise RuntimeErrorAL("Inconsistent stack depth at control-flow join")

            if op == JUMP:
                visit(arg)
            elif op == JUMP_FALSE:
                visit(arg)
                if pc + 1 < len(fn.code):
                    visit(pc + 1)
            else:
                if pc + 1 >= len(fn.code):
                    raise RuntimeErrorAL("Execution can fall off end of function " + fn.name)
                visit(pc + 1)


def _value_kind(value: Any) -> int:
    if type(value) is int:
        return 0
    if type(value) is str:
        return 1
    if type(value) is bool:
        return 2
    if isinstance(value, ArrayValue):
        return 3
    if isinstance(value, ObjectValue):
        return 4
    raise RuntimeErrorAL("Unsupported runtime value")


def _show(value: Any) -> str:
    if type(value) is bool:
        return "true" if value else "false"
    if type(value) in (int, str):
        return str(value)
    if isinstance(value, ArrayValue):
        return f"[array length={len(value.items)}]"
    return "[object]"


def _integer(value: Any) -> int:
    if type(value) is not int:
        raise RuntimeErrorAL("Runtime type error: expected int")
    return value


def _boolean(value: Any) -> bool:
    if type(value) is not bool:
        raise RuntimeErrorAL("Runtime type error: expected boolean")
    return value


def _checked(value: int) -> int:
    if value < I64_MIN or value > I64_MAX:
        raise RuntimeErrorAL("Integer overflow")
    return value


def _same(a: Any, b: Any) -> bool:
    if _value_kind(a) != _value_kind(b):
        return False
    if isinstance(a, (ArrayValue, ObjectValue)):
        return a is b
    return a == b


def _is_instance_of(actual: int, expected: int, classes: list[ClassInfo]) -> bool:
    while 0 <= actual < len(classes):
        if actual == expected:
            return True
        parent = classes[actual].base_class_id
        if parent < 0:
            return False
        actual = parent
    return False


def _pop(stack: list[Any]) -> Any:
    if not stack:
        raise RuntimeErrorAL("Runtime stack underflow")
    return stack.pop()


def _as_string(args: list[Any], index: int) -> str:
    if index >= len(args) or type(args[index]) is not str:
        raise RuntimeErrorAL("UI API expected string argument")
    return args[index]


def _as_int(args: list[Any], index: int) -> int:
    if index >= len(args):
        raise RuntimeErrorAL("UI API missing integer argument")
    return _integer(args[index])


def _find_ui_element(element_id: str) -> UIElement | None:
    for element in _ACTIVE_UI:
        if element.element_id == element_id:
            return element
    return None


def _ui_call(instruction: Instruction, args: list[Any], stack: list[Any]) -> None:
    global _ACTIVE_UI_TITLE
    name = instruction.text
    if name in ("screen", "game"):
        _ACTIVE_UI_TITLE = _as_string(args, 0)
        if name == "game":
            _ACTIVE_UI.clear()
    elif name == "text":
        element_id, value = _as_string(args, 0), _as_string(args, 1)
        element = _find_ui_element(element_id)
        if element:
            element.kind, element.text = "text", value
        else:
            _ACTIVE_UI.append(UIElement("text", element_id, value))
    elif name == "button":
        element_id = _as_string(args, 0)
        label = _as_string(args, 1)
        callback = _as_string(args, 2)
        element = _find_ui_element(element_id)
        if element:
            element.kind, element.text, element.callback = "button", label, callback
        else:
            _ACTIVE_UI.append(UIElement("button", element_id, label, callback))
    elif name in ("rect", "circle", "line"):
        element_id = _as_string(args, 0)
        shape = UIElement(name, element_id)
        if name == "rect":
            shape.x, shape.y, shape.w, shape.h, shape.color = (
                _as_int(args, 1), _as_int(args, 2), _as_int(args, 3),
                _as_int(args, 4), _as_int(args, 5)
            )
            if shape.w < 0 or shape.h < 0:
                raise RuntimeErrorAL("UI.rect width and height cannot be negative")
        elif name == "circle":
            shape.x, shape.y, shape.w, shape.color = (
                _as_int(args, 1), _as_int(args, 2), _as_int(args, 3), _as_int(args, 4)
            )
            if shape.w < 0:
                raise RuntimeErrorAL("UI.circle radius cannot be negative")
        else:
            shape.x, shape.y, shape.w, shape.h, shape.color = (
                _as_int(args, 1), _as_int(args, 2), _as_int(args, 3),
                _as_int(args, 4), _as_int(args, 5)
            )
        old = _find_ui_element(element_id)
        if old:
            _ACTIVE_UI[_ACTIVE_UI.index(old)] = shape
        else:
            _ACTIVE_UI.append(shape)
    elif name == "remove":
        element_id = _as_string(args, 0)
        _ACTIVE_UI[:] = [item for item in _ACTIVE_UI if item.element_id != element_id]
    elif name == "setText":
        element_id = _as_string(args, 0)
        value = _show(args[1]) if len(args) > 1 else ""
        element = _find_ui_element(element_id)
        if element is None:
            raise RuntimeErrorAL("UI.setText unknown element: " + element_id)
        element.text = value
    elif name == "setInt":
        _ACTIVE_UI_INTS[_as_string(args, 0)] = _as_int(args, 1)
    elif name == "getInt":
        key = _as_string(args, 0)
        default = _as_int(args, 1)
        stack.append(_ACTIVE_UI_INTS.get(key, default))
    elif name == "clear":
        _ACTIVE_UI.clear()
        _ACTIVE_UI_INTS.clear()
    else:
        raise RuntimeErrorAL("Unknown UI opcode: " + name)


@dataclass
class _Frame:
    function_id: int
    pc: int
    locals: list[Any]
    stack_base: int


_UNINITIALIZED = object()


def _execute(program: Program, entry_name: str = "main", event_value: str = "") -> str:
    global _ACTIVE_UI_TITLE
    _verify(program)
    functions, classes = program.functions, program.classes
    stack: list[Any] = []

    if entry_name == "main":
        _ACTIVE_UI_TITLE = "AL Application"
        _ACTIVE_UI.clear()
        _ACTIVE_UI_INTS.clear()
        entry_index = 0
    else:
        matches = [
            index for index, function in enumerate(functions[1:], start=1)
            if (function.name == entry_name or function.name.startswith(entry_name + "$"))
            and function.owner_class_id < 0
            and function.parameter_count == 1
            and function.return_type == TYPE_VOID
        ]
        if len(matches) > 1:
            raise RuntimeErrorAL("Ambiguous UI event handler: " + entry_name)
        if not matches:
            raise RuntimeErrorAL("Unknown UI event handler: " + entry_name)
        entry_index = matches[0]

    entry_function = functions[entry_index]
    entry_locals = [_UNINITIALIZED] * entry_function.local_count
    if entry_name != "main":
        if not entry_locals:
            raise RuntimeErrorAL("UI event handler has no parameter")
        entry_locals[0] = event_value
    frames = [_Frame(entry_index, 0, entry_locals, 0)]

    while frames:
        frame = frames[-1]
        fn = functions[frame.function_id]
        if frame.pc >= len(fn.code):
            raise RuntimeErrorAL("Instruction pointer outside function")
        instruction_pc = frame.pc
        instruction = fn.code[frame.pc]
        frame.pc += 1
        op, arg = instruction.op, instruction.arg

        try:
            if op == DUP:
                if not stack:
                    raise RuntimeErrorAL("DUP on empty stack")
                stack.append(stack[-1])
            elif op == PUSH_INT:
                stack.append(_checked(arg))
            elif op == PUSH_STRING:
                stack.append(instruction.text)
            elif op == PUSH_BOOL:
                stack.append(arg != 0)
            elif op == LOAD:
                if arg < 0 or arg >= len(frame.locals) or frame.locals[arg] is _UNINITIALIZED:
                    raise RuntimeErrorAL("Read of uninitialized local variable")
                stack.append(frame.locals[arg])
            elif op == STORE:
                if arg < 0 or arg >= len(frame.locals):
                    raise RuntimeErrorAL("Invalid local slot")
                frame.locals[arg] = _pop(stack)
            elif op == PRINT:
                # Output is collected by the outer runner through a special list.
                _OUTPUT_BUFFER.append(_show(_pop(stack)))
            elif op == POP:
                _pop(stack)
            elif op == UI_CALL:
                if arg < 0 or arg > 6 or len(stack) < arg:
                    raise RuntimeErrorAL("Invalid UI call argument count")
                call_args = stack[-arg:] if arg else []
                if arg:
                    del stack[-arg:]
                _ui_call(instruction, call_args, stack)
            elif op == NEG:
                value = _integer(_pop(stack))
                if value == I64_MIN:
                    raise RuntimeErrorAL("Integer overflow")
                stack.append(-value)
            elif op == NOT:
                stack.append(not _boolean(_pop(stack)))
            elif op == ADD:
                right, left = _pop(stack), _pop(stack)
                if type(left) is str and type(right) is str:
                    stack.append(left + right)
                else:
                    stack.append(_checked(_integer(left) + _integer(right)))
            elif op == SUB:
                right, left = _integer(_pop(stack)), _integer(_pop(stack))
                stack.append(_checked(left - right))
            elif op == MUL:
                right, left = _integer(_pop(stack)), _integer(_pop(stack))
                stack.append(_checked(left * right))
            elif op in (DIV, MOD):
                right, left = _integer(_pop(stack)), _integer(_pop(stack))
                if right == 0:
                    raise RuntimeErrorAL("Division by zero")
                if left == I64_MIN and right == -1:
                    raise RuntimeErrorAL("Integer overflow")
                quotient = abs(left) // abs(right)
                if (left < 0) != (right < 0):
                    quotient = -quotient
                stack.append(quotient if op == DIV else left - quotient * right)
            elif op in (EQ, NE):
                right, left = _pop(stack), _pop(stack)
                same = _same(left, right)
                stack.append(same if op == EQ else not same)
            elif op in (LT, GT, LE, GE):
                right, left = _integer(_pop(stack)), _integer(_pop(stack))
                stack.append(
                    left < right if op == LT else
                    left > right if op == GT else
                    left <= right if op == LE else left >= right
                )
            elif op in (AND, OR):
                right, left = _boolean(_pop(stack)), _boolean(_pop(stack))
                stack.append(left and right if op == AND else left or right)
            elif op == NEW_ARRAY:
                if arg < 0 or arg > len(stack) or arg > MAX_ARRAY_ITEMS:
                    raise RuntimeErrorAL("Invalid array construction")
                values = list(stack[-arg:]) if arg else []
                if arg:
                    del stack[-arg:]
                if values:
                    element_kind = _value_kind(values[0])
                    if any(_value_kind(value) != element_kind for value in values):
                        raise RuntimeErrorAL("Array element type mismatch in bytecode")
                else:
                    element_kind = (1 << 64) - 1
                stack.append(ArrayValue(values, element_kind))
            elif op == NEW_ARRAY_SIZED:
                length = _integer(_pop(stack))
                if length < 0 or length > MAX_ARRAY_ITEMS:
                    raise RuntimeErrorAL("Array length must be between 0 and 100000")
                defaults = {
                    TYPE_INT: 0,
                    TYPE_STRING: "",
                    TYPE_BOOL: False,
                }
                if arg not in defaults:
                    raise RuntimeErrorAL("Invalid sized-array element type")
                kind = {TYPE_INT: 0, TYPE_STRING: 1, TYPE_BOOL: 2}[arg]
                stack.append(ArrayValue([defaults[arg] for _ in range(length)], kind))
            elif op == ARRAY_GET:
                index = _integer(_pop(stack))
                base = _pop(stack)
                if not isinstance(base, ArrayValue):
                    raise RuntimeErrorAL("Array access on non-array value")
                if index < 0 or index >= len(base.items):
                    raise RuntimeErrorAL("Array index out of bounds")
                stack.append(base.items[index])
            elif op == ARRAY_SET:
                value = _pop(stack)
                index = _integer(_pop(stack))
                base = _pop(stack)
                if not isinstance(base, ArrayValue):
                    raise RuntimeErrorAL("Array assignment on non-array value")
                if index < 0 or index >= len(base.items):
                    raise RuntimeErrorAL("Array index out of bounds")
                kind = _value_kind(value)
                if base.element_kind == (1 << 64) - 1:
                    base.element_kind = kind
                if kind != base.element_kind:
                    raise RuntimeErrorAL("Array element type mismatch")
                base.items[index] = value
            elif op == ARRAY_LENGTH:
                base = _pop(stack)
                if not isinstance(base, ArrayValue):
                    raise RuntimeErrorAL(".length on non-array value")
                stack.append(len(base.items))
            elif op == NEW_OBJECT:
                if arg < 0 or arg >= len(classes):
                    raise RuntimeErrorAL("Invalid class allocation")
                defaults = {TYPE_INT: 0, TYPE_STRING: "", TYPE_BOOL: False}
                fields = []
                for field_type in classes[arg].field_types:
                    if field_type not in defaults:
                        raise RuntimeErrorAL("Unsupported object field type")
                    fields.append(defaults[field_type])
                stack.append(ObjectValue(arg, fields))
            elif op == GET_FIELD:
                base = _pop(stack)
                if not isinstance(base, ObjectValue):
                    raise RuntimeErrorAL("Field read on non-object value")
                if arg < 0 or arg >= len(base.fields):
                    raise RuntimeErrorAL("Object field index out of bounds")
                stack.append(base.fields[arg])
            elif op == SET_FIELD:
                value, base = _pop(stack), _pop(stack)
                if not isinstance(base, ObjectValue):
                    raise RuntimeErrorAL("Field write on non-object value")
                if arg < 0 or arg >= len(base.fields):
                    raise RuntimeErrorAL("Object field index out of bounds")
                expected = classes[base.class_id].field_types[arg]
                actual = _value_kind(value)
                expected_kind = {TYPE_INT: 0, TYPE_STRING: 1, TYPE_BOOL: 2}.get(expected)
                if actual != expected_kind:
                    raise RuntimeErrorAL("Object field type mismatch")
                base.fields[arg] = value
            elif op == CALL:
                target = arg
                if target <= 0 or target >= len(functions):
                    raise RuntimeErrorAL("Invalid call target")
                callee = functions[target]
                if len(frames) >= MAX_CALL_DEPTH:
                    raise RuntimeErrorAL("Maximum call depth exceeded")
                count = callee.parameter_count + (1 if callee.owner_class_id >= 0 else 0)
                if len(stack) < count:
                    raise RuntimeErrorAL("Call argument stack underflow")
                call_args = list(stack[-count:]) if count else []
                if count:
                    del stack[-count:]
                if callee.owner_class_id >= 0:
                    receiver = call_args[0]
                    if not isinstance(receiver, ObjectValue) or not _is_instance_of(
                        receiver.class_id, callee.owner_class_id, classes
                    ):
                        raise RuntimeErrorAL(
                            "Instance method called with incompatible receiver object"
                        )
                next_frame = _Frame(
                    target, 0, [_UNINITIALIZED] * callee.local_count, len(stack)
                )
                for index, value in enumerate(call_args):
                    next_frame.locals[index] = value
                frames.append(next_frame)
            elif op == RET:
                returns = fn.return_type != TYPE_VOID
                result = _pop(stack) if returns else None
                base = frame.stack_base
                frames.pop()
                if not frames:
                    return "\n".join(_OUTPUT_BUFFER)
                if len(stack) < base:
                    raise RuntimeErrorAL("Call frame stack underflow")
                del stack[base:]
                if returns:
                    stack.append(result)
            elif op == JUMP:
                frame.pc = arg
            elif op == JUMP_FALSE:
                condition = _boolean(_pop(stack))
                if not condition:
                    frame.pc = arg
            elif op == HALT:
                return "\n".join(_OUTPUT_BUFFER)
            else:
                raise RuntimeErrorAL("Unsupported opcode: " + str(op))
        except RuntimeErrorAL as exc:
            raise RuntimeErrorAL(f"{exc} (function {fn.name}, instruction {instruction_pc})") from exc

    return "\n".join(_OUTPUT_BUFFER)


_OUTPUT_BUFFER: list[str] = []


def run_room(room_path: str, entry_name: str = "main", event_value: str = "") -> dict[str, Any]:
    """Execute a verified .room file and return console output plus native-UI JSON."""
    global _OUTPUT_BUFFER
    _OUTPUT_BUFFER = []
    try:
        path = Path(room_path)
        if not path.is_file():
            raise RuntimeErrorAL("Cannot open .room file")
        if path.stat().st_size > MAX_FILE_BYTES:
            raise RuntimeErrorAL(".room file exceeds the 64 MiB safety limit")
        program = _read_room_bytes(path.read_bytes())
        _verify(program)
        output = _execute(program, entry_name, event_value)
        ui = {
            "title": _ACTIVE_UI_TITLE,
            "elements": [item.as_json() for item in _ACTIVE_UI],
        }
        return {"ok": True, "output": output, "ui": ui, "runtimeVersion": RUNTIME_VERSION}
    except Exception as exc:
        return {
            "ok": False,
            "output": "",
            "error": "CI Runtime error: " + str(exc),
            "ui": {
                "title": _ACTIVE_UI_TITLE,
                "elements": [item.as_json() for item in _ACTIVE_UI],
            },
            "runtimeVersion": RUNTIME_VERSION,
        }
    finally:
        _OUTPUT_BUFFER = []


def self_test() -> bool:
    """Small startup health check for runtime packages."""
    return (
        ROOM_FORMAT_VERSION == 9
        and UI_CALL == 37
        and _checked(7) == 7
        and _show(True) == "true"
        and _show(ArrayValue([1, 2], 0)) == "[array length=2]"
    )
