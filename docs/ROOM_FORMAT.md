# ALVM .room binary format

## Current version

- File extension: `.room`
- Magic bytes: ASCII `ALRM`
- Binary format version: **9**
- Integer encoding: little-endian
- Text encoding: UTF-8
- The file contains instructions, not AL source text.

The C++ compiler in `src/alvm.cpp` writes this format. The Android CI Runtime is implemented in Rust and reads, verifies and executes the same format. A version mismatch is a hard error; the runtime must not guess how to read another version.

## File layout

All counts and identifiers below are unsigned 32-bit integers unless marked signed.

| Order | Field | Encoding |
|---|---|---|
| 1 | Magic | 4 bytes: `ALRM` |
| 2 | Format version | u32, currently 9 |
| 3 | Class count | u32 |
| 4 | Each class | Name string, base class ID (i32), field count (u32), field type IDs (u32 array) |
| 5 | Function count | u32 |
| 6 | Each function | Name string, owner class ID (i32), return type (u32), parameter count (u32), local count (u32), instruction count (u32) |
| 7 | Each instruction | Opcode (u8), argument (i64), text string |
| 8 | End of file | No trailing bytes are allowed |

A string is encoded as a u32 UTF-8 byte length followed by that many bytes. A signed i64 instruction argument is stored in little-endian two's-complement form. Class and function owner IDs use signed i32; -1 means no owner/base class.

## Type IDs

| ID | Type |
|---:|---|
| 0 | int |
| 1 | string |
| 2 | boolean |
| 3 | int[] |
| 4 | string[] |
| 5 | boolean[] |
| 6 | void |
| 7 | unknown/internal |
| 100+ | User-defined class ID, encoded as 100 + class index |

Class fields are currently limited to scalar int, string and boolean fields.

## Opcode IDs

| ID | Instruction |
|---|---|
| 1 | PushInt |
| 2 | PushString |
| 3 | PushBool |
| 4 | Load |
| 5 | Store |
| 6-10 | Add, Sub, Mul, Div, Mod |
| 11-12 | Neg, Not |
| 13-20 | Eq, Ne, Lt, Gt, Le, Ge, And, Or |
| 21-22 | Jump, JumpFalse |
| 23-25 | Print, Pop, Halt |
| 26-29 | NewArray, ArrayGet, ArraySet, ArrayLength |
| 30-31 | Call, Ret |
| 32-35 | NewObject, GetField, SetField, Dup |
| 36 | NewArraySized |
| 37 | UiCall |

The UiCall opcode is part of format v9. Version 8 files do not contain this opcode and must be recompiled from AL source.

## Runtime verification

A runtime must reject malformed headers, unsupported versions, invalid type IDs, excessive counts, truncated strings/instructions, invalid function/class references, invalid jumps, inconsistent operand-stack depths, invalid local slots and execution paths which fall off the end of a function.

The Rust CI Runtime performs these checks before executing the instruction stream. It does not parse or execute AL source text.
