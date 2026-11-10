//! CI Runtime: Rust implementation of the ALRM .room v9 virtual machine.
//!
//! AL source is compiled by the C++ compiler. This crate reads, validates and
//! executes verified bytecode only. It does not parse or evaluate AL source.

use libloading::{Library, Symbol};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value as JsonValue};
use sha2::{Digest, Sha256};
use std::cell::RefCell;
use std::collections::{HashMap, HashSet};
use std::ffi::{CStr, CString};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::os::raw::c_char;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::{Mutex, MutexGuard, OnceLock};
use zip::ZipArchive;

pub const RUNTIME_API_VERSION: u32 = 1;
pub const ROOM_FORMAT_VERSION: u32 = 9;
pub const BUNDLED_VERSION: &str = "2.0.0";

const TYPE_INT: u32 = 0;
const TYPE_STRING: u32 = 1;
const TYPE_BOOL: u32 = 2;
const TYPE_INT_ARRAY: u32 = 3;
const TYPE_STRING_ARRAY: u32 = 4;
const TYPE_BOOL_ARRAY: u32 = 5;
const TYPE_VOID: u32 = 6;
const TYPE_UNKNOWN: u32 = 7;

const PUSH_INT: u8 = 1;
const PUSH_STRING: u8 = 2;
const PUSH_BOOL: u8 = 3;
const LOAD: u8 = 4;
const STORE: u8 = 5;
const ADD: u8 = 6;
const SUB: u8 = 7;
const MUL: u8 = 8;
const DIV: u8 = 9;
const MOD: u8 = 10;
const NEG: u8 = 11;
const NOT: u8 = 12;
const EQ: u8 = 13;
const NE: u8 = 14;
const LT: u8 = 15;
const GT: u8 = 16;
const LE: u8 = 17;
const GE: u8 = 18;
const AND: u8 = 19;
const OR: u8 = 20;
const JUMP: u8 = 21;
const JUMP_FALSE: u8 = 22;
const PRINT: u8 = 23;
const POP: u8 = 24;
const HALT: u8 = 25;
const NEW_ARRAY: u8 = 26;
const ARRAY_GET: u8 = 27;
const ARRAY_SET: u8 = 28;
const ARRAY_LENGTH: u8 = 29;
const CALL: u8 = 30;
const RET: u8 = 31;
const NEW_OBJECT: u8 = 32;
const GET_FIELD: u8 = 33;
const SET_FIELD: u8 = 34;
const DUP: u8 = 35;
const NEW_ARRAY_SIZED: u8 = 36;
const UI_CALL: u8 = 37;

const I64_MIN: i64 = i64::MIN;
const I64_MAX: i64 = i64::MAX;
const MAX_FILE_BYTES: usize = 64 * 1024 * 1024;
const MAX_CLASSES: usize = 10_000;
const MAX_FUNCTIONS: usize = 10_000;
const MAX_LOCALS: usize = 100_000;
const MAX_INSTRUCTIONS: usize = 1_000_000;
const MAX_STRING_BYTES: usize = 1_000_000;
const MAX_ARRAY_ITEMS: usize = 100_000;
const MAX_CALL_DEPTH: usize = 1024;
const MAX_PACKAGE_BYTES: u64 = 32 * 1024 * 1024;
const MAX_RUNTIME_LIBRARY_BYTES: u64 = 24 * 1024 * 1024;
const MAX_MANIFEST_BYTES: u64 = 64 * 1024;

#[derive(Debug, Clone)]
pub struct RuntimeError(pub String);

impl std::fmt::Display for RuntimeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for RuntimeError {}

type RtResult<T> = Result<T, RuntimeError>;

fn error<T>(message: impl Into<String>) -> RtResult<T> {
    Err(RuntimeError(message.into()))
}

#[derive(Clone, Debug)]
struct Instruction {
    op: u8,
    arg: i64,
    text: String,
}

#[derive(Clone, Debug)]
struct Function {
    name: String,
    owner_class_id: i32,
    return_type: u32,
    parameter_count: usize,
    local_count: usize,
    code: Vec<Instruction>,
}

#[derive(Clone, Debug)]
struct ClassInfo {
    name: String,
    base_class_id: i32,
    field_types: Vec<u32>,
}

#[derive(Clone, Debug)]
struct Program {
    classes: Vec<ClassInfo>,
    functions: Vec<Function>,
}

struct Reader<'a> {
    data: &'a [u8],
    offset: usize,
}

impl<'a> Reader<'a> {
    fn new(data: &'a [u8]) -> RtResult<Self> {
        if data.len() > MAX_FILE_BYTES {
            return error(".room file exceeds the 64 MiB safety limit");
        }
        Ok(Self { data, offset: 0 })
    }

    fn take(&mut self, count: usize) -> RtResult<&'a [u8]> {
        let end = self.offset.checked_add(count)
            .ok_or_else(|| RuntimeError("Truncated .room file".into()))?;
        if end > self.data.len() {
            return error("Truncated .room file");
        }
        let result = &self.data[self.offset..end];
        self.offset = end;
        Ok(result)
    }

    fn u8(&mut self) -> RtResult<u8> {
        Ok(self.take(1)?[0])
    }

    fn u32(&mut self) -> RtResult<u32> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }

    fn i32(&mut self) -> RtResult<i32> {
        Ok(i32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }

    fn i64(&mut self) -> RtResult<i64> {
        Ok(i64::from_le_bytes(self.take(8)?.try_into().unwrap()))
    }

    fn count(&mut self, maximum: usize, label: &str) -> RtResult<usize> {
        let value = self.u32()? as usize;
        if value > maximum {
            return error(format!("{label} limit exceeded"));
        }
        Ok(value)
    }

    fn string(&mut self) -> RtResult<String> {
        let size = self.count(MAX_STRING_BYTES, ".room string length")?;
        String::from_utf8(self.take(size)?.to_vec())
            .map_err(|_| RuntimeError("Invalid UTF-8 string in .room file".into()))
    }
}

fn read_room_bytes(data: &[u8]) -> RtResult<Program> {
    let mut reader = Reader::new(data)?;
    if reader.take(4)? != b"ALRM" {
        return error("Invalid .room magic");
    }
    let version = reader.u32()?;
    if version != ROOM_FORMAT_VERSION {
        return error(format!(
            "Unsupported .room version {version}; CI Runtime supports version {ROOM_FORMAT_VERSION}"
        ));
    }

    let class_count = reader.count(MAX_CLASSES, "Class count")?;
    let mut classes = Vec::with_capacity(class_count);
    for index in 0..class_count {
        let name = reader.string()?;
        let base_class_id = reader.i32()?;
        if base_class_id < -1 || (base_class_id >= 0 && base_class_id as usize >= index) {
            return error("Invalid or forward class inheritance reference");
        }
        let field_count = reader.count(MAX_LOCALS, "Class field count")?;
        let mut field_types = Vec::with_capacity(field_count);
        for _ in 0..field_count {
            let field_type = reader.u32()?;
            if !matches!(field_type, TYPE_INT | TYPE_STRING | TYPE_BOOL) {
                return error("Unsupported object field type in .room v9");
            }
            field_types.push(field_type);
        }
        classes.push(ClassInfo { name, base_class_id, field_types });
    }

    let function_count = reader.count(MAX_FUNCTIONS, "Function count")?;
    if function_count == 0 {
        return error("Invalid function count");
    }
    let mut functions = Vec::with_capacity(function_count);
    for _ in 0..function_count {
        let name = reader.string()?;
        let owner_class_id = reader.i32()?;
        let return_type = reader.u32()?;
        if owner_class_id < -1 || (owner_class_id >= 0 && owner_class_id as usize >= class_count) {
            return error("Invalid method owner class");
        }
        if !valid_type(return_type, class_count) {
            return error("Invalid function return type");
        }
        let parameter_count = reader.count(MAX_LOCALS, "Function parameter count")?;
        let local_count = reader.count(MAX_LOCALS, "Function local count")?;
        if parameter_count > local_count {
            return error("Invalid function local/parameter count");
        }
        let instruction_count = reader.count(MAX_INSTRUCTIONS, "Instruction count")?;
        if instruction_count == 0 {
            return error("Invalid instruction count");
        }
        let mut code = Vec::with_capacity(instruction_count);
        for _ in 0..instruction_count {
            let op = reader.u8()?;
            if !(PUSH_INT..=UI_CALL).contains(&op) {
                return error("Invalid opcode");
            }
            code.push(Instruction { op, arg: reader.i64()?, text: reader.string()? });
        }
        functions.push(Function {
            name, owner_class_id, return_type, parameter_count, local_count, code,
        });
    }
    if reader.offset != data.len() {
        return error("Unexpected trailing bytes in .room file");
    }
    Ok(Program { classes, functions })
}

fn valid_type(value: u32, class_count: usize) -> bool {
    value <= TYPE_UNKNOWN || (value >= 100 && ((value - 100) as usize) < class_count)
}

fn stack_delta(op: u8) -> i64 {
    match op {
        PUSH_INT | PUSH_STRING | PUSH_BOOL | LOAD | NEW_OBJECT | DUP => 1,
        STORE | JUMP_FALSE | PRINT | POP => -1,
        ADD | SUB | MUL | DIV | MOD | EQ | NE | LT | GT | LE | GE | AND | OR | ARRAY_GET => -1,
        ARRAY_SET => -3,
        SET_FIELD => -2,
        _ => 0,
    }
}

fn verify(program: &Program) -> RtResult<()> {
    let functions = &program.functions;
    let classes = &program.classes;
    if functions.is_empty()
        || functions[0].name != "main"
        || functions[0].owner_class_id != -1
    {
        return error(".room must start with global main function");
    }
    if functions[0].return_type != TYPE_VOID || functions[0].parameter_count != 0 {
        return error("Invalid main function signature");
    }

    let mut class_names = HashSet::new();
    for (index, class) in classes.iter().enumerate() {
        if class.name.is_empty() || !class_names.insert(class.name.as_str()) {
            return error("Duplicate or empty class name");
        }
        if class.base_class_id < -1
            || (class.base_class_id >= 0 && class.base_class_id as usize >= index)
        {
            return error("Invalid class inheritance graph");
        }
    }

    let mut function_names = HashSet::new();
    for function in functions {
        if function.name.is_empty() || !function_names.insert(function.name.as_str()) {
            return error("Empty or duplicate function name");
        }
        if function.owner_class_id < -1
            || (function.owner_class_id >= 0 && function.owner_class_id as usize >= classes.len())
        {
            return error("Invalid method owner class");
        }
        if function.owner_class_id >= 0
            && function.local_count < function.parameter_count + 1
        {
            return error("Method does not have room for its this parameter");
        }
        if !valid_type(function.return_type, classes.len()) {
            return error("Invalid function return type");
        }
        if function.code.is_empty() {
            return error(format!("Empty function body: {}", function.name));
        }
    }

    for function in functions {
        let mut depths: HashMap<usize, i64> = HashMap::new();
        let mut pending = vec![0usize];
        depths.insert(0, 0);
        while let Some(pc) = pending.pop() {
            let instruction = &function.code[pc];
            let op = instruction.op;
            let arg = instruction.arg;
            if matches!(op, LOAD | STORE) && (arg < 0 || arg as usize >= function.local_count) {
                return error("Invalid local slot");
            }
            if op == NEW_OBJECT && (arg < 0 || arg as usize >= classes.len()) {
                return error("Invalid class allocation target");
            }
            if matches!(op, GET_FIELD | SET_FIELD) && (arg < 0 || arg > 100_000) {
                return error("Invalid field index");
            }

            let before = depths[&pc];
            let mut need: i64 = 0;
            let mut delta = stack_delta(op);
            match op {
                STORE | JUMP_FALSE | PRINT | POP | ARRAY_LENGTH | GET_FIELD | DUP => need = 1,
                ADD | SUB | MUL | DIV | MOD | EQ | NE | LT | GT | LE | GE | AND | OR
                | ARRAY_GET | SET_FIELD => need = 2,
                ARRAY_SET => need = 3,
                NEW_ARRAY => {
                    if arg < 0 || arg as usize > MAX_ARRAY_ITEMS {
                        return error("Invalid array literal size");
                    }
                    need = arg;
                    delta = 1 - arg;
                }
                NEW_ARRAY_SIZED => {
                    if !matches!(arg, 0 | 1 | 2) {
                        return error("Invalid sized-array element type");
                    }
                    need = 1;
                }
                UI_CALL => {
                    if !(0..=6).contains(&arg) {
                        return error("Invalid UI call argument count");
                    }
                    need = arg;
                    delta = (if instruction.text == "getInt" { 1 } else { 0 }) - arg;
                }
                CALL => {
                    if arg <= 0 || arg as usize >= functions.len() {
                        return error("Invalid call target");
                    }
                    let target = &functions[arg as usize];
                    need = target.parameter_count as i64
                        + if target.owner_class_id >= 0 { 1 } else { 0 };
                    delta = (if target.return_type == TYPE_VOID { 0 } else { 1 }) - need;
                }
                RET => {
                    need = if function.return_type == TYPE_VOID { 0 } else { 1 };
                }
                _ => {}
            }
            if before < need {
                return error(format!(
                    "Bytecode stack underflow at {} instruction {}",
                    function.name, pc
                ));
            }
            if op == RET {
                if before != need {
                    return error(format!("Invalid stack at RET in function {}", function.name));
                }
                continue;
            }
            if op == HALT {
                if function.name != "main" || before != 0 {
                    return error("Invalid HALT in bytecode");
                }
                continue;
            }
            let after = before + delta;
            let mut visit = |destination: i64| -> RtResult<()> {
                if destination < 0 || destination as usize >= function.code.len() {
                    return error("Invalid jump target");
                }
                let destination = destination as usize;
                match depths.get(&destination) {
                    None => {
                        depths.insert(destination, after);
                        pending.push(destination);
                    }
                    Some(existing) if *existing != after => {
                        return error("Inconsistent stack depth at control-flow join");
                    }
                    _ => {}
                }
                Ok(())
            };
            if op == JUMP {
                visit(arg)?;
            } else if op == JUMP_FALSE {
                visit(arg)?;
                if pc + 1 < function.code.len() {
                    visit((pc + 1) as i64)?;
                }
            } else {
                if pc + 1 >= function.code.len() {
                    return error(format!(
                        "Execution can fall off end of function {}",
                        function.name
                    ));
                }
                visit((pc + 1) as i64)?;
            }
        }
    }
    Ok(())
}

#[derive(Clone)]
enum RuntimeValue {
    Int(i64),
    String(String),
    Bool(bool),
    Array(Rc<RefCell<ArrayValue>>),
    Object(Rc<RefCell<ObjectValue>>),
}

struct ArrayValue {
    items: Vec<RuntimeValue>,
    element_kind: u64,
}

struct ObjectValue {
    class_id: usize,
    fields: Vec<RuntimeValue>,
}

#[derive(Clone, Serialize)]
struct UiElement {
    kind: String,
    #[serde(rename = "id")]
    element_id: String,
    text: String,
    callback: String,
    x: i64,
    y: i64,
    w: i64,
    h: i64,
    color: u32,
}

#[derive(Default)]
struct UiState {
    title: String,
    elements: Vec<UiElement>,
    ints: HashMap<String, i64>,
}

impl UiState {
    fn new() -> Self {
        Self { title: "AL Application".into(), elements: Vec::new(), ints: HashMap::new() }
    }

    fn find(&self, id: &str) -> Option<usize> {
        self.elements.iter().position(|item| item.element_id == id)
    }
}

static UI_STATE: OnceLock<Mutex<UiState>> = OnceLock::new();
static HOST_LOCK: OnceLock<Mutex<()>> = OnceLock::new();

fn ui_state() -> &'static Mutex<UiState> {
    UI_STATE.get_or_init(|| Mutex::new(UiState::new()))
}

fn host_lock() -> &'static Mutex<()> {
    HOST_LOCK.get_or_init(|| Mutex::new(()))
}

fn lock_ui() -> MutexGuard<'static, UiState> {
    ui_state().lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn lock_host() -> MutexGuard<'static, ()> {
    host_lock().lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn value_kind(value: &RuntimeValue) -> usize {
    match value {
        RuntimeValue::Int(_) => 0,
        RuntimeValue::String(_) => 1,
        RuntimeValue::Bool(_) => 2,
        RuntimeValue::Array(_) => 3,
        RuntimeValue::Object(_) => 4,
    }
}

fn show_value(value: &RuntimeValue) -> String {
    match value {
        RuntimeValue::Bool(value) => if *value { "true" } else { "false" }.into(),
        RuntimeValue::Int(value) => value.to_string(),
        RuntimeValue::String(value) => value.clone(),
        RuntimeValue::Array(value) => format!("[array length={}]", value.borrow().items.len()),
        RuntimeValue::Object(_) => "[object]".into(),
    }
}

fn expect_int(value: RuntimeValue) -> RtResult<i64> {
    match value {
        RuntimeValue::Int(value) => Ok(value),
        _ => error("Runtime type error: expected int"),
    }
}

fn expect_bool(value: RuntimeValue) -> RtResult<bool> {
    match value {
        RuntimeValue::Bool(value) => Ok(value),
        _ => error("Runtime type error: expected boolean"),
    }
}

fn checked_add(left: i64, right: i64) -> RtResult<i64> {
    left.checked_add(right).ok_or_else(|| RuntimeError("Integer overflow".into()))
}

fn checked_sub(left: i64, right: i64) -> RtResult<i64> {
    left.checked_sub(right).ok_or_else(|| RuntimeError("Integer overflow".into()))
}

fn checked_mul(left: i64, right: i64) -> RtResult<i64> {
    left.checked_mul(right).ok_or_else(|| RuntimeError("Integer overflow".into()))
}

fn same_value(left: &RuntimeValue, right: &RuntimeValue) -> bool {
    match (left, right) {
        (RuntimeValue::Int(a), RuntimeValue::Int(b)) => a == b,
        (RuntimeValue::String(a), RuntimeValue::String(b)) => a == b,
        (RuntimeValue::Bool(a), RuntimeValue::Bool(b)) => a == b,
        (RuntimeValue::Array(a), RuntimeValue::Array(b)) => Rc::ptr_eq(a, b),
        (RuntimeValue::Object(a), RuntimeValue::Object(b)) => Rc::ptr_eq(a, b),
        _ => false,
    }
}

fn is_instance_of(actual: usize, expected: usize, classes: &[ClassInfo]) -> bool {
    let mut current = actual as i32;
    while current >= 0 && (current as usize) < classes.len() {
        if current as usize == expected {
            return true;
        }
        current = classes[current as usize].base_class_id;
    }
    false
}

fn pop(stack: &mut Vec<RuntimeValue>) -> RtResult<RuntimeValue> {
    stack.pop().ok_or_else(|| RuntimeError("Runtime stack underflow".into()))
}

fn expect_string(args: &[RuntimeValue], index: usize) -> RtResult<String> {
    match args.get(index) {
        Some(RuntimeValue::String(value)) => Ok(value.clone()),
        _ => error("UI API expected string argument"),
    }
}

fn expect_int_arg(args: &[RuntimeValue], index: usize) -> RtResult<i64> {
    match args.get(index) {
        Some(value) => expect_int(value.clone()),
        None => error("UI API missing integer argument"),
    }
}

fn upsert_ui(state: &mut UiState, element: UiElement) {
    if let Some(index) = state.find(&element.element_id) {
        state.elements[index] = element;
    } else {
        state.elements.push(element);
    }
}

fn ui_call(instruction: &Instruction, args: &[RuntimeValue], stack: &mut Vec<RuntimeValue>, state: &mut UiState) -> RtResult<()> {
    let name = instruction.text.as_str();
    if matches!(name, "screen" | "game") {
        state.title = expect_string(args, 0)?;
        if name == "game" {
            state.elements.clear();
        }
    } else if name == "text" {
        let element_id = expect_string(args, 0)?;
        let value = expect_string(args, 1)?;
        let element = UiElement {
            kind: "text".into(), element_id: element_id.clone(), text: value,
            callback: String::new(), x: 0, y: 0, w: 0, h: 0, color: 0xFFFFFFFF,
        };
        upsert_ui(state, element);
    } else if name == "button" {
        let element_id = expect_string(args, 0)?;
        let label = expect_string(args, 1)?;
        let callback = expect_string(args, 2)?;
        let element = UiElement {
            kind: "button".into(), element_id: element_id.clone(), text: label,
            callback, x: 0, y: 0, w: 0, h: 0, color: 0xFFFFFFFF,
        };
        upsert_ui(state, element);
    } else if matches!(name, "rect" | "circle" | "line") {
        let element_id = expect_string(args, 0)?;
        let mut shape = UiElement {
            kind: name.into(), element_id: element_id.clone(), text: String::new(),
            callback: String::new(), x: 0, y: 0, w: 0, h: 0, color: 0xFFFFFFFF,
        };
        if name == "rect" {
            shape.x = expect_int_arg(args, 1)?;
            shape.y = expect_int_arg(args, 2)?;
            shape.w = expect_int_arg(args, 3)?;
            shape.h = expect_int_arg(args, 4)?;
            shape.color = expect_int_arg(args, 5)? as u32;
            if shape.w < 0 || shape.h < 0 {
                return error("UI.rect width and height cannot be negative");
            }
        } else if name == "circle" {
            shape.x = expect_int_arg(args, 1)?;
            shape.y = expect_int_arg(args, 2)?;
            shape.w = expect_int_arg(args, 3)?;
            shape.color = expect_int_arg(args, 4)? as u32;
            if shape.w < 0 {
                return error("UI.circle radius cannot be negative");
            }
        } else {
            shape.x = expect_int_arg(args, 1)?;
            shape.y = expect_int_arg(args, 2)?;
            shape.w = expect_int_arg(args, 3)?;
            shape.h = expect_int_arg(args, 4)?;
            shape.color = expect_int_arg(args, 5)? as u32;
        }
        upsert_ui(state, shape);
    } else if name == "remove" {
        let element_id = expect_string(args, 0)?;
        state.elements.retain(|item| item.element_id != element_id);
    } else if name == "setText" {
        let element_id = expect_string(args, 0)?;
        let value = args.get(1).map(show_value).unwrap_or_default();
        let index = state.find(&element_id)
            .ok_or_else(|| RuntimeError(format!("UI.setText unknown element: {element_id}")))?;
        state.elements[index].text = value;
    } else if name == "setInt" {
        state.ints.insert(expect_string(args, 0)?, expect_int_arg(args, 1)?);
    } else if name == "getInt" {
        let key = expect_string(args, 0)?;
        let default = expect_int_arg(args, 1)?;
        stack.push(RuntimeValue::Int(*state.ints.get(&key).unwrap_or(&default)));
    } else if name == "clear" {
        state.elements.clear();
        state.ints.clear();
    } else {
        return error(format!("Unknown UI opcode: {name}"));
    }
    Ok(())
}

#[derive(Clone)]
struct Frame {
    function_id: usize,
    pc: usize,
    locals: Vec<Option<RuntimeValue>>,
    stack_base: usize,
}

fn execute(program: &Program, entry_name: &str, event_value: &str, state: &mut UiState) -> RtResult<String> {
    verify(program)?;
    let functions = &program.functions;
    let classes = &program.classes;
    let mut output = Vec::<String>::new();
    let entry_index = if entry_name == "main" {
        *state = UiState::new();
        0
    } else {
        let matches: Vec<usize> = functions.iter().enumerate().skip(1)
            .filter(|(_, function)| {
                (function.name == entry_name || function.name.starts_with(&format!("{entry_name}$")))
                    && function.owner_class_id < 0
                    && function.parameter_count == 1
                    && function.return_type == TYPE_VOID
            })
            .map(|(index, _)| index)
            .collect();
        if matches.len() > 1 {
            return error(format!("Ambiguous UI event handler: {entry_name}"));
        }
        match matches.first() {
            Some(index) => *index,
            None => return error(format!("Unknown UI event handler: {entry_name}")),
        }
    };

    let entry_fn = &functions[entry_index];
    let mut entry_locals = vec![None; entry_fn.local_count];
    if entry_name != "main" {
        if entry_locals.is_empty() {
            return error("UI event handler has no parameter");
        }
        entry_locals[0] = Some(RuntimeValue::String(event_value.to_string()));
    }
    let mut frames = vec![Frame { function_id: entry_index, pc: 0, locals: entry_locals, stack_base: 0 }];
    let mut stack = Vec::<RuntimeValue>::new();

    while !frames.is_empty() {
        let frame_index = frames.len() - 1;
        let function_id = frames[frame_index].function_id;
        let function = &functions[function_id];
        let pc = frames[frame_index].pc;
        if pc >= function.code.len() {
            return error("Instruction pointer outside function");
        }
        let instruction = function.code[pc].clone();
        frames[frame_index].pc += 1;
        let op = instruction.op;
        let arg = instruction.arg;
        let step = (|| -> RtResult<Control> {
            match op {
                DUP => {
                    let value = stack.last().cloned().ok_or_else(|| RuntimeError("DUP on empty stack".into()))?;
                    stack.push(value);
                }
                PUSH_INT => stack.push(RuntimeValue::Int(arg)),
                PUSH_STRING => stack.push(RuntimeValue::String(instruction.text.clone())),
                PUSH_BOOL => stack.push(RuntimeValue::Bool(arg != 0)),
                LOAD => {
                    let slot = usize::try_from(arg).map_err(|_| RuntimeError("Invalid local slot".into()))?;
                    let value = frames[frame_index].locals.get(slot).and_then(Clone::clone)
                        .ok_or_else(|| RuntimeError("Read of uninitialized local variable".into()))?;
                    stack.push(value);
                }
                STORE => {
                    let slot = usize::try_from(arg).map_err(|_| RuntimeError("Invalid local slot".into()))?;
                    let value = pop(&mut stack)?;
                    let destination = frames[frame_index].locals.get_mut(slot)
                        .ok_or_else(|| RuntimeError("Invalid local slot".into()))?;
                    *destination = Some(value);
                }
                PRINT => output.push(show_value(&pop(&mut stack)?)),
                POP => { pop(&mut stack)?; }
                UI_CALL => {
                    let count = usize::try_from(arg).map_err(|_| RuntimeError("Invalid UI call argument count".into()))?;
                    if count > 6 || stack.len() < count {
                        return error("Invalid UI call argument count");
                    }
                    let args = if count == 0 { Vec::new() } else { stack.split_off(stack.len() - count) };
                    ui_call(&instruction, &args, &mut stack, state)?;
                }
                NEG => {
                    let value = expect_int(pop(&mut stack)?)?;
                    if value == I64_MIN { return error("Integer overflow"); }
                    stack.push(RuntimeValue::Int(-value));
                }
                NOT => {
                    let value = expect_bool(pop(&mut stack)?)?;
                    stack.push(RuntimeValue::Bool(!value));
                }
                ADD => {
                    let right = pop(&mut stack)?;
                    let left = pop(&mut stack)?;
                    let value = match (left, right) {
                        (RuntimeValue::String(a), RuntimeValue::String(b)) => RuntimeValue::String(a + &b),
                        (a, b) => RuntimeValue::Int(checked_add(expect_int(a)?, expect_int(b)?)?),
                    };
                    stack.push(value);
                }
                SUB | MUL | DIV | MOD => {
                    let right = expect_int(pop(&mut stack)?)?;
                    let left = expect_int(pop(&mut stack)?)?;
                    let result = match op {
                        SUB => checked_sub(left, right)?,
                        MUL => checked_mul(left, right)?,
                        DIV | MOD => {
                            if right == 0 { return error("Division by zero"); }
                            if left == I64_MIN && right == -1 { return error("Integer overflow"); }
                            let quotient = left / right;
                            if op == DIV { quotient } else { left % right }
                        }
                        _ => unreachable!(),
                    };
                    stack.push(RuntimeValue::Int(result));
                }
                EQ | NE => {
                    let right = pop(&mut stack)?;
                    let left = pop(&mut stack)?;
                    let equal = same_value(&left, &right);
                    stack.push(RuntimeValue::Bool(if op == EQ { equal } else { !equal }));
                }
                LT | GT | LE | GE => {
                    let right = expect_int(pop(&mut stack)?)?;
                    let left = expect_int(pop(&mut stack)?)?;
                    let result = match op {
                        LT => left < right,
                        GT => left > right,
                        LE => left <= right,
                        GE => left >= right,
                        _ => unreachable!(),
                    };
                    stack.push(RuntimeValue::Bool(result));
                }
                AND | OR => {
                    let right = expect_bool(pop(&mut stack)?)?;
                    let left = expect_bool(pop(&mut stack)?)?;
                    stack.push(RuntimeValue::Bool(if op == AND { left && right } else { left || right }));
                }
                NEW_ARRAY => {
                    let count = usize::try_from(arg).map_err(|_| RuntimeError("Invalid array construction".into()))?;
                    if count > stack.len() || count > MAX_ARRAY_ITEMS {
                        return error("Invalid array construction");
                    }
                    let items = if count == 0 { Vec::new() } else { stack.split_off(stack.len() - count) };
                    let element_kind = if let Some(first) = items.first() {
                        let kind = value_kind(first);
                        if items.iter().any(|item| value_kind(item) != kind) {
                            return error("Array element type mismatch in bytecode");
                        }
                        kind as u64
                    } else {
                        u64::MAX
                    };
                    stack.push(RuntimeValue::Array(Rc::new(RefCell::new(ArrayValue { items, element_kind }))));
                }
                NEW_ARRAY_SIZED => {
                    let length = expect_int(pop(&mut stack)?)?;
                    if !(0..=MAX_ARRAY_ITEMS as i64).contains(&length) {
                        return error("Array length must be between 0 and 100000");
                    }
                    let element_type = u32::try_from(arg).map_err(|_| RuntimeError("Invalid sized-array element type".into()))?;
                    let (kind, default) = match element_type {
                        TYPE_INT => (0, RuntimeValue::Int(0)),
                        TYPE_STRING => (1, RuntimeValue::String(String::new())),
                        TYPE_BOOL => (2, RuntimeValue::Bool(false)),
                        _ => return error("Invalid sized-array element type"),
                    };
                    stack.push(RuntimeValue::Array(Rc::new(RefCell::new(ArrayValue {
                        items: vec![default; length as usize], element_kind: kind,
                    }))));
                }
                ARRAY_GET => {
                    let index = expect_int(pop(&mut stack)?)?;
                    let base = pop(&mut stack)?;
                    let array = match base {
                        RuntimeValue::Array(value) => value,
                        _ => return error("Array access on non-array value"),
                    };
                    if index < 0 { return error("Array index out of bounds"); }
                    let item = array.borrow().items.get(index as usize).cloned()
                        .ok_or_else(|| RuntimeError("Array index out of bounds".into()))?;
                    stack.push(item);
                }
                ARRAY_SET => {
                    let value = pop(&mut stack)?;
                    let index = expect_int(pop(&mut stack)?)?;
                    let base = pop(&mut stack)?;
                    let array = match base {
                        RuntimeValue::Array(value) => value,
                        _ => return error("Array assignment on non-array value"),
                    };
                    if index < 0 { return error("Array index out of bounds"); }
                    let mut array = array.borrow_mut();
                    if index as usize >= array.items.len() { return error("Array index out of bounds"); }
                    let kind = value_kind(&value);
                    if array.element_kind == u64::MAX { array.element_kind = kind as u64; }
                    if kind as u64 != array.element_kind { return error("Array element type mismatch"); }
                    array.items[index as usize] = value;
                }
                ARRAY_LENGTH => {
                    let base = pop(&mut stack)?;
                    let length = match base {
                        RuntimeValue::Array(value) => value.borrow().items.len(),
                        _ => return error(".length on non-array value"),
                    };
                    stack.push(RuntimeValue::Int(length as i64));
                }
                NEW_OBJECT => {
                    let class_id = usize::try_from(arg).map_err(|_| RuntimeError("Invalid class allocation".into()))?;
                    let class = classes.get(class_id).ok_or_else(|| RuntimeError("Invalid class allocation".into()))?;
                    let fields = class.field_types.iter().map(|field_type| match *field_type {
                        TYPE_INT => Ok(RuntimeValue::Int(0)),
                        TYPE_STRING => Ok(RuntimeValue::String(String::new())),
                        TYPE_BOOL => Ok(RuntimeValue::Bool(false)),
                        _ => error("Unsupported object field type"),
                    }).collect::<RtResult<Vec<_>>>()?;
                    stack.push(RuntimeValue::Object(Rc::new(RefCell::new(ObjectValue { class_id, fields }))));
                }
                GET_FIELD => {
                    let base = pop(&mut stack)?;
                    let object = match base {
                        RuntimeValue::Object(value) => value,
                        _ => return error("Field read on non-object value"),
                    };
                    let field_index = usize::try_from(arg).map_err(|_| RuntimeError("Object field index out of bounds".into()))?;
                    let value = object.borrow().fields.get(field_index).cloned()
                        .ok_or_else(|| RuntimeError("Object field index out of bounds".into()))?;
                    stack.push(value);
                }
                SET_FIELD => {
                    let value = pop(&mut stack)?;
                    let base = pop(&mut stack)?;
                    let object = match base {
                        RuntimeValue::Object(value) => value,
                        _ => return error("Field write on non-object value"),
                    };
                    let field_index = usize::try_from(arg).map_err(|_| RuntimeError("Object field index out of bounds".into()))?;
                    let mut object = object.borrow_mut();
                    let expected_type = classes.get(object.class_id)
                        .and_then(|class| class.field_types.get(field_index))
                        .copied().ok_or_else(|| RuntimeError("Object field index out of bounds".into()))?;
                    let expected_kind = match expected_type { TYPE_INT => 0, TYPE_STRING => 1, TYPE_BOOL => 2, _ => usize::MAX };
                    if value_kind(&value) != expected_kind { return error("Object field type mismatch"); }
                    let slot = object.fields.get_mut(field_index)
                        .ok_or_else(|| RuntimeError("Object field index out of bounds".into()))?;
                    *slot = value;
                }
                CALL => {
                    let target = usize::try_from(arg).map_err(|_| RuntimeError("Invalid call target".into()))?;
                    let callee = functions.get(target).ok_or_else(|| RuntimeError("Invalid call target".into()))?;
                    if frames.len() >= MAX_CALL_DEPTH { return error("Maximum call depth exceeded"); }
                    let count = callee.parameter_count + if callee.owner_class_id >= 0 { 1 } else { 0 };
                    if stack.len() < count { return error("Call argument stack underflow"); }
                    let args = if count == 0 { Vec::new() } else { stack.split_off(stack.len() - count) };
                    if callee.owner_class_id >= 0 {
                        let receiver = match args.first() {
                            Some(RuntimeValue::Object(value)) => value.borrow().class_id,
                            _ => return error("Instance method called with incompatible receiver object"),
                        };
                        if !is_instance_of(receiver, callee.owner_class_id as usize, classes) {
                            return error("Instance method called with incompatible receiver object");
                        }
                    }
                    let mut locals = vec![None; callee.local_count];
                    for (index, value) in args.into_iter().enumerate() {
                        locals[index] = Some(value);
                    }
                    frames.push(Frame { function_id: target, pc: 0, locals, stack_base: stack.len() });
                }
                RET => {
                    let returns_value = function.return_type != TYPE_VOID;
                    let result = if returns_value { Some(pop(&mut stack)?) } else { None };
                    let base = frames[frame_index].stack_base;
                    frames.pop();
                    if frames.is_empty() {
                        return Ok(Control::Finish(output.join("\n")));
                    }
                    if stack.len() < base { return error("Call frame stack underflow"); }
                    stack.truncate(base);
                    if let Some(value) = result { stack.push(value); }
                }
                JUMP => {
                    let destination = usize::try_from(arg).map_err(|_| RuntimeError("Invalid jump target".into()))?;
                    frames[frame_index].pc = destination;
                }
                JUMP_FALSE => {
                    let condition = expect_bool(pop(&mut stack)?)?;
                    if !condition {
                        let destination = usize::try_from(arg).map_err(|_| RuntimeError("Invalid jump target".into()))?;
                        frames[frame_index].pc = destination;
                    }
                }
                HALT => return Ok(Control::Finish(output.join("\n"))),
                _ => return error(format!("Unsupported opcode: {op}")),
            }
            Ok(Control::Continue)
        })();
        match step {
            Ok(Control::Continue) => {}
            Ok(Control::Finish(final_output)) => return Ok(final_output),
            Err(runtime_error) => {
                return error(format!(
                    "{} (function {}, instruction {})",
                    runtime_error.0, function.name, pc
                ));
            }
        }
    }
    Ok(output.join("\n"))
}

enum Control { Continue, Finish(String) }

fn run_room_bytes(data: &[u8], entry_name: &str, event_value: &str, version: &str) -> JsonValue {
    let result = (|| -> RtResult<(String, UiState)> {
        let program = read_room_bytes(data)?;
        verify(&program)?;
        let mut state = lock_ui();
        let output = execute(&program, entry_name, event_value, &mut state)?;
        Ok((output, UiState {
            title: state.title.clone(),
            elements: state.elements.clone(),
            ints: state.ints.clone(),
        }))
    })();
    match result {
        Ok((output, state)) => json!({
            "ok": true, "output": output,
            "ui": { "title": state.title, "elements": state.elements },
            "runtimeVersion": version,
        }),
        Err(error) => {
            let state = lock_ui();
            json!({
                "ok": false, "output": "",
                "error": format!("CI Runtime error: {}", error.0),
                "ui": { "title": state.title, "elements": state.elements },
                "runtimeVersion": version,
            })
        }
    }
}

fn run_room_file(room_path: &str, entry_name: &str, event_value: &str, version: &str) -> JsonValue {
    let result = (|| -> RtResult<Vec<u8>> {
        let path = Path::new(room_path);
        let metadata = fs::metadata(path).map_err(|_| RuntimeError("Cannot open .room file".into()))?;
        if metadata.len() > MAX_FILE_BYTES as u64 {
            return error(".room file exceeds the 64 MiB safety limit");
        }
        fs::read(path).map_err(|_| RuntimeError("Cannot open .room file".into()))
    })();
    match result {
        Ok(data) => run_room_bytes(&data, entry_name, event_value, version),
        Err(error) => json!({
            "ok": false, "output": "",
            "error": format!("CI Runtime error: {}", error.0),
            "ui": current_ui_json(),
            "runtimeVersion": version,
        }),
    }
}

fn current_ui_json() -> JsonValue {
    let state = lock_ui();
    json!({ "title": state.title, "elements": state.elements })
}

pub fn run_room(room_path: &str, entry_name: &str, event_value: &str) -> JsonValue {
    run_room_file(room_path, entry_name, event_value, build_version())
}

fn build_version() -> &'static str {
    option_env!("CI_RUNTIME_VERSION").unwrap_or(BUNDLED_VERSION)
}

fn runtime_result_with_version(mut result: JsonValue, version: &str) -> JsonValue {
    result["runtimeVersion"] = json!(version);
    result
}

// -----------------------------------------------------------------------------
// Versioned Rust runtime module loader, package validation and rollback manager.
// -----------------------------------------------------------------------------

#[derive(Clone, Serialize, Deserialize)]
struct RuntimeState {
    active_version: String,
    #[serde(default)]
    previous_versions: Vec<String>,
}

impl Default for RuntimeState {
    fn default() -> Self {
        Self { active_version: "bundled".into(), previous_versions: Vec::new() }
    }
}

#[derive(Serialize, Deserialize)]
struct RuntimeManifest {
    name: String,
    version: String,
    runtime_api_version: u32,
    room_format_min: u32,
    room_format_max: u32,
    sha256: HashMap<String, String>,
}

fn abi_library_name(abi: &str) -> RtResult<&'static str> {
    match abi {
        "arm64-v8a" => Ok("android/arm64-v8a/libci_runtime.so"),
        "armeabi-v7a" => Ok("android/armeabi-v7a/libci_runtime.so"),
        _ => error(format!("Unsupported Android ABI: {abi}")),
    }
}

fn runtime_root(root: &str) -> RtResult<PathBuf> {
    if root.trim().is_empty() { return error("Runtime storage path is empty"); }
    let root = PathBuf::from(root);
    fs::create_dir_all(root.join("versions"))
        .map_err(|error| RuntimeError(format!("Cannot create CI Runtime storage: {error}")))?;
    Ok(root)
}

fn version_valid(version: &str) -> bool {
    let parts: Vec<&str> = version.split('.').collect();
    parts.len() == 3 && parts.iter().all(|part| !part.is_empty() && part.bytes().all(|b| b.is_ascii_digit()))
}

fn compare_versions(left: &str, right: &str) -> std::cmp::Ordering {
    let parse = |value: &str| -> [u64; 3] {
        let mut result = [0u64; 3];
        for (index, part) in value.split('.').take(3).enumerate() {
            result[index] = part.parse().unwrap_or(0);
        }
        result
    };
    parse(left).cmp(&parse(right))
}

fn read_state(root: &Path) -> RuntimeState {
    let path = root.join("active.json");
    match fs::read(&path).ok().and_then(|data| serde_json::from_slice::<RuntimeState>(&data).ok()) {
        Some(mut state) if state.previous_versions.len() <= 5 => {
            state.previous_versions.retain(|item| item == "bundled" || version_valid(item));
            if state.active_version != "bundled" && !version_valid(&state.active_version) {
                RuntimeState::default()
            } else {
                state
            }
        }
        _ => RuntimeState::default(),
    }
}

fn write_state(root: &Path, state: &RuntimeState) -> RtResult<()> {
    let destination = root.join("active.json");
    let temporary = root.join("active.json.tmp");
    let bytes = serde_json::to_vec_pretty(state)
        .map_err(|error| RuntimeError(format!("Cannot encode runtime state: {error}")))?;
    {
        let mut file = File::create(&temporary)
            .map_err(|error| RuntimeError(format!("Cannot write runtime state: {error}")))?;
        file.write_all(&bytes)
            .and_then(|_| file.sync_all())
            .map_err(|error| RuntimeError(format!("Cannot persist runtime state: {error}")))?;
    }
    fs::rename(&temporary, &destination)
        .map_err(|error| RuntimeError(format!("Cannot activate runtime state: {error}")))?;
    Ok(())
}

fn version_dir(root: &Path, version: &str) -> PathBuf {
    root.join("versions").join(version)
}

fn sha256(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

fn load_plugin(path: &Path, expected_version: Option<&str>) -> RtResult<Library> {
    let library = unsafe {
        Library::new(path).map_err(|error| RuntimeError(format!("Cannot load Rust CI Runtime module: {error}")))?
    };
    unsafe {
        let api: Symbol<unsafe extern "C" fn() -> u32> = library.get(b"ci_runtime_plugin_api_version\0")
            .map_err(|_| RuntimeError("Runtime module is missing its API version export".into()))?;
        if api() != RUNTIME_API_VERSION {
            return error("Runtime API version mismatch");
        }
        let version_fn: Symbol<unsafe extern "C" fn() -> *const c_char> = library.get(b"ci_runtime_plugin_version\0")
            .map_err(|_| RuntimeError("Runtime module is missing its version export".into()))?;
        let version_ptr = version_fn();
        if version_ptr.is_null() {
            return error("Runtime module returned an empty version");
        }
        let version = CStr::from_ptr(version_ptr).to_string_lossy();
        if let Some(expected) = expected_version {
            if version != expected {
                return error("Runtime code version does not match its manifest");
            }
        }
        let test: Symbol<unsafe extern "C" fn() -> bool> = library.get(b"ci_runtime_plugin_self_test\0")
            .map_err(|_| RuntimeError("Runtime module is missing its self-test export".into()))?;
        if !test() {
            return error("CI Runtime self-test failed");
        }
    }
    Ok(library)
}

fn manifest_for_dir(version_dir: &Path, abi: &str) -> RtResult<RuntimeManifest> {
    let manifest_path = version_dir.join("manifest.json");
    let manifest_bytes = fs::read(&manifest_path)
        .map_err(|_| RuntimeError("Runtime version folder is incomplete".into()))?;
    if manifest_bytes.len() as u64 > MAX_MANIFEST_BYTES {
        return error("Runtime manifest exceeds the 64 KiB limit");
    }
    let manifest: RuntimeManifest = serde_json::from_slice(&manifest_bytes)
        .map_err(|error| RuntimeError(format!("Invalid runtime manifest: {error}")))?;
    if manifest.name != "CI Runtime" {
        return error("Invalid runtime package name");
    }
    if manifest.version != version_dir.file_name().and_then(|name| name.to_str()).unwrap_or("") {
        return error("Runtime version folder does not match manifest");
    }
    if !version_valid(&manifest.version) {
        return error("Invalid runtime version");
    }
    if manifest.runtime_api_version != RUNTIME_API_VERSION {
        return error("Runtime API version mismatch");
    }
    if !(manifest.room_format_min <= ROOM_FORMAT_VERSION && ROOM_FORMAT_VERSION <= manifest.room_format_max) {
        return error("This runtime does not support the installed .room format");
    }
    let library_path = version_dir.join(abi_library_name(abi)?);
    let library_bytes = fs::read(&library_path)
        .map_err(|_| RuntimeError(format!("Runtime module for {abi} is missing")))?;
    let expected = manifest.sha256.get(abi)
        .ok_or_else(|| RuntimeError(format!("Runtime SHA-256 is missing for {abi}")))?;
    if sha256(&library_bytes).to_lowercase() != expected.to_lowercase() {
        return error("Runtime package checksum mismatch");
    }
    if library_bytes.len() as u64 > MAX_RUNTIME_LIBRARY_BYTES {
        return error("Runtime module exceeds the 2 MiB limit");
    }
    Ok(manifest)
}

fn validate_installed_plugin(root: &Path, version: &str, abi: &str) -> RtResult<PathBuf> {
    let directory = version_dir(root, version);
    let manifest = manifest_for_dir(&directory, abi)?;
    let path = directory.join(abi_library_name(abi)?);
    let _library = load_plugin(&path, Some(&manifest.version))?;
    Ok(path)
}

fn previous_valid_version(root: &Path, candidates: &[String], abi: &str) -> String {
    for version in candidates {
        if version == "bundled" { return "bundled".into(); }
        if validate_installed_plugin(root, version, abi).is_ok() {
            return version.clone();
        }
    }
    "bundled".into()
}

fn activate_fallback(root: &Path, state: &RuntimeState, abi: &str) -> RtResult<RuntimeState> {
    let fallback = previous_valid_version(root, &state.previous_versions, abi);
    let mut previous = vec![state.active_version.clone()];
    previous.extend(state.previous_versions.iter().filter(|item| **item != fallback).cloned());
    previous.truncate(5);
    let updated = RuntimeState { active_version: fallback, previous_versions: previous };
    write_state(root, &updated)?;
    Ok(updated)
}

fn run_with_active(root: &Path, abi: &str, room_path: &str, entry: &str, event: &str) -> RtResult<JsonValue> {
    let state = read_state(root);
    if state.active_version == "bundled" {
        return Ok(run_room_file(room_path, entry, event, BUNDLED_VERSION));
    }
    let plugin_path = match validate_installed_plugin(root, &state.active_version, abi) {
        Ok(path) => path,
        Err(_) => {
            let fallback = activate_fallback(root, &state, abi)?;
            if fallback.active_version == "bundled" {
                return Ok(run_room_file(room_path, entry, event, BUNDLED_VERSION));
            }
            return run_with_active(root, abi, room_path, entry, event);
        }
    };
    let library = load_plugin(&plugin_path, Some(&state.active_version))?;
    let run: Symbol<unsafe extern "C" fn(*const c_char, *const c_char, *const c_char) -> *mut c_char> =
        unsafe { library.get(b"ci_runtime_plugin_run_room_json\0") }
            .map_err(|_| RuntimeError("Runtime module is missing its run export".into()))?;
    let path = CString::new(room_path).map_err(|_| RuntimeError("Invalid .room path".into()))?;
    let entry = CString::new(entry).map_err(|_| RuntimeError("Invalid entry name".into()))?;
    let event = CString::new(event).map_err(|_| RuntimeError("Invalid event value".into()))?;
    let result_ptr = unsafe { run(path.as_ptr(), entry.as_ptr(), event.as_ptr()) };
    if result_ptr.is_null() {
        return error("Runtime module returned an empty result");
    }
    let result = unsafe { CStr::from_ptr(result_ptr).to_string_lossy().into_owned() };
    let free: Symbol<unsafe extern "C" fn(*mut c_char)> =
        unsafe { library.get(b"ci_runtime_plugin_free_string\0") }
            .map_err(|_| RuntimeError("Runtime module is missing its memory release export".into()))?;
    unsafe { free(result_ptr) };
    let parsed: JsonValue = serde_json::from_str(&result)
        .map_err(|error| RuntimeError(format!("Runtime module returned invalid JSON: {error}")))?;
    Ok(runtime_result_with_version(parsed, &state.active_version))
}

fn status_json(root: &Path, abi: &str) -> JsonValue {
    let mut state = read_state(root);
    if state.active_version != "bundled"
        && validate_installed_plugin(root, &state.active_version, abi).is_err()
    {
        if let Ok(updated) = activate_fallback(root, &state, abi) {
            state = updated;
        } else {
            state = RuntimeState::default();
        }
    }
    json!({
        "ok": true,
        "active_version": state.active_version,
        "bundled_version": BUNDLED_VERSION,
        "previous_versions": state.previous_versions,
        "room_format": ROOM_FORMAT_VERSION,
        "runtime_api_version": RUNTIME_API_VERSION,
        "implementation": "Rust",
    })
}

fn install_update(package_path: &str, root: &Path, abi: &str) -> RtResult<JsonValue> {
    let archive_file = File::open(package_path)
        .map_err(|_| RuntimeError("Runtime update file was not found".into()))?;
    let metadata = archive_file.metadata()
        .map_err(|_| RuntimeError("Cannot inspect runtime update package".into()))?;
    if metadata.len() > MAX_PACKAGE_BYTES {
        return error("Runtime update package exceeds the 4 MiB limit");
    }
    let mut archive = ZipArchive::new(archive_file)
        .map_err(|_| RuntimeError("Runtime update is not a valid ZIP package".into()))?;
    if archive.len() != 3 {
        return error("Package must contain manifest.json and both Android ABI modules");
    }
    let mut names = HashSet::new();
    for index in 0..archive.len() {
        let entry = archive.by_index(index)
            .map_err(|_| RuntimeError("Cannot read runtime package entry".into()))?;
        names.insert(entry.name().to_string());
    }
    let expected_names: HashSet<String> = [
        "manifest.json",
        "android/arm64-v8a/libci_runtime.so",
        "android/armeabi-v7a/libci_runtime.so",
    ].into_iter().map(str::to_string).collect();
    if names != expected_names {
        return error("Runtime package contains unexpected or missing files");
    }

    let manifest_bytes = {
        let mut entry = archive.by_name("manifest.json")
            .map_err(|_| RuntimeError("Runtime manifest is missing".into()))?;
        if entry.size() > MAX_MANIFEST_BYTES { return error("Runtime manifest exceeds the 64 KiB limit"); }
        let mut bytes = Vec::with_capacity(entry.size() as usize);
        entry.read_to_end(&mut bytes)
            .map_err(|_| RuntimeError("Cannot read runtime manifest".into()))?;
        bytes
    };
    let manifest: RuntimeManifest = serde_json::from_slice(&manifest_bytes)
        .map_err(|error| RuntimeError(format!("Invalid runtime manifest: {error}")))?;
    if manifest.name != "CI Runtime" { return error("Invalid runtime package name"); }
    if !version_valid(&manifest.version) { return error("Runtime version must use major.minor.patch"); }
    if manifest.runtime_api_version != RUNTIME_API_VERSION { return error("Runtime API version is not supported"); }
    if !(manifest.room_format_min <= ROOM_FORMAT_VERSION && ROOM_FORMAT_VERSION <= manifest.room_format_max) {
        return error("Runtime package does not support .room version 9");
    }
    let abi_entry_name = abi_library_name(abi)?;
    let mut module_bytes = {
        let mut entry = archive.by_name(abi_entry_name)
            .map_err(|_| RuntimeError(format!("Package does not contain {abi} runtime")))?;
        if entry.size() > MAX_RUNTIME_LIBRARY_BYTES { return error("Runtime module exceeds the 2 MiB limit"); }
        let mut bytes = Vec::with_capacity(entry.size() as usize);
        entry.read_to_end(&mut bytes)
            .map_err(|_| RuntimeError("Cannot read Rust runtime module".into()))?;
        bytes
    };
    let expected = manifest.sha256.get(abi)
        .ok_or_else(|| RuntimeError(format!("Runtime SHA-256 is missing for {abi}")))?;
    if sha256(&module_bytes).to_lowercase() != expected.to_lowercase() {
        return error("Runtime package SHA-256 verification failed");
    }

    let destination = version_dir(root, &manifest.version);
    if destination.exists() { return error("This runtime version is already installed"); }
    let stage = root.join("versions").join(format!(".staging-{}", manifest.version));
    if stage.exists() { fs::remove_dir_all(&stage).map_err(|e| RuntimeError(e.to_string()))?; }
    fs::create_dir_all(stage.join("android"))
        .map_err(|e| RuntimeError(format!("Cannot prepare runtime update: {e}")))?;
    let stage_library = stage.join(abi_entry_name);
    if let Some(parent) = stage_library.parent() {
        fs::create_dir_all(parent).map_err(|e| RuntimeError(e.to_string()))?;
    }
    fs::write(&stage_library, &module_bytes)
        .map_err(|e| RuntimeError(format!("Cannot install Rust runtime module: {e}")))?;
    let mut full_manifest = manifest;
    let mut full_hashes = full_manifest.sha256.clone();
    full_hashes.insert(abi.into(), sha256(&module_bytes));
    full_manifest.sha256 = full_hashes;
    let normalized = serde_json::to_vec_pretty(&full_manifest)
        .map_err(|e| RuntimeError(format!("Cannot encode runtime manifest: {e}")))?;
    fs::write(stage.join("manifest.json"), normalized)
        .map_err(|e| RuntimeError(format!("Cannot save runtime manifest: {e}")))?;

    let staged_library = stage.join(abi_entry_name);
    load_plugin(&staged_library, Some(&full_manifest.version))?;
    fs::rename(&stage, &destination)
        .map_err(|e| RuntimeError(format!("Cannot finalize runtime installation: {e}")))?;

    let old_state = read_state(root);
    let old_active = old_state.active_version;
    let mut previous = vec![old_active.clone()];
    previous.extend(old_state.previous_versions.into_iter()
        .filter(|item| item != &old_active && item != &full_manifest.version));
    previous.truncate(5);
    let new_state = RuntimeState { active_version: full_manifest.version.clone(), previous_versions: previous.clone() };
    write_state(root, &new_state)?;
    module_bytes.clear();

    Ok(json!({
        "ok": true,
        "message": "Rust CI Runtime update installed and activated",
        "active_version": new_state.active_version,
        "previous_versions": new_state.previous_versions,
        "room_format": ROOM_FORMAT_VERSION,
    }))
}

fn rollback(root: &Path, abi: &str) -> RtResult<JsonValue> {
    let state = read_state(root);
    if state.previous_versions.is_empty() {
        return error("No earlier runtime version is available");
    }
    let selected = previous_valid_version(root, &state.previous_versions, abi);
    if selected == state.active_version {
        return error("No earlier working runtime is available");
    }
    let mut remaining: Vec<String> = state.previous_versions.into_iter()
        .filter(|item| item != &selected)
        .collect();
    remaining.insert(0, state.active_version);
    remaining.truncate(5);
    let next = RuntimeState { active_version: selected.clone(), previous_versions: remaining };
    write_state(root, &next)?;
    Ok(json!({
        "ok": true,
        "message": "Rust CI Runtime rollback completed",
        "active_version": next.active_version,
        "previous_versions": next.previous_versions,
    }))
}

fn json_string(value: JsonValue) -> String {
    serde_json::to_string(&value).unwrap_or_else(|_| "{\"ok\":false,\"error\":\"JSON serialization failed\"}".into())
}

fn host_run_json(room: &str, root: &str, abi: &str, entry: &str, event: &str) -> String {
    let _guard = lock_host();
    let root_path = match runtime_root(root) {
        Ok(value) => value,
        Err(error) => return json_string(json!({
            "ok": false, "output": "", "error": format!("CI Runtime loader error: {}", error.0),
            "ui": current_ui_json(), "runtimeVersion": BUNDLED_VERSION,
        })),
    };
    let mut state = read_state(&root_path);
    if state.active_version != "bundled"
        && validate_installed_plugin(&root_path, &state.active_version, abi).is_err()
    {
        if let Ok(updated) = activate_fallback(&root_path, &state, abi) {
            state = updated;
        }
    }
    if state.active_version == "bundled" {
        return json_string(run_room_file(room, entry, event, BUNDLED_VERSION));
    }
    match run_with_active(&root_path, abi, room, entry, event) {
        Ok(result) => json_string(result),
        Err(error) => json_string(json!({
            "ok": false, "output": "", "error": format!("CI Runtime loader error: {}", error.0),
            "ui": current_ui_json(), "runtimeVersion": BUNDLED_VERSION,
        })),
    }
}

fn host_status_json(root: &str, abi: &str) -> String {
    let _guard = lock_host();
    match runtime_root(root) {
        Ok(path) => json_string(status_json(&path, abi)),
        Err(error) => json_string(json!({"ok": false, "error": error.0, "active_version": "bundled"})),
    }
}

fn host_install_update_json(package: &str, root: &str, abi: &str) -> String {
    let _guard = lock_host();
    let result = (|| -> RtResult<JsonValue> {
        let path = runtime_root(root)?;
        install_update(package, &path, abi)
    })();
    match result {
        Ok(value) => json_string(value),
        Err(error) => json_string(json!({"ok": false, "error": error.0})),
    }
}

fn host_rollback_json(root: &str, abi: &str) -> String {
    let _guard = lock_host();
    let result = (|| -> RtResult<JsonValue> {
        let path = runtime_root(root)?;
        rollback(&path, abi)
    })();
    match result {
        Ok(value) => json_string(value),
        Err(error) => json_string(json!({"ok": false, "error": error.0})),
    }
}

fn ffi_text(value: *const c_char) -> String {
    if value.is_null() { return String::new(); }
    unsafe { CStr::from_ptr(value).to_string_lossy().into_owned() }
}

fn into_ffi_string(value: String) -> *mut c_char {
    CString::new(value.replace('\0', "�")).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn ci_runtime_host_run(
    room_path: *const c_char, runtime_root: *const c_char, abi: *const c_char,
    entry_name: *const c_char, event_value: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| host_run_json(
        &ffi_text(room_path), &ffi_text(runtime_root), &ffi_text(abi),
        &ffi_text(entry_name), &ffi_text(event_value),
    )).unwrap_or_else(|_| "{\"ok\":false,\"output\":\"\",\"error\":\"Rust CI Runtime panic\",\"runtimeVersion\":\"1.0.0\"}".into());
    into_ffi_string(result)
}

#[no_mangle]
pub extern "C" fn ci_runtime_host_status(runtime_root: *const c_char, abi: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(|| host_status_json(&ffi_text(runtime_root), &ffi_text(abi)))
        .unwrap_or_else(|_| "{\"ok\":false,\"error\":\"Rust CI Runtime panic\"}".into());
    into_ffi_string(result)
}

#[no_mangle]
pub extern "C" fn ci_runtime_host_install_update(
    package_path: *const c_char, runtime_root: *const c_char, abi: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| host_install_update_json(
        &ffi_text(package_path), &ffi_text(runtime_root), &ffi_text(abi),
    )).unwrap_or_else(|_| "{\"ok\":false,\"error\":\"Rust CI Runtime panic\"}".into());
    into_ffi_string(result)
}

#[no_mangle]
pub extern "C" fn ci_runtime_host_rollback(runtime_root: *const c_char, abi: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(|| host_rollback_json(&ffi_text(runtime_root), &ffi_text(abi)))
        .unwrap_or_else(|_| "{\"ok\":false,\"error\":\"Rust CI Runtime panic\"}".into());
    into_ffi_string(result)
}

#[no_mangle]
pub extern "C" fn ci_runtime_free_string(value: *mut c_char) {
    if !value.is_null() { unsafe { drop(CString::from_raw(value)); } }
}

#[no_mangle]
pub extern "C" fn ci_runtime_plugin_api_version() -> u32 {
    RUNTIME_API_VERSION
}

#[no_mangle]
pub extern "C" fn ci_runtime_plugin_version() -> *const c_char {
    static VERSION: OnceLock<CString> = OnceLock::new();
    VERSION.get_or_init(|| CString::new(build_version()).unwrap()).as_ptr()
}

#[no_mangle]
pub extern "C" fn ci_runtime_plugin_self_test() -> bool {
    self_test()
}

#[no_mangle]
pub extern "C" fn ci_runtime_plugin_run_room_json(
    room_path: *const c_char, entry_name: *const c_char, event_value: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| json_string(run_room_file(
        &ffi_text(room_path), &ffi_text(entry_name), &ffi_text(event_value), build_version(),
    ))).unwrap_or_else(|_| "{\"ok\":false,\"output\":\"\",\"error\":\"Rust runtime module panic\"}".into());
    into_ffi_string(result)
}

#[no_mangle]
pub extern "C" fn ci_runtime_plugin_free_string(value: *mut c_char) {
    if !value.is_null() { unsafe { drop(CString::from_raw(value)); } }
}

pub fn self_test() -> bool {
    if ROOM_FORMAT_VERSION != 9 || RUNTIME_API_VERSION != 1 { return false; }
    let sample = test_room_bytes(vec![
        Instruction { op: PUSH_INT, arg: 40, text: String::new() },
        Instruction { op: PUSH_INT, arg: 2, text: String::new() },
        Instruction { op: ADD, arg: 0, text: String::new() },
        Instruction { op: PRINT, arg: 0, text: String::new() },
        Instruction { op: HALT, arg: 0, text: String::new() },
    ]);
    let result = run_room_bytes(&sample, "main", "", build_version());
    result["ok"] == true && result["output"] == "42"
}

fn test_room_bytes(code: Vec<Instruction>) -> Vec<u8> {
    let mut bytes = b"ALRM".to_vec();
    bytes.extend_from_slice(&ROOM_FORMAT_VERSION.to_le_bytes());
    bytes.extend_from_slice(&0u32.to_le_bytes());
    bytes.extend_from_slice(&1u32.to_le_bytes());
    let string = |value: &str, output: &mut Vec<u8>| {
        output.extend_from_slice(&(value.len() as u32).to_le_bytes());
        output.extend_from_slice(value.as_bytes());
    };
    string("main", &mut bytes);
    bytes.extend_from_slice(&(-1i32).to_le_bytes());
    bytes.extend_from_slice(&TYPE_VOID.to_le_bytes());
    bytes.extend_from_slice(&0u32.to_le_bytes());
    bytes.extend_from_slice(&0u32.to_le_bytes());
    bytes.extend_from_slice(&(code.len() as u32).to_le_bytes());
    for item in code {
        bytes.push(item.op);
        bytes.extend_from_slice(&item.arg.to_le_bytes());
        string(&item.text, &mut bytes);
    }
    bytes
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rust_runtime_self_test_executes_verified_bytecode() {
        assert!(self_test());
    }

    #[test]
    fn rejects_wrong_room_version() {
        let mut bytes = test_room_bytes(vec![Instruction { op: HALT, arg: 0, text: String::new() }]);
        bytes[4..8].copy_from_slice(&8u32.to_le_bytes());
        let error = read_room_bytes(&bytes).unwrap_err();
        assert!(error.0.contains("Unsupported .room version"));
    }

    #[test]
    fn rejects_truncated_room() {
        let error = read_room_bytes(b"ALRM\x09\x00").unwrap_err();
        assert!(error.0.contains("Truncated"));
    }

    #[test]
    fn detects_addition_overflow() {
        assert!(checked_add(i64::MAX, 1).is_err());
        assert!(checked_sub(i64::MIN, 1).is_err());
        assert!(checked_mul(i64::MAX, 2).is_err());
    }

    #[test]
    fn compares_object_references_by_identity() {
        let object = Rc::new(RefCell::new(ObjectValue { class_id: 0, fields: vec![] }));
        let left = RuntimeValue::Object(object.clone());
        let right = RuntimeValue::Object(object);
        assert!(same_value(&left, &right));
        let other = RuntimeValue::Object(Rc::new(RefCell::new(ObjectValue { class_id: 0, fields: vec![] })));
        assert!(!same_value(&left, &other));
    }

    #[test]
    fn supports_version_comparison() {
        assert_eq!(compare_versions("1.10.0", "1.9.9"), std::cmp::Ordering::Greater);
        assert_eq!(compare_versions("2.0.0", "2.0.0"), std::cmp::Ordering::Equal);
    }
}
