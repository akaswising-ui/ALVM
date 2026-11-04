import { useEffect, useMemo, useRef, useState } from 'react';
import Libraries from './Libraries';
import WebsiteBuilder from './WebsiteBuilder';
import './builder.css';
import {
  Activity, BookOpen, Braces, Boxes, Code2, FileCode2, FolderOpen, Globe,
  Play, Save, Terminal, Upload, X
} from 'lucide-react';

type NativeBridge = {
  runSource: (source: string, name: string) => string;
  compileSource: (source: string, name: string) => string;
  executeRoom: () => string;
  saveSource: (source: string, name: string) => string;
  openWorkspace: () => void;
  createNewFile: () => void;
};

declare global {
  interface Window {
    Android?: NativeBridge;
    __alvmShowOutput?: (message: string, status: string) => void;
  }
}

const sample = \`// AL program compiled and executed by the native C++ ALVM.
room Main {
    public static void main() {
        int a = 10;
        int b = 20;
        int result = a + b;
        AL.print(result);
    }
}\`;

function statusFor(message: string, fallback: string): string {
  const lower = message.toLowerCase();
  if (message.includes('✕') || lower.includes('error') || lower.includes('failed')) return 'ERROR';
  if (lower.includes('compiled')) return 'COMPILED';
  if (lower.includes('success') || lower.includes('completed')) return 'SUCCESS';
  return fallback;
}

export default function App() {
  const [source, setSource] = useState(() => localStorage.getItem('alvm-source') || sample);
  const [filename, setFilename] = useState('Main.al');
  const [output, setOutput] = useState<string[]>(['ALVM native engine ready.', 'Edit Main.al, then press Run.']);
  const [status, setStatus] = useState('READY');
  const [showReference, setShowReference] = useState(false);
  const [showLibraries, setShowLibraries] = useState(false);
  const [showWebsiteBuilder, setShowWebsiteBuilder] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const lineCount = useMemo(() => source.split('\n').length, [source]);

  const log = (message: string, fallback = 'READY') => {
    setOutput((message || '(no output)').split(/\r?\n/));
    setStatus(statusFor(message || '', fallback));
  };

  useEffect(() => {
    window.__alvmShowOutput = (message, nextStatus) => {
      setOutput((message || '(no output)').split(/\r?\n/));
      setStatus(nextStatus || statusFor(message || '', 'READY'));
    };
    return () => { delete window.__alvmShowOutput; };
  }, []);

  const run = () => {
    if (!window.Android) return log('✕ Native ALVM bridge is unavailable.', 'ERROR');
    log(window.Android.runSource(source, filename), 'SUCCESS');
  };

  const compileRoom = () => {
    if (!window.Android) return log('✕ Native ALVM bridge is unavailable.', 'ERROR');
    log(window.Android.compileSource(source, filename), 'COMPILED');
  };

  const executeRoom = () => {
    if (!window.Android) return log('✕ Native ALVM bridge is unavailable.', 'ERROR');
    log(window.Android.executeRoom(), 'SUCCESS');
  };

  const saveSource = () => {
    if (!window.Android) return log('✕ Native ALVM bridge is unavailable.', 'ERROR');
    log(window.Android.saveSource(source, filename), 'READY');
  };

  const newFile = () => {
    if (!window.Android) return log('✕ Native file manager is unavailable.', 'ERROR');
    window.Android.createNewFile();
  };

  const openWorkspace = () => {
    if (!window.Android) {
      fileInput.current?.click();
      return;
    }
    window.Android.openWorkspace();
  };

  const openFile = async (file: File) => {
    try {
      if (file.name.toLowerCase().endsWith('.room')) {
        log('Bytecode file selected. Use the native workspace import to load and verify .room files.', 'READY');
        return;
      }
      const text = await file.text();
      setSource(text);
      setFilename(file.name.toLowerCase().endsWith('.al') ? file.name : file.name + '.al');
      localStorage.setItem('alvm-source', text);
      log('Opened source file: ' + file.name);
    } catch (error) {
      log('✕ Could not open file: ' + (error as Error).message, 'ERROR');
    }
  };

  const updateSource = (value: string) => {
    setSource(value);
    localStorage.setItem('alvm-source', value);
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-icon"><Braces size={22} /></div>
          <div><b>ALVM <span>STUDIO</span></b><small>AL Virtual Machine · Offline</small></div>
        </div>
        <div className="top-actions">
          <span className="online"><Activity size={14} /> NATIVE ALVM ENGINE</span>
          <button className="icon-button" onClick={() => { setShowReference(!showReference); setShowLibraries(false); setShowWebsiteBuilder(false); }} title="Language reference"><BookOpen size={18} /></button>
        </div>
      </header>

      <main className="layout">
        <aside className="sidebar">
          <div className="section-label">PROJECT</div>
          <div className="file-item active"><FileCode2 size={16} /><span>{filename}</span></div>
          <div className="section-label tools-label">WORKSPACE</div>
          <button className="side-button" onClick={newFile}><Code2 size={16} />New file</button>
          <button className="side-button" onClick={() => { setShowLibraries(true); setShowReference(false); setShowWebsiteBuilder(false); }}><Boxes size={16} />AL Libraries</button>
          <button className="side-button" onClick={() => { setShowWebsiteBuilder(true); setShowLibraries(false); setShowReference(false); }}><Globe size={16} />Website Builder</button>
          <button className="side-button" onClick={openWorkspace}><FolderOpen size={16} />Open file</button>
          <div className="info-card">
            <b><Activity size={15} /> VM STATUS</b>
            <p><span>Architecture</span><strong>Stack bytecode</strong></p>
            <p><span>Source</span><strong>.al</strong></p>
            <p><span>Compiled</span><strong>.room</strong></p>
            <p><span>Version</span><strong>v0.4</strong></p>
            <p><span>Engine</span><strong>Native C++17</strong></p>
          </div>
          <div className="sidebar-foot"><i />On-device execution</div>
        </aside>

        <section className="workspace">
          <div className="editor-head">
            <div><FileCode2 size={15} />{filename}</div>
            <div className="file-actions">
              <button onClick={saveSource}><Save size={14} />Save .al</button>
              <button onClick={openWorkspace}><Upload size={14} />Open</button>
            </div>
          </div>

          {showWebsiteBuilder ? (
            <WebsiteBuilder />
          ) : showLibraries ? (
            <Libraries onOpenWebsite={() => { setShowWebsiteBuilder(true); setShowLibraries(false); }} />
          ) : showReference ? (
            <div className="reference">
              <h2>AL Language Reference</h2>
              <p>The source is compiled by the native AL compiler and executed by the native ALVM. The current language subset is documented in the project repository.</p>
              <h3>Basic program</h3>
              <pre>{\`room Main {
    public static void main() {
        int score = 10;
        AL.print(score);
    }
}\`}</pre>
              <h3>Supported features in the current engine</h3>
              <pre>int, float, double, long, short, byte, boolean, char, string
Arrays · functions · recursion · function overloading
Classes · fields · objects · constructors · inheritance
if / else · switch · while · do-while · for · foreach
Arithmetic · comparisons · logical operators</pre>
              <h3>Build and execution</h3>
              <pre>.al source → native compiler → .room bytecode → verifier → ALVM</pre>
              <p>Graphics, game APIs, interfaces, dynamic dispatch, exceptions, networking and the .AL application package are not available in the current native runtime yet.</p>
            </div>
          ) : (
            <div className="editor-area">
              <div className="line-numbers">{Array.from({ length: lineCount }, (_, index) => <div key={index}>{index + 1}</div>)}</div>
              <textarea aria-label="AL source editor" spellCheck={false} autoCapitalize="off" autoCorrect="off" value={source} onChange={event => updateSource(event.target.value)} />
            </div>
          )}

          <div className="action-bar">
            <div>
              <button className="run-button" onClick={run}><Play size={15} fill="currentColor" />Run</button>
              <button className="compile-button" onClick={compileRoom}><Braces size={15} />Compile .room</button>
              <button className="execute-button" onClick={executeRoom}><Terminal size={15} />Execute .room</button>
            </div>
            <span>ALVM · NATIVE</span>
          </div>

          <section className="console">
            <div className="console-title">
              <div><Terminal size={15} /><b>OUTPUT CONSOLE</b><span className={status.includes('ERROR') || status === 'ERROR' ? 'status error' : status === 'SUCCESS' ? 'status success' : 'status'}>{status}</span></div>
              <button onClick={() => { setOutput([]); setStatus('READY'); }} title="Clear console"><X size={15} /></button>
            </div>
            <div className="console-output">{output.map((line, index) => <div key={index} className={line.startsWith('✕') ? 'error-line' : line.startsWith('✓') ? 'success-line' : line.startsWith('AL OUTPUT') ? 'program-output' : ''}>{line || ' '}</div>)}</div>
          </section>
        </section>
      </main>

      <footer><span>ALVM STUDIO · NATIVE BYTECODE RUNTIME</span><span>{lineCount} lines · UTF-8 · OFFLINE</span></footer>
      <input ref={fileInput} type="file" accept=".al,.room,.txt" hidden onChange={event => { const file = event.target.files?.[0]; if (file) void openFile(file); event.currentTarget.value = ''; }} />
    </div>
  );
}
