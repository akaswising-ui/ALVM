import { useMemo, useState } from 'react';
import {
  Boxes, Code2, Cpu, Database, FileCode2, Folder, Gamepad2, Globe,
  Image, Layers, Network, ShieldCheck, Plus, Check, ExternalLink, Volume2, TerminalSquare
} from 'lucide-react';

type Entry = { id: string; title: string; description: string; snippet: string; category?: string };
type Module = {
  id: string; name: string; group: string; status: string; detail: string;
  entries: Entry[];
};

const padCodeId = (value: number) => String(value).padStart(4, '0');

function createAlCodeOptions(): Entry[] {
  const options: Entry[] = [];
  const add = (category: string, index: number, title: string, description: string, snippet: string) => {
    options.push({
      id: 'al-' + category.toLowerCase().replace(/[^a-z0-9]+/g, '-') + '-' + padCodeId(index),
      category,
      title,
      description,
      snippet
    });
  };

  // 1,000 scalar and inferred variable declarations.
  const variableTypes = ['int', 'string', 'boolean', 'float', 'double', 'var'] as const;
  for (let i = 0; i < 1000; i++) {
    const type = variableTypes[i % variableTypes.length];
    const name = 'value_' + padCodeId(i);
    const value = type === 'string' ? '"AL ' + i + '"'
      : type === 'boolean' ? (i % 2 === 0 ? 'true' : 'false')
      : type === 'float' ? ((i % 90) + 1) + '.5f'
      : type === 'double' ? ((i % 90) + 1) + '.75'
      : String(i % 100);
    add('Variables', i, type + ' declaration ' + padCodeId(i),
      'Scalar declaration using a type supported by the native compiler. Insert inside main().',
      type + ' ' + name + ' = ' + value + ';');
  }

  // 1,000 arithmetic expressions with matching numeric operand types.
  const numericTypes = ['int', 'float', 'double'] as const;
  for (let i = 0; i < 1000; i++) {
    const type = numericTypes[i % numericTypes.length];
    const left = (i % 40) + 2;
    const right = ((i * 7) % 17) + 1;
    const operators = type === 'int' ? ['+', '-', '*', '/', '%'] : ['+', '-', '*', '/'];
    const op = operators[Math.floor(i / numericTypes.length) % operators.length];
    const literal = (n: number) => type === 'float' ? n + '.5f' : type === 'double' ? n + '.25' : String(n);
    const name = 'calculation_' + padCodeId(i);
    add('Arithmetic', i, type + ' expression ' + padCodeId(i),
      'Strict same-type arithmetic. Mixed float/double operations are intentionally not generated.',
      type + ' ' + name + ' = ' + literal(left) + ' ' + op + ' ' + literal(right) + ';\nAL.print(' + name + ');');
  }

  // 1,000 sized-array declarations for the five implemented array element types.
  const arrayTypes = ['int', 'string', 'boolean', 'float', 'double'] as const;
  for (let i = 0; i < 1000; i++) {
    const type = arrayTypes[i % arrayTypes.length];
    const name = 'items_' + padCodeId(i);
    const size = (i % 20) + 1;
    add('Arrays', i, type + ' array ' + padCodeId(i),
      'Sized array allocation and .length access, supported by the native bytecode runtime.',
      type + '[] ' + name + ' = new ' + type + '[' + size + '];\nAL.print(' + name + '.length);');
  }

  // 1,000 if/else examples using typed boolean conditions.
  const comparisons = ['==', '!=', '>', '<', '>=', '<='];
  for (let i = 0; i < 1000; i++) {
    const name = 'conditionValue_' + padCodeId(i);
    const left = i % 100;
    const right = (i * 3) % 100;
    const op = comparisons[i % comparisons.length];
    add('Conditions', i, 'if/else condition ' + padCodeId(i),
      'A complete conditional statement. Insert inside main() or a function body.',
      'int ' + name + ' = ' + left + ';\nif (' + name + ' ' + op + ' ' + right + ') {\n    AL.print("condition true");\n} else {\n    AL.print("condition false");\n}');
  }

  // 1,000 loop examples: for, while and do/while.
  for (let i = 0; i < 1000; i++) {
    const name = 'loopIndex_' + padCodeId(i);
    const limit = (i % 12) + 1;
    const kind = i % 3;
    let snippet: string;
    if (kind === 0) {
      snippet = 'for (int ' + name + ' = 0; ' + name + ' < ' + limit + '; ' + name + '++) {\n    AL.print(' + name + ');\n}';
    } else if (kind === 1) {
      snippet = 'int ' + name + ' = ' + limit + ';\nwhile (' + name + ' > 0) {\n    AL.print(' + name + ');\n    ' + name + '--;\n}';
    } else {
      snippet = 'int ' + name + ' = ' + limit + ';\ndo {\n    AL.print(' + name + ');\n    ' + name + '--;\n} while (' + name + ' > 0);';
    }
    add('Loops', i, ['for loop ', 'while loop ', 'do/while loop '][kind] + padCodeId(i),
      'Executable loop syntax with a finite counter. Insert inside main() or a function body.',
      snippet);
  }

  // 1,000 switch examples with explicit case exits.
  for (let i = 0; i < 1000; i++) {
    const name = 'choice_' + padCodeId(i);
    const selected = i % 3;
    add('Switch', i, 'switch statement ' + padCodeId(i),
      'Integer switch with cases, break and default. Case bodies do not rely on accidental fall-through.',
      'int ' + name + ' = ' + selected + ';\nswitch (' + name + ') {\n    case 0:\n        AL.print("zero");\n        break;\n    case 1:\n        AL.print("one");\n        break;\n    default:\n        AL.print("other");\n}');
  }

  // 1,000 global function definitions. These belong at program scope, outside main().
  for (let i = 0; i < 1000; i++) {
    const suffix = padCodeId(i);
    const left = 'left_' + suffix;
    const right = 'right_' + suffix;
    const name = 'addValues_' + suffix;
    add('Functions', i, 'typed function ' + suffix,
      'Global function definition. Place at program scope, outside main().',
      'int ' + name + '(int ' + left + ', int ' + right + ') {\n    return ' + left + ' + ' + right + ';\n}');
  }

  // 1,000 class/object patterns using implemented fields, constructors and methods.
  for (let i = 0; i < 1000; i++) {
    const suffix = padCodeId(i);
    const cls = 'Record_' + suffix;
    add('Classes and objects', i, 'class with constructor ' + suffix,
      'Class, field, constructor, this-field access and instance method. Place at program scope.',
      'class ' + cls + ' {\n    int value;\n\n    ' + cls + '(int initialValue) {\n        this.value = initialValue;\n    }\n\n    int getValue() {\n        return this.value;\n    }\n}');
  }

  // 1,000 AL.print examples, using real scalar literals and the existing output call.
  for (let i = 0; i < 1000; i++) {
    const kind = i % 4;
    const value = kind === 0 ? '"AL output ' + i + '"'
      : kind === 1 ? String(i)
      : kind === 2 ? (i % 2 === 0 ? 'true' : 'false')
      : ((i % 90) + 1) + '.5f';
    add('Output', i, 'AL.print example ' + padCodeId(i),
      'Uses the implemented AL.print runtime call. Insert inside a function body.',
      'AL.print(' + value + ');');
  }

  // 1,000 native UI calls only. No browser-only Game.* or Chess.* preview shortcuts.
  for (let i = 0; i < 1000; i++) {
    const suffix = padCodeId(i);
    const kind = i % 5;
    let snippet: string;
    if (kind === 0) snippet = 'UI.screen("AL App ' + i + '");\nUI.text("status_' + suffix + '", "Ready");';
    else if (kind === 1) snippet = 'UI.game("AL Game ' + i + '");\nUI.rect("ground_' + suffix + '", 0, 400, 320, 80, 4278255360);';
    else if (kind === 2) snippet = 'UI.game("AL Shapes ' + i + '");\nUI.circle("player_' + suffix + '", 80, 120, 18, 4294901760);';
    else if (kind === 3) snippet = 'UI.setInt("score_' + suffix + '", ' + (i % 500) + ');\nAL.print(UI.getInt("score_' + suffix + '", 0));';
    else snippet = 'UI.screen("AL Screen ' + i + '");\nUI.text("message_' + suffix + '", "Hello from AL");';
    add('Native UI and 2D', i, 'native UI pattern ' + suffix,
      'Uses UI calls implemented by the native AL compiler and CI Runtime. Insert inside main().',
      snippet);
  }

  return options;
}

const generatedCodeOptions = createAlCodeOptions();
if (generatedCodeOptions.length !== 10000) throw new Error('AL Code Options catalog must contain exactly 10,000 entries.');

const modules: Module[] = [
  {
    id: 'code-options', name: '10,000 AL Code Options', group: 'AL language',
    status: 'Available',
    detail: '10,000 searchable AL source snippets generated from syntax and runtime calls already implemented by the native compiler. These are code patterns, not 10,000 new built-in APIs.',
    entries: generatedCodeOptions
  },

  {
    id: 'compiler', name: 'AL Compiler', group: 'Language core', status: 'Prototype',
    detail: 'Compile supported AL source into .room bytecode.',
    entries: [
      { id: 'compile', title: 'Compile source', description: 'Compiler entry point proposal.', snippet: 'AL.compile("Main.al");' },
      { id: 'diagnostics', title: 'Compiler diagnostics', description: 'Read compile errors and warnings.', snippet: 'ALCompiler.showDiagnostics();' }
    ]
  },
  {
    id: 'vm', name: 'AL Virtual Machine', group: 'Language core', status: 'Prototype',
    detail: 'Load and execute compiled AL bytecode.',
    entries: [
      { id: 'load', title: 'Load .room file', description: 'Load a compiled AL program.', snippet: 'ALVM.load("Main.room");' },
      { id: 'execute', title: 'Execute program', description: 'Start the loaded bytecode.', snippet: 'ALVM.execute();' }
    ]
  },
  {
    id: 'standard', name: 'Standard Library', group: 'Core libraries', status: 'Planned',
    detail: 'Reusable tools for text, maths, collections, dates, random values and conversions.',
    entries: [
      { id: 'math', title: 'Math utilities', description: 'Common numeric functions.', snippet: 'Math.sqrt(144);\nMath.round(4.6);\nMath.max(10, 20);' },
      { id: 'strings', title: 'String utilities', description: 'Text length, search and replacement.', snippet: 'String.length("Hello");\nString.upper("hello");\nString.contains("AL language", "AL");' },
      { id: 'collections', title: 'Collections', description: 'List and map data structures.', snippet: 'List numbers = List.create();\nnumbers.add(10);\nnumbers.add(20);' },
      { id: 'date', title: 'Date and time', description: 'Date and clock helpers.', snippet: 'Date.today();\nTime.now();' }
    ]
  },
  {
    id: 'website', name: 'Website Development Library', group: 'Web development', status: 'Builder available',
    detail: 'Build responsive websites from reusable sections, navigation, forms, cards, images and themes.',
    entries: [
      { id: 'navbar', title: 'Navigation bar', description: 'Responsive site navigation.', snippet: 'Web.navbar("My Website");' },
      { id: 'hero', title: 'Hero section', description: 'Headline, text and call-to-action.', snippet: 'Web.hero("Welcome", "Build with AL");' },
      { id: 'cards', title: 'Feature cards', description: 'Reusable content cards.', snippet: 'Web.cards(["Fast", "Simple", "Responsive"]);' },
      { id: 'contact', title: 'Contact form', description: 'Name, email and message fields.', snippet: 'Web.contactForm();' },
      { id: 'footer', title: 'Footer', description: 'Site footer with links.', snippet: 'Web.footer("© My Website");' }
    ]
  },
  {
    id: 'graphics', name: 'Graphics Library', group: 'Creative tools', status: 'Planned',
    detail: '2D shapes, colors, paths, canvas and image rendering.',
    entries: [
      { id: 'canvas', title: 'Canvas', description: 'Create a drawing surface.', snippet: 'Graphics.canvas(800, 600);' },
      { id: 'shapes', title: 'Shapes', description: 'Draw circles, rectangles and lines.', snippet: 'Graphics.circle(120, 120, 50, "purple");\nGraphics.rectangle(20, 20, 180, 90, "blue");' },
      { id: 'image', title: 'Image renderer', description: 'Place an image in a scene.', snippet: 'Graphics.image("assets/photo.png", 40, 40);' }
    ]
  },
  {
    id: 'filesystem', name: 'File System Library', group: 'System libraries', status: 'Planned',
    detail: 'Project file reading, writing and folders with permission-aware access.',
    entries: [
      { id: 'read', title: 'Read file', description: 'Read a permitted project file.', snippet: 'File.read("notes.txt");' },
      { id: 'write', title: 'Write file', description: 'Save text to a project file.', snippet: 'File.write("notes.txt", "Hello AL");' },
      { id: 'folders', title: 'Folders', description: 'Create and list project folders.', snippet: 'File.createFolder("assets");\nFile.list("assets");' }
    ]
  },
  {
    id: 'network', name: 'Networking Library', group: 'System libraries', status: 'Planned',
    detail: 'HTTP requests, JSON, network status and safe async operations.',
    entries: [
      { id: 'http', title: 'HTTP request', description: 'Request data from a web API.', snippet: 'Network.get("https://example.com/data");' },
      { id: 'json', title: 'JSON tools', description: 'Parse and create JSON data.', snippet: 'JSON.parse(response);\nJSON.stringify(data);' }
    ]
  },
  {
    id: 'gui', name: 'GUI Framework', group: 'Creative tools', status: 'Planned',
    detail: 'Desktop and app interfaces, layouts, controls and events.',
    entries: [
      { id: 'window', title: 'Window', description: 'Create an application window.', snippet: 'GUI.window("My App", 800, 600);' },
      { id: 'button', title: 'Button', description: 'Add a clickable button.', snippet: 'GUI.button("Start");' },
      { id: 'input', title: 'Text input', description: 'Add an input field.', snippet: 'GUI.textInput("Your name");' }
    ]
  },
  {
    id: 'game', name: 'Game Development Library', group: 'Creative tools', status: 'Planned',
    detail: 'Graphics and game APIs are not implemented in the native ALVM runtime yet.',
    entries: [
      { id: 'chess', title: 'Chess preview', description: 'Chess support is planned for a future native AL runtime module.', snippet: 'Chess.start();' },
      { id: 'start', title: 'Start 2D game', description: 'Planned API. Not available in the current native ALVM runtime.', snippet: 'Game.start(360, 560);\nGame.background("#101827");' },
      { id: 'shapes', title: 'Game shapes', description: 'Draw a player and game objects.', snippet: 'Game.rect("player", 40, 420, 40, 40, "#55d6a7");\nGame.circle("coin", 180, 180, 14, "#ffd166");' },
      { id: 'touch', title: 'Touch controls', description: 'Add phone buttons and map them to object movement.', snippet: 'Game.button("left", "◀");\nGame.button("right", "▶");\nGame.control("left", "player", -1, 0);\nGame.control("right", "player", 1, 0);' },
      { id: 'text', title: 'Game text', description: 'Show a title or score label.', snippet: 'Game.text("MY GAME", 12, 18, "#ffffff", 18);' }
    ]
  },
  {
    id: 'audio', name: 'AL.Audio', group: 'System libraries', status: 'Planned',
    detail: 'Native audio playback, sound effects, volume control and audio resource management. These APIs remain planned until the AL runtime and Android host implement and test them.',
    entries: [
      { id: 'play', title: 'Play audio', description: 'Play an audio asset from the application package.', snippet: 'Audio.play("assets/sound.ogg");' },
      { id: 'effect', title: 'Sound effect', description: 'Play a short sound effect.', snippet: 'Audio.playEffect("assets/click.wav");' },
      { id: 'volume', title: 'Set volume', description: 'Set the application audio volume.', snippet: 'Audio.setVolume(0.7);' },
      { id: 'stop', title: 'Stop playback', description: 'Stop the currently playing audio resource.', snippet: 'Audio.stop();' }
    ]
  },
  {
    id: 'system', name: 'AL.System', group: 'System libraries', status: 'Planned',
    detail: 'Platform information, app lifecycle, permissions and system capability access. Operations will be permission-scoped and implemented by the native runtime.',
    entries: [
      { id: 'platform', title: 'Platform information', description: 'Read the operating system and device platform.', snippet: 'System.platform();' },
      { id: 'device', title: 'Device information', description: 'Read permitted device capability information.', snippet: 'System.deviceInfo();' },
      { id: 'permission', title: 'Request permission', description: 'Request a capability from the Android host.', snippet: 'System.requestPermission("camera");' },
      { id: 'exit', title: 'Exit application', description: 'Request a normal application exit.', snippet: 'System.exit();' }
    ]
  },
  {
    id: 'database', name: 'Database Library', group: 'Data libraries', status: 'Planned',
    detail: 'Structured storage, tables, queries, indexes and persistence.',
    entries: [
      { id: 'open', title: 'Open database', description: 'Connect to a project database.', snippet: 'Database.open("project.db");' },
      { id: 'table', title: 'Create table', description: 'Define a data table.', snippet: 'Database.table("students").create();' },
      { id: 'query', title: 'Query data', description: 'Read matching records.', snippet: 'Database.query("SELECT * FROM students");' }
    ]
  }
];

type ProjectItem = { key: string; module: string; title: string; snippet: string };

export default function Libraries({ onOpenWebsite }: { onOpenWebsite: () => void }) {
  const [selected, setSelected] = useState('code-options');
  const [added, setAdded] = useState<string[]>([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All');
  const [page, setPage] = useState(0);
  const active = modules.find(item => item.id === selected) || modules[0];
  const categories = useMemo(
    () => ['All', ...Array.from(new Set(active.entries.map(item => item.category).filter(Boolean) as string[])).sort()],
    [active]
  );
  const entries = useMemo(() => active.entries.filter(item =>
    (category === 'All' || item.category === category)
    && (item.title + ' ' + item.description + ' ' + item.snippet).toLowerCase().includes(query.toLowerCase())
  ), [active, query, category]);
  const pageSize = 48;
  const pageCount = Math.max(1, Math.ceil(entries.length / pageSize));
  const visibleEntries = entries.slice(page * pageSize, (page + 1) * pageSize);
  const addEntry = (entry: Entry) => {
    const key = active.id + ':' + entry.id;
    const current: ProjectItem[] = JSON.parse(localStorage.getItem('alvm-project-library-items') || '[]');
    if (!current.some(item => item.key === key)) {
      current.push({ key, module: active.name, title: entry.title, snippet: entry.snippet });
      localStorage.setItem('alvm-project-library-items', JSON.stringify(current));
    }
    setAdded(previous => previous.includes(key) ? previous : [...previous, key]);
  };
  const allItems: ProjectItem[] = JSON.parse(localStorage.getItem('alvm-project-library-items') || '[]');

  return (
    <section className="library-page">
      <div className="library-heading">
        <div>
          <span className="library-eyebrow">AL LANGUAGE ECOSYSTEM</span>
          <h2>Library Store</h2>
          <p>Choose a module, add reusable components to your project, and build with them.</p>
        </div>
        <div className="library-count"><Boxes size={17} /> {active.id === 'code-options' ? '10,000 CODE OPTIONS' : modules.length + ' MODULES'}</div>
      </div>
      <div className="library-layout">
        <nav className="library-list" aria-label="AL modules">
          {modules.map(module => (
            <button key={module.id} className={selected === module.id ? 'library-item selected' : 'library-item'} onClick={() => { setSelected(module.id); setQuery(''); setCategory('All'); setPage(0); }}>
              <span>{module.id === 'website' ? <Globe size={17} /> : module.id === 'standard' ? <Boxes size={17} /> : module.id === 'graphics' ? <Image size={17} /> : module.id === 'filesystem' ? <Folder size={17} /> : module.id === 'network' ? <Network size={17} /> : module.id === 'gui' ? <Layers size={17} /> : module.id === 'game' ? <Gamepad2 size={17} /> : module.id === 'database' ? <Database size={17} /> : module.id === 'audio' ? <Volume2 size={17} /> : module.id === 'system' ? <TerminalSquare size={17} /> : module.id === 'vm' ? <Cpu size={17} /> : <Code2 size={17} />}</span>
              <span>{module.name}</span>
              <small>{module.id === 'code-options' ? '10K CODE' : module.status === 'Planned' ? 'ROADMAP' : module.status === 'Prototype' ? 'CORE' : 'BUILDER'}</small>
            </button>
          ))}
        </nav>
        <article className="library-detail">
          <div className="library-detail-icon"><Globe size={24} /></div>
          <span className="library-eyebrow">{active.group}</span>
          <h3>{active.name}</h3>
          <span className={active.status === 'Planned' ? 'module-status planned' : active.status === 'Available' ? 'module-status available' : 'module-status prototype'}>{active.status}</span>
          <p>{active.detail}</p>
          {active.id === 'website' && <button className="builder-primary" onClick={onOpenWebsite}><ExternalLink size={16} /> Open Website Builder</button>}
          <div className="component-toolbar">
            <b>{active.id === 'code-options' ? 'Searchable AL code patterns' : 'Available components'}</b>
            {active.id === 'code-options' && (
              <select value={category} onChange={event => { setCategory(event.target.value); setPage(0); }} aria-label="Filter code category">
                {categories.map(item => <option key={item} value={item}>{item}</option>)}
              </select>
            )}
            <input value={query} onChange={event => { setQuery(event.target.value); setPage(0); }} placeholder={active.id === 'code-options' ? 'Search 10,000 code options...' : 'Search components...'} aria-label="Search components" />
          </div>
          {active.id === 'code-options' && (
            <div className="code-options-summary">
              <b>{entries.length.toLocaleString()} matching snippets</b>
              <span>Page {page + 1} of {pageCount} · Showing {entries.length === 0 ? 0 : page * pageSize + 1}-{Math.min((page + 1) * pageSize, entries.length)}</span>
            </div>
          )}
          <div className="component-grid">
            {visibleEntries.map(entry => {
              const key = active.id + ':' + entry.id;
              const isAdded = added.includes(key) || allItems.some(item => item.key === key);
              return (
                <div className="component-card" key={key}>
                  <div><small className="component-category">{entry.category || active.name}</small><b>{entry.title}</b><p>{entry.description}</p></div>
                  <pre>{entry.snippet}</pre>
                  <button onClick={() => addEntry(entry)} className={isAdded ? 'added-button' : 'add-button'}>{isAdded ? <Check size={15} /> : <Plus size={15} />}{isAdded ? 'Added to project' : 'Add to project'}</button>
                </div>
              );
            })}
            {entries.length === 0 && <p>No matching components.</p>}
          </div>
          {active.id === 'code-options' && pageCount > 1 && (
            <div className="library-pagination">
              <button onClick={() => setPage(0)} disabled={page === 0}>First</button>
              <button onClick={() => setPage(value => Math.max(0, value - 1))} disabled={page === 0}>Previous</button>
              <button onClick={() => setPage(value => Math.min(pageCount - 1, value + 1))} disabled={page >= pageCount - 1}>Next</button>
              <button onClick={() => setPage(pageCount - 1)} disabled={page >= pageCount - 1}>Last</button>
            </div>
          )}
          <div className="module-note"><ShieldCheck size={16} /> {active.id === 'code-options'
            ? 'These 10,000 entries are generated source patterns for syntax already supported by the native AL compiler. They do not add new keywords, runtime APIs, libraries or VM instructions.'
            : 'Components are saved in this browser project. Planned library APIs are starter snippets, not executable AL features yet.'}</div>
        </article>
      </div>
      <section className="project-library-items">
        <h3><Folder size={17} /> Added to this project ({allItems.length})</h3>
        {allItems.length === 0 ? <p>No components added yet. Select a component above.</p> : allItems.map(item => (
          <div className="project-library-row" key={item.key}><div><b>{item.title}</b><small>{item.module}</small></div><code>{item.snippet}</code></div>
        ))}
        <button className="add-button" onClick={() => {
          const text = allItems.map(item => '// ' + item.module + ': ' + item.title + '\n' + item.snippet).join('\n\n');
          const blob = new Blob([text], { type: 'text/plain' });
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = 'AL-Library-Snippets.txt';
          anchor.click();
          URL.revokeObjectURL(url);
        }} disabled={allItems.length === 0}><FileCode2 size={15} /> Export library snippets</button>
      </section>
    </section>
  );
}
