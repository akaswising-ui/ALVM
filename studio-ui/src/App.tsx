import { useEffect, useMemo, useRef, useState } from 'react';
import ChessPlayer from './ChessPlayer';
import Libraries from './Libraries';
import WebsiteBuilder from './WebsiteBuilder';
import './builder.css';
import {
  Activity, BookOpen, Braces, Boxes, Code2, FileCode2, FolderOpen, Globe,
  Package, Play, Save, Terminal, Upload, X
} from 'lucide-react';

type AndroidBridgeApi = {
  getAppVersion?: () => string;
  getCiRuntimeVersion?: () => string;
  openWorkspace?: () => void;
  importFile?: () => void;
  showCiRuntimeManager?: () => void;
  createAlPackage?: (source: string, name: string) => void;
};

declare global {
  interface Window {
    Android?: AndroidBridgeApi;
    __alvmSetRuntimeVersion?: (version: string) => void;
  }
}

type Value = number | string | boolean;
type Token = { type: string; value: string; line: number };
type Instruction = { op: string; arg?: Value | number | { name: string; argc: number } };
type GameObject = { id: string; kind: 'rect' | 'circle' | 'text'; x: number; y: number; w: number; h: number; color: string; text?: string; size?: number };
type GameScene = { width: number; height: number; background: string; objects: GameObject[]; buttons: { id: string; label: string }[]; controls: { button: string; target: string; dx: number; dy: number }[]; chess?: boolean };
type VarInfo = { name: string; type: 'int' | 'String' | 'boolean'; slot: number };
type Program = { code: Instruction[]; vars: VarInfo[] };

const sample = "// Write your AL program here.\nRoom Main {\n    public static void main() {\n        AL.print(\"Hello from AL\");\n    }\n}";

function tokenize(source: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  let line = 1;
  while (i < source.length) {
    const c = source[i];
    if (c === '\n') { line++; i++; continue; }
    if (/\s/.test(c)) { i++; continue; }
    if (c === '/' && source[i + 1] === '/') {
      while (i < source.length && source[i] !== '\n') i++;
      continue;
    }
    const rest = source.slice(i);
    if (rest.startsWith('AL.print')) {
      tokens.push({ type: 'PRINT', value: 'AL.print', line });
      i += 8;
      continue;
    }
    const str = rest.match(/^"(?:\\.|[^"\\])*"/);
    if (str) {
      tokens.push({ type: 'STRING', value: JSON.parse(str[0]), line });
      i += str[0].length;
      continue;
    }
    const number = rest.match(/^\d+(?:\.\d+)?/);
    if (number) {
      tokens.push({ type: 'NUMBER', value: number[0], line });
      i += number[0].length;
      continue;
    }
    const pair = rest.slice(0, 2);
    if (['==', '!=', '<=', '>=', '&&', '||'].includes(pair)) {
      tokens.push({ type: pair, value: pair, line });
      i += 2;
      continue;
    }
    const id = rest.match(/^[A-Za-z_][A-Za-z0-9_]*/);
    if (id) {
      const value = id[0];
      const keywords = ['int', 'String', 'boolean', 'Room', 'public', 'static', 'void', 'main', 'if', 'else', 'while', 'true', 'false'];
      tokens.push({ type: keywords.includes(value) ? value.toUpperCase() : 'IDENT', value, line });
      i += value.length;
      continue;
    }
    if ('+-*/%=<>!(){};,.'.includes(c)) {
      tokens.push({ type: c, value: c, line });
      i++;
      continue;
    }
    if (c === '[' || c === ']') {
      throw new Error('Line ' + line + ': arrays are not supported by the lightweight browser preview compiler yet.');
    }
    throw new Error('Line ' + line + ': unexpected character ' + c);
  }
  tokens.push({ type: 'EOF', value: '', line });
  return tokens;
}

class Compiler {
  private tokens: Token[];
  private pos = 0;
  private code: Instruction[] = [];
  private vars = new Map<string, VarInfo>();

  constructor(source: string) { this.tokens = tokenize(source); }
  private peek(): Token { return this.tokens[this.pos]; }
  private take(): Token { return this.tokens[this.pos++]; }
  private match(type: string): boolean {
    if (this.peek().type !== type) return false;
    this.take();
    return true;
  }
  private need(type: string, message: string): Token {
    if (!this.match(type)) throw new Error('Line ' + this.peek().line + ': ' + message);
    return this.tokens[this.pos - 1];
  }
  private emit(op: string, arg?: Value | number): number {
    this.code.push(arg === undefined ? { op } : { op, arg });
    return this.code.length - 1;
  }
  private patch(at: number, target: number): void { this.code[at].arg = target; }

  compile(): Program {
    this.need('ROOM', 'program must start with Room');
    this.need('IDENT', 'expected Room name');
    this.need('{', 'expected { after Room name');
    this.need('PUBLIC', 'expected public static void main()');
    this.need('STATIC', 'expected static');
    this.need('VOID', 'expected void');
    this.need('MAIN', 'expected main');
    this.need('(', 'expected ( after main');
    this.need(')', 'main() takes no parameters');
    this.block();
    this.need('}', 'expected } to close Room');
    this.need('EOF', 'unexpected code after Room');
    this.emit('HALT');
    return { code: this.code, vars: [...this.vars.values()] };
  }

  private block(): void {
    this.need('{', 'expected { to start block');
    while (this.peek().type !== '}' && this.peek().type !== 'EOF') this.statement();
    this.need('}', 'expected } to close block');
  }

  private statement(): void {
    const t = this.peek();
    if (['INT', 'STRING', 'BOOLEAN'].includes(t.type)) {
      this.take();
      const name = this.need('IDENT', 'expected variable name');
      if (this.vars.has(name.value)) throw new Error('Line ' + name.line + ': variable already declared: ' + name.value);
      this.need('=', 'expected = in declaration');
      this.expression();
      this.need(';', 'expected ; after declaration');
      const info: VarInfo = { name: name.value, type: t.value as VarInfo['type'], slot: this.vars.size };
      this.vars.set(name.value, info);
      this.emit('STORE', info.slot);
      return;
    }
    if (t.type === 'IDENT' && (t.value === 'Game' || t.value === 'Chess')) { this.gameStatement(); return; }
    if (this.match('PRINT')) {
      this.need('(', 'expected ( after AL.print');
      this.expression();
      this.need(')', 'expected ) after print value');
      this.need(';', 'expected ; after print');
      this.emit('PRINT');
      return;
    }
    if (this.match('IF')) { this.ifStatement(); return; }
    if (this.match('WHILE')) {
      const start = this.code.length;
      this.need('(', 'expected ( after while');
      this.expression();
      this.need(')', 'expected ) after condition');
      const exit = this.emit('JMP_FALSE', 0);
      this.block();
      this.emit('JMP', start);
      this.patch(exit, this.code.length);
      return;
    }
    if (this.peek().type === 'IDENT') {
      const name = this.take();
      const info = this.vars.get(name.value);
      if (!info) throw new Error('Line ' + name.line + ': undeclared variable ' + name.value);
      this.need('=', 'expected = in assignment');
      this.expression();
      this.need(';', 'expected ; after assignment');
      this.emit('STORE', info.slot);
      return;
    }
    throw new Error('Line ' + t.line + ': invalid statement near ' + (t.value || 'end of file'));
  }

  private gameStatement(): void {
    const namespace = this.need('IDENT', 'expected Game or Chess');
    this.need('.', 'expected . after library name');
    const method = this.need('IDENT', 'expected Game method');
    this.need('(', 'expected ( after Game method');
    let argc = 0;
    if (this.peek().type !== ')') {
      do { this.expression(); argc++; } while (this.match(','));
    }
    this.need(')', 'expected ) after Game arguments');
    this.need(';', 'expected ; after Game call');
    this.emit('GAME_CALL', { name: namespace.value + '.' + method.value, argc });
  }

  private ifStatement(): void {
    this.need('(', 'expected ( after if');
    this.expression();
    this.need(')', 'expected ) after condition');
    const falseJump = this.emit('JMP_FALSE', 0);
    this.block();
    if (this.match('ELSE')) {
      const endJump = this.emit('JMP', 0);
      this.patch(falseJump, this.code.length);
      if (this.match('IF')) this.ifStatement();
      else this.block();
      this.patch(endJump, this.code.length);
    } else {
      this.patch(falseJump, this.code.length);
    }
  }

  private expression(): void { this.logicalOr(); }
  private logicalOr(): void {
    this.logicalAnd();
    while (this.match('||')) { this.logicalAnd(); this.emit('OR'); }
  }
  private logicalAnd(): void {
    this.compare();
    while (this.match('&&')) { this.compare(); this.emit('AND'); }
  }
  private compare(): void {
    this.add();
    while (['<', '>', '==', '!=', '<=', '>='].includes(this.peek().type)) {
      const op = this.take().type;
      this.add();
      this.emit(op === '<' ? 'LT' : op === '>' ? 'GT' : op === '==' ? 'EQ' : op === '!=' ? 'NE' : op === '<=' ? 'LE' : 'GE');
    }
  }
  private add(): void {
    this.mul();
    while (this.peek().type === '+' || this.peek().type === '-') {
      const op = this.take().type;
      this.mul();
      this.emit(op === '+' ? 'ADD' : 'SUB');
    }
  }
  private mul(): void {
    this.unary();
    while (['*', '/', '%'].includes(this.peek().type)) {
      const op = this.take().type;
      this.unary();
      this.emit(op === '*' ? 'MUL' : op === '/' ? 'DIV' : 'MOD');
    }
  }
  private unary(): void {
    if (this.match('-')) { this.unary(); this.emit('NEG'); return; }
    if (this.match('!')) { this.unary(); this.emit('NOT'); return; }
    this.primary();
  }
  private primary(): void {
    const t = this.peek();
    if (this.match('NUMBER')) { this.emit('PUSH', Number(t.value)); return; }
    if (this.match('STRING')) { this.emit('PUSH', t.value); return; }
    if (this.match('TRUE')) { this.emit('PUSH', true); return; }
    if (this.match('FALSE')) { this.emit('PUSH', false); return; }
    if (this.match('IDENT')) {
      const info = this.vars.get(t.value);
      if (!info) throw new Error('Line ' + t.line + ': undeclared variable ' + t.value);
      this.emit('LOAD', info.slot);
      return;
    }
    if (this.match('(')) { this.expression(); this.need(')', 'expected )'); return; }
    throw new Error('Line ' + t.line + ': expected a value');
  }
}

function runVM(code: Instruction[], vars: VarInfo[]) {
  const stack: Value[] = [];
  const memory: (Value | undefined)[] = [];
  const output: string[] = [];
  const game: GameScene = { width: 360, height: 560, background: '#101827', objects: [], buttons: [], controls: [] };
  let pc = 0;
  let steps = 0;
  const pop = (): Value => {
    const value = stack.pop();
    if (value === undefined) throw new Error('ALVM: stack underflow');
    return value;
  };
  while (pc < code.length) {
    if (++steps > 100000) throw new Error('ALVM: instruction limit reached');
    const ins = code[pc++];
    const arg = ins.arg;
    if (ins.op === 'PUSH') stack.push(arg as Value);
    else if (ins.op === 'LOAD') {
      const value = memory[Number(arg)];
      if (value === undefined) throw new Error('ALVM: variable used before initialization');
      stack.push(value);
    } else if (ins.op === 'STORE') {
      const value = pop();
      const info = vars[Number(arg)];
      if (!info) throw new Error('ALVM: invalid variable slot');
      const valid = info.type === 'int'
        ? typeof value === 'number' && Number.isInteger(value)
        : info.type === 'String' ? typeof value === 'string' : typeof value === 'boolean';
      if (!valid) throw new Error('ALVM: type error. ' + info.name + ' requires ' + info.type);
      memory[info.slot] = value;
    } else if (ins.op === 'PRINT') output.push(String(pop()));
    else if (ins.op === 'GAME_CALL') {
      const call = arg as { name: string; argc: number };
      const values = stack.splice(stack.length - call.argc, call.argc);
      const [a, b, c, d, e, f] = values;
      if (call.name === 'Chess.start' && values.length === 0) game.chess = true;
      else if (call.name === 'Game.start' && values.length === 2) { game.width = Number(a); game.height = Number(b); }
      else if (call.name === 'Game.background' && values.length === 1) game.background = String(a);
      else if (call.name === 'Game.rect' && values.length === 6) game.objects.push({ id: String(a), kind: 'rect', x: Number(b), y: Number(c), w: Number(d), h: Number(e), color: String(f) });
      else if (call.name === 'Game.circle' && values.length === 5) game.objects.push({ id: String(a), kind: 'circle', x: Number(b), y: Number(c), w: Number(d) * 2, h: Number(d) * 2, color: String(e) });
      else if (call.name === 'Game.text' && values.length === 5) game.objects.push({ id: 'text-' + game.objects.length, kind: 'text', x: Number(b), y: Number(c), w: 0, h: 0, color: String(d), text: String(a), size: Number(e) });
      else if (call.name === 'Game.button' && values.length === 2) game.buttons.push({ id: String(a), label: String(b) });
      else if (call.name === 'Game.control' && values.length === 4) game.controls.push({ button: String(a), target: String(b), dx: Number(c), dy: Number(d) });
      else throw new Error('ALVM: invalid Game.' + call.name + '() arguments or unsupported game command');
    }
    else if (ins.op === 'JMP') pc = Number(arg);
    else if (ins.op === 'JMP_FALSE') {
      const condition = pop();
      if (typeof condition !== 'boolean') throw new Error('ALVM: if/while condition must be boolean');
      if (!condition) pc = Number(arg);
    } else if (ins.op === 'NEG') {
      const a = pop();
      if (typeof a !== 'number') throw new Error('ALVM: unary minus requires a number');
      stack.push(-a);
    } else if (ins.op === 'NOT') {
      const a = pop();
      if (typeof a !== 'boolean') throw new Error('ALVM: ! requires a boolean');
      stack.push(!a);
    } else if (['ADD', 'SUB', 'MUL', 'DIV', 'MOD', 'LT', 'GT', 'EQ', 'NE', 'LE', 'GE', 'AND', 'OR'].includes(ins.op)) {
      const b = pop();
      const a = pop();
      let result: Value;
      if (ins.op === 'AND' || ins.op === 'OR') {
        if (typeof a !== 'boolean' || typeof b !== 'boolean') throw new Error('ALVM: logical operators require boolean values');
        result = ins.op === 'AND' ? a && b : a || b;
      } else if (ins.op === 'ADD' && (typeof a === 'string' || typeof b === 'string')) {
        result = String(a) + String(b);
      } else if (['EQ', 'NE'].includes(ins.op) && typeof a !== 'number' && typeof b !== 'number') {
        result = ins.op === 'EQ' ? a === b : a !== b;
      } else {
        if (typeof a !== 'number' || typeof b !== 'number') throw new Error('ALVM: operator ' + ins.op + ' requires numeric values');
        if (ins.op === 'ADD') result = a + b;
        else if (ins.op === 'SUB') result = a - b;
        else if (ins.op === 'MUL') result = a * b;
        else if (ins.op === 'DIV') {
          if (b === 0) throw new Error('ALVM: division by zero');
          result = Math.trunc(a / b);
        } else if (ins.op === 'MOD') {
          if (b === 0) throw new Error('ALVM: modulo by zero');
          result = a % b;
        } else if (ins.op === 'LT') result = a < b;
        else if (ins.op === 'GT') result = a > b;
        else if (ins.op === 'LE') result = a <= b;
        else if (ins.op === 'GE') result = a >= b;
        else if (ins.op === 'EQ') result = a === b;
        else result = a !== b;
      }
      if (typeof result === 'number' && !Number.isFinite(result)) throw new Error('ALVM: numeric overflow');
      stack.push(result);
    } else if (ins.op === 'HALT') {
      return {
        output,
        game,
        memory: vars.map(v => ({ name: v.name, type: v.type, value: memory[v.slot] ?? 'uninitialized' })),
        steps
      };
    } else {
      throw new Error('ALVM: unknown opcode ' + ins.op);
    }
  }
  throw new Error('ALVM: program ended without HALT');
}

function encodeRoom(program: Program): Blob {
  const payload = new TextEncoder().encode(JSON.stringify({ format: 'ALVM_ROOM', version: 1, ...program }));
  const header = new Uint8Array([65, 76, 86, 77, 1]);
  return new Blob([header, payload], { type: 'application/octet-stream' });
}
async function decodeRoom(file: File): Promise<Program> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (bytes.length < 6 || bytes[0] !== 65 || bytes[1] !== 76 || bytes[2] !== 86 || bytes[3] !== 77 || bytes[4] !== 1) {
    throw new Error('Invalid ALVM .room header or version');
  }
  const parsed = JSON.parse(new TextDecoder().decode(bytes.slice(5))) as { format: string; version: number; code: Instruction[]; vars: VarInfo[] };
  if (parsed.format !== 'ALVM_ROOM' || parsed.version !== 1 || !Array.isArray(parsed.code) || !Array.isArray(parsed.vars)) {
    throw new Error('Invalid .room bytecode structure');
  }
  const allowed = new Set(['PUSH', 'LOAD', 'STORE', 'PRINT', 'GAME_CALL', 'JMP', 'JMP_FALSE', 'NEG', 'NOT', 'ADD', 'SUB', 'MUL', 'DIV', 'MOD', 'LT', 'GT', 'EQ', 'NE', 'LE', 'GE', 'AND', 'OR', 'HALT']);
  if (parsed.code.some(item => !item || !allowed.has(item.op))) throw new Error('Invalid opcode in .room bytecode');
  return { code: parsed.code, vars: parsed.vars };
}

export default function App() {
  const [source, setSource] = useState(() => localStorage.getItem('alvm-source') || sample);
  const [filename, setFilename] = useState('Main.al');
  const [compiled, setCompiled] = useState<Program | null>(null);
  const [output, setOutput] = useState<string[]>(['ALVM Studio ready.', 'Edit Main.al, then press Run.']);
  const [appVersion, setAppVersion] = useState('Checking app version...');
  const [runtimeVersion, setRuntimeVersion] = useState('...');
  const [status, setStatus] = useState('READY');
  const [showReference, setShowReference] = useState(false);
  const [showLibraries, setShowLibraries] = useState(false);
  const [showWebsiteBuilder, setShowWebsiteBuilder] = useState(false);
  const [gameScene, setGameScene] = useState<GameScene | null>(null);
  const [activeControl, setActiveControl] = useState<string | null>(null);
  useEffect(() => {
    try {
      const installedVersion = window.Android?.getAppVersion?.();
      setAppVersion(installedVersion && installedVersion.trim()
        ? installedVersion.trim()
        : 'Web preview');
      const installedRuntime = window.Android?.getCiRuntimeVersion?.();
      setRuntimeVersion(installedRuntime && installedRuntime.trim()
        ? installedRuntime.trim()
        : 'unavailable');
    } catch {
      setAppVersion('Web preview');
      setRuntimeVersion('unavailable');
    }
    window.__alvmSetRuntimeVersion = version => setRuntimeVersion(version || 'unknown');
    return () => { delete window.__alvmSetRuntimeVersion; };
  }, []);
  useEffect(() => {
    if (!activeControl || !gameScene) return;
    const timer = window.setInterval(() => {
      setGameScene(previous => {
        if (!previous) return previous;
        const control = previous.controls.find(item => item.button === activeControl);
        if (!control) return previous;
        return { ...previous, objects: previous.objects.map(item => item.id === control.target ? { ...item, x: Math.max(0, Math.min(previous.width - item.w, item.x + control.dx * 4)), y: Math.max(0, Math.min(previous.height - item.h, item.y + control.dy * 4)) } : item) };
      });
    }, 35);
    return () => window.clearInterval(timer);
  }, [activeControl, gameScene?.controls]);
  const fileInput = useRef<HTMLInputElement>(null);
  const lineCount = useMemo(() => source.split('\n').length, [source]);
  const log = (lines: string[], nextStatus = 'READY') => { setOutput(lines); setStatus(nextStatus); };

  const compile = () => {
    try {
      const program = new Compiler(source).compile();
      setCompiled(program);
      log(['✓ AL compilation successful', 'Bytecode instructions: ' + program.code.length, 'Variables: ' + program.vars.length, 'Ready for ALVM execution.'], 'COMPILED');
      return program;
    } catch (error) {
      setCompiled(null);
      setGameScene(null);
      setActiveControl(null);
      log(['✕ ' + (error as Error).message], 'COMPILE ERROR');
      return null;
    }
  };
  const run = () => {
    const program = compile();
    if (!program) return;
    try {
      const result = runVM(program.code, program.vars);
      setGameScene(result.game);
      log(['✓ Program completed in ' + result.steps + ' VM steps.', '', ...result.output.map(line => 'AL OUTPUT  ' + line), '', 'MEMORY', ...result.memory.map(item => item.name + ' (' + item.type + ') = ' + item.value)], 'SUCCESS');
    } catch (error) { log(['✕ ' + (error as Error).message], 'RUNTIME ERROR'); }
  };
  const compileRoom = () => {
    const program = compile();
    if (!program) return;
    const blob = encodeRoom(program);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename.replace(/\.al$/i, '') + '.room';
    anchor.click();
    URL.revokeObjectURL(url);
    log(['✓ Compiled AL bytecode.', 'Saved file: ' + anchor.download, 'Format: ALVM_ROOM v1'], 'COMPILED');
  };
  const packageAl = () => {
    const createPackage = window.Android?.createAlPackage;
    if (!createPackage) {
      log(['✕ .AL export requires the ALVM Studio Android host.'], 'ERROR');
      return;
    }
    const appName = filename.replace(/\.al$/i, '') || 'ALApp';
    createPackage(source, appName);
    log(['Preparing .AL package with the native AL compiler.', 'The compiled .room v10 module and manifest will be saved using Android document picker.'], 'READY');
  };
  const openFile = async (file: File) => {
    try {
      if (file.name.toLowerCase().endsWith('.room')) {
        const program = await decodeRoom(file);
        setCompiled(program);
        setFilename(file.name);
        log(['✓ Loaded ' + file.name, 'Bytecode instructions: ' + program.code.length, 'Press Execute .room to run.'], 'LOADED');
      } else {
        setSource(await file.text());
        setFilename(file.name);
        setCompiled(null);
        log(['Opened source file: ' + file.name], 'READY');
      }
    } catch (error) { log(['✕ ' + (error as Error).message], 'ERROR'); }
  };
  const executeRoom = () => {
    if (!compiled) { log(['✕ No compiled .room file is loaded.'], 'ERROR'); return; }
    try {
      const result = runVM(compiled.code, compiled.vars);
      setGameScene(result.game);
      log(['✓ .room execution complete.', ...result.output.map(line => 'AL OUTPUT  ' + line), '', 'MEMORY', ...result.memory.map(item => item.name + ' = ' + item.value)], 'SUCCESS');
    } catch (error) { log(['✕ ' + (error as Error).message], 'RUNTIME ERROR'); }
  };
  const saveSource = () => {
    const blob = new Blob([source], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename.endsWith('.al') ? filename : filename + '.al';
    anchor.click();
    URL.revokeObjectURL(url);
  };
  const newFile = () => {
    setSource('Room Main {\n    public static void main() {\n        int value = 42;\n        AL.print(value);\n    }\n}');
    setFilename('Untitled.al');
    setCompiled(null);
    log(['New AL source file created.']);
  };
  const openFileManager = () => {
    if (window.Android?.openWorkspace) {
      window.Android.openWorkspace();
      return;
    }
    fileInput.current?.click();
  };
  const importFile = () => {
    if (window.Android?.importFile) {
      window.Android.importFile();
      return;
    }
    fileInput.current?.click();
  };
  const openRuntimeManager = () => {
    if (window.Android?.showCiRuntimeManager) {
      window.Android.showCiRuntimeManager();
      return;
    }
    log(['CI Runtime Manager is available in the installed Android app.'], 'READY');
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-icon"><Braces size={22} /></div>
          <div><b>ALVM <span>STUDIO</span></b><small>APP VERSION · {appVersion}</small></div>
        </div>
        <div className="top-actions">
          <span className="online"><Activity size={14} /> LOCAL VM ENGINE</span>
          <button className="icon-button" onClick={() => { setShowReference(!showReference); setShowLibraries(false); }} title="Language reference"><BookOpen size={18} /></button>
        </div>
      </header>
      <main className="layout">
        <aside className="sidebar" aria-label="ALVM Studio navigation">
          <div className="section-label">PROJECT</div>
          <div className="file-item active"><FileCode2 size={16} /><span>{filename}</span></div>
          <div className="section-label tools-label">WORKSPACE</div>
          <button className="side-button" onClick={newFile} title="Create a new AL file"><Code2 size={18} /><span>New file</span></button>
          <button className="side-button" onClick={() => { setShowLibraries(true); setShowReference(false); setShowWebsiteBuilder(false); }} title="Open AL libraries"><Boxes size={18} /><span>Libraries</span></button>
          <button className="side-button" onClick={openRuntimeManager} title="Open CI Runtime Manager"><Activity size={18} /><span>Runtime</span></button>
          <button className="side-button" onClick={openFileManager} title="Open project File Manager"><FolderOpen size={18} /><span>File Manager</span></button>
          <button className="side-button" onClick={importFile} title="Import an AL, ROOM or runtime file"><Upload size={18} /><span>File Importer</span></button>
          <button className="side-button" onClick={() => { setShowWebsiteBuilder(true); setShowLibraries(false); setShowReference(false); }} title="Open Website Builder"><Globe size={18} /><span>Website Builder</span></button>
          <div className="info-card">
            <b><Activity size={15} /> VM STATUS</b>
            <p><span>Architecture</span><strong>Verified bytecode</strong></p>
            <p><span>Source</span><strong>.al</strong></p>
            <p><span>Compiled</span><strong>.room v9</strong></p>
            <p><span>App</span><strong>{appVersion}</strong></p>
            <p><span>Engine</span><strong>CI Runtime {runtimeVersion}</strong></p>
          </div>
          <div className="sidebar-foot"><i />Local app execution</div>
        </aside>
        <section className="workspace">
          <div className="editor-head">
            <div><FileCode2 size={15} />{filename}</div>
            <div className="file-actions">
              <button onClick={saveSource}><Save size={14} />Save .al</button>
              <button onClick={() => fileInput.current?.click()}><Upload size={14} />Open</button>
            </div>
          </div>
          {showWebsiteBuilder ? <WebsiteBuilder /> : showLibraries ? <Libraries onOpenWebsite={() => { setShowWebsiteBuilder(true); setShowLibraries(false); }} /> : showReference ? (
            <div className="reference">
              <h2>AL Language Reference</h2>
              <p><code>Room</code> replaces <code>class</code>. Every program starts at <code>public static void main()</code>.</p>
              <h3>Types and variables</h3>
              <pre>int score = 10;{'\n'}String title = "AL";{'\n'}boolean ready = true;</pre>
              <h3>Output</h3>
              <pre>AL.print("Score: " + score);</pre>
              <h3>Operators</h3>
              <pre>+  -  *  /  %  ==  !=  {'<'}  {'>'}  {'<='}  {'>='}  &&  ||  !</pre>
              <h3>Conditions and loops</h3>
              <pre>if (ready && score {'>'} 5) {'{'} AL.print(score); {'}'}{'\n'}else if (score == 5) {'{'} AL.print("five"); {'}'}{'\n'}else {'{'} AL.print("other"); {'}'}{'\n'}while (score {'>'} 0) {'{'} score = score - 1; {'}'}</pre>
              <h3>Chess game command</h3>
              <pre>Chess.start();</pre>
              <p>Launches the Studio's built-in touch chess preview. The current AL compiler exposes this as a built-in command; chess rules are handled by the Studio runtime, not by an AL-written chess engine.</p>
              <h3>Mobile game commands</h3>
              <pre>Game.start(360, 560);{'\n'}Game.background("#101827");{'\n'}Game.rect("player", 40, 420, 40, 40, "#55d6a7");{'\n'}Game.button("left", "◀");{'\n'}Game.button("right", "▶");{'\n'}Game.control("left", "player", -1, 0);{'\n'}Game.control("right", "player", 1, 0);</pre>
              <p>Game commands run in the browser game preview. Touch controls move the named object. The current runtime supports basic 2D shapes and held movement, not a full game engine yet. Use semicolons after statements. Blocks use braces.</p>
            </div>
          ) : (
            <div className="editor-area">
              <div className="line-numbers">{Array.from({ length: lineCount }, (_, index) => <div key={index}>{index + 1}</div>)}</div>
              <textarea aria-label="AL source editor" spellCheck={false} autoCapitalize="off" autoCorrect="off" value={source} onChange={event => { setSource(event.target.value); setCompiled(null); localStorage.setItem('alvm-source', event.target.value); }} />
            </div>
          )}
          <div className="action-bar">
            <div>
              <button className="run-button" onClick={run}><Play size={15} fill="currentColor" />Run</button>
              <button className="compile-button" onClick={compileRoom}><Braces size={15} />Compile .room</button>
              <button className="execute-button" onClick={executeRoom}><Terminal size={15} />Execute .room</button>
              <button className="package-button" onClick={packageAl}><Package size={15} />Package .AL</button>
            </div>
            <span>ALVM Studio · {appVersion}</span>
          </div>
          {gameScene?.chess && <ChessPlayer />}
          {gameScene && !gameScene.chess && <section style={{ padding: 14, borderTop: '1px solid #263044', background: '#0b111c' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}><b style={{ fontSize: 12 }}>🎮 AL GAME PLAYER</b><button className="icon-button" onClick={() => { setGameScene(null); setActiveControl(null); }}>Close game</button></div>
            <div style={{ width: 'min(100%, 360px)', margin: '0 auto' }}>
              <div style={{ position: 'relative', width: '100%', aspectRatio: `${gameScene.width} / ${gameScene.height}`, overflow: 'hidden', background: gameScene.background, border: '1px solid #46536b', borderRadius: 10, touchAction: 'none' }}>
                {gameScene.objects.map(item => <div key={item.id} style={{ position: 'absolute', left: `${item.x / gameScene.width * 100}%`, top: `${item.y / gameScene.height * 100}%`, width: item.kind === 'text' ? 'auto' : `${item.w / gameScene.width * 100}%`, height: item.kind === 'text' ? 'auto' : `${item.h / gameScene.height * 100}%`, background: item.kind === 'text' ? 'transparent' : item.color, borderRadius: item.kind === 'circle' ? '50%' : 3, color: item.color, fontSize: item.size || 16, fontWeight: 700, whiteSpace: 'pre' }}>{item.text}</div>)}
              </div>
              <div style={{ display: 'flex', justifyContent: 'center', flexWrap: 'wrap', gap: 10, paddingTop: 12 }}>
                {gameScene.buttons.map(button => <button key={button.id} onPointerDown={event => { event.preventDefault(); setActiveControl(button.id); }} onPointerUp={() => setActiveControl(null)} onPointerLeave={() => setActiveControl(null)} onPointerCancel={() => setActiveControl(null)} style={{ minWidth: 64, minHeight: 52, borderRadius: 12, border: '1px solid #6256a5', background: activeControl === button.id ? '#6555b8' : '#252342', color: 'white', fontSize: 19, touchAction: 'none', userSelect: 'none' }}>{button.label}</button>)}
              </div>
              <small style={{ display: 'block', color: '#8e9bb0', textAlign: 'center', marginTop: 8 }}>Touch and hold the controls to move your game objects.</small>
            </div>
          </section>}
          <section className="console">
            <div className="console-title">
              <div><Terminal size={15} /><b>OUTPUT CONSOLE</b><span className={status.includes('ERROR') || status === 'ERROR' ? 'status error' : status === 'SUCCESS' ? 'status success' : 'status'}>{status}</span></div>
              <button onClick={() => setOutput([])} title="Clear console"><X size={15} /></button>
            </div>
            <div className="console-output">{output.map((line, index) => <div key={index} className={line.startsWith('✕') ? 'error-line' : line.startsWith('✓') ? 'success-line' : line.startsWith('AL OUTPUT') ? 'program-output' : ''}>{line || ' '}</div>)}</div>
          </section>
        </section>
      </main>
      <footer><span>ALVM STUDIO · APP VERSION {appVersion}</span><span>{lineCount} lines · UTF-8</span></footer>
      <input ref={fileInput} type="file" accept=".al,.room,.txt" hidden onChange={event => { const file = event.target.files?.[0]; if (file) void openFile(file); event.currentTarget.value = ''; }} />
    </div>
  );
}
