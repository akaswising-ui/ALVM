import { useMemo, useState } from 'react';
import { ArrowDown, Download, Eye, Plus, Trash2, ChevronUp, ChevronDown } from 'lucide-react';

type BlockType = 'navbar' | 'hero' | 'about' | 'features' | 'gallery' | 'contact' | 'footer';
type Block = { id: number; type: BlockType; title: string; text: string };

const templates: Record<BlockType, Block> = {
  navbar: { id: 0, type: 'navbar', title: 'My Website', text: 'Home, About, Services, Contact' },
  hero: { id: 0, type: 'hero', title: 'Build something remarkable.', text: 'A responsive website made with the AL Website Development Library.' },
  about: { id: 0, type: 'about', title: 'About us', text: 'Write a short introduction about your project, team or idea.' },
  features: { id: 0, type: 'features', title: 'What we offer', text: 'Fast and simple|Responsive design|Easy to customize' },
  gallery: { id: 0, type: 'gallery', title: 'Our gallery', text: 'Add your images and project highlights here.' },
  contact: { id: 0, type: 'contact', title: 'Contact us', text: 'Send us a message using the form below.' },
  footer: { id: 0, type: 'footer', title: 'My Website', text: '© 2026 My Website. All rights reserved.' }
};

const escapeHtml = (value: string) => value.replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char] || char));

function renderBlock(block: Block, primary: string): string {
  const title = escapeHtml(block.title);
  const text = escapeHtml(block.text);
  if (block.type === 'navbar') return '<header class="nav"><b>' + title + '</b><nav>' + block.text.split(',').map(item => '<a href="#">' + escapeHtml(item.trim()) + '</a>').join('') + '</nav></header>';
  if (block.type === 'hero') return '<section class="hero"><div><p class="eyebrow">WELCOME</p><h1>' + title + '</h1><p>' + text + '</p><a class="cta" href="#contact">Explore</a></div></section>';
  if (block.type === 'about') return '<section class="section"><h2>' + title + '</h2><p>' + text + '</p></section>';
  if (block.type === 'features') return '<section class="section"><h2>' + title + '</h2><div class="cards">' + block.text.split('|').map(item => '<article><h3>' + escapeHtml(item.trim()) + '</h3><p>Describe this feature for your visitors.</p></article>').join('') + '</div></section>';
  if (block.type === 'gallery') return '<section class="section"><h2>' + title + '</h2><div class="gallery"><div>Image / project 1</div><div>Image / project 2</div><div>Image / project 3</div></div><p>' + text + '</p></section>';
  if (block.type === 'contact') return '<section class="section" id="contact"><h2>' + title + '</h2><p>' + text + '</p><form><input placeholder="Your name" required><input type="email" placeholder="Email address" required><textarea placeholder="Your message"></textarea><button type="button">Send message</button></form></section>';
  return '<footer class="footer"><b>' + title + '</b><p>' + text + '</p></footer>';
}

export default function WebsiteBuilder() {
  const [siteName, setSiteName] = useState('My AL Website');
  const [primary, setPrimary] = useState('#6d5ce8');
  const [blocks, setBlocks] = useState<Block[]>([
    { ...templates.navbar, id: 1 },
    { ...templates.hero, id: 2 },
    { ...templates.about, id: 3 },
    { ...templates.features, id: 4 },
    { ...templates.contact, id: 5 },
    { ...templates.footer, id: 6 }
  ]);
  const [selected, setSelected] = useState(2);
  const [showPreview, setShowPreview] = useState(true);
  const nextId = useMemo(() => Math.max(0, ...blocks.map(block => block.id)) + 1, [blocks]);
  const page = '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>' + escapeHtml(siteName) + '</title><style>*{box-sizing:border-box}body{margin:0;font-family:Inter,Arial,sans-serif;color:#172033;line-height:1.6}.nav{display:flex;justify-content:space-between;align-items:center;padding:20px 7%;background:#fff;position:sticky;top:0}.nav nav{display:flex;gap:22px;flex-wrap:wrap}.nav a{color:#344054;text-decoration:none}.hero{padding:100px 8%;background:linear-gradient(135deg,' + primary + '22,#f6f7fb);min-height:360px;display:flex;align-items:center}.hero h1{font-size:clamp(36px,6vw,64px);line-height:1.08;max-width:760px;margin:12px 0}.hero p{max-width:650px;color:#596579}.eyebrow{color:' + primary + ';font-weight:700;letter-spacing:2px}.cta,form button{display:inline-block;background:' + primary + ';color:white;padding:12px 22px;border-radius:9px;text-decoration:none;border:0}.section{padding:64px 8%;max-width:1200px;margin:auto}.section h2{font-size:32px}.section>p{max-width:760px;color:#596579}.cards,.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:18px}.cards article,.gallery div{padding:24px;border:1px solid #e4e7ec;border-radius:14px;background:#fff}.gallery div{min-height:130px;display:grid;place-items:center;background:#f6f7fb;color:#697586}form{display:grid;gap:12px;max-width:520px;margin-top:20px}input,textarea{padding:13px;border:1px solid #d0d5dd;border-radius:8px;font:inherit}textarea{min-height:110px}.footer{padding:32px 8%;background:#101828;color:white}.footer p{color:#cbd5e1}@media(max-width:620px){.nav{align-items:flex-start;gap:12px;flex-direction:column}.nav nav{gap:12px}.hero{padding:70px 7%}}</style></head><body>' + blocks.map(block => renderBlock(block, primary)).join('') + '</body></html>';
  const addBlock = (type: BlockType) => {
    const block = { ...templates[type], id: nextId };
    setBlocks(previous => [...previous, block]);
    setSelected(nextId);
  };
  const updateSelected = (field: 'title' | 'text', value: string) => setBlocks(previous => previous.map(block => block.id === selected ? { ...block, [field]: value } : block));
  const removeSelected = () => {
    setBlocks(previous => previous.filter(block => block.id !== selected));
    setSelected(blocks.find(block => block.id !== selected)?.id || 0);
  };
  const moveSelected = (direction: -1 | 1) => {
    const index = blocks.findIndex(block => block.id === selected);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= blocks.length) return;
    const next = [...blocks];
    [next[index], next[target]] = [next[target], next[index]];
    setBlocks(next);
  };
  const selectedBlock = blocks.find(block => block.id === selected);
  const download = () => {
    const blob = new Blob([page], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'index.html';
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <section className="website-builder">
      <div className="website-heading">
        <div><span className="library-eyebrow">AL WEBSITE DEVELOPMENT LIBRARY</span><h2>Website Builder</h2><p>Add sections, edit their content, preview the page and export an HTML website.</p></div>
        <button className="builder-primary" onClick={download}><Download size={16} /> Export website</button>
      </div>
      <div className="website-toolbar">
        <label>Website name<input value={siteName} onChange={event => setSiteName(event.target.value)} /></label>
        <label>Theme color<input type="color" value={primary} onChange={event => setPrimary(event.target.value)} /></label>
        <button className="builder-secondary" onClick={() => setShowPreview(value => !value)}><Eye size={15} />{showPreview ? 'Hide preview' : 'Show preview'}</button>
      </div>
      <div className="website-layout">
        <aside className="website-components">
          <b>ADD COMPONENTS</b>
          {(Object.keys(templates) as BlockType[]).map(type => (
            <button key={type} onClick={() => addBlock(type)}><Plus size={15} />{type === 'navbar' ? 'Navigation' : type.charAt(0).toUpperCase() + type.slice(1)}</button>
          ))}
          <p>Components are inserted into your page. Select any section to edit it.</p>
        </aside>
        <div className="website-workarea">
          <div className="website-section-list">
            <div className="website-list-head"><b>PAGE SECTIONS ({blocks.length})</b><span>Tap a section to edit</span></div>
            {blocks.map((block, index) => (
              <div key={block.id} className={selected === block.id ? 'website-block active' : 'website-block'}>
                <button className="website-block-select" onClick={() => setSelected(block.id)}><span>{index + 1}. {block.type.toUpperCase()}</span><b>{block.title}</b></button>
                <div className="website-block-actions">
                  <button onClick={() => moveSelected(-1)} disabled={index === 0 || selected !== block.id} aria-label="Move section up"><ChevronUp size={15} /></button>
                  <button onClick={() => moveSelected(1)} disabled={index === blocks.length - 1 || selected !== block.id} aria-label="Move section down"><ChevronDown size={15} /></button>
                  <button onClick={() => { setSelected(block.id); setBlocks(previous => previous.filter(item => item.id !== block.id)); }} aria-label="Remove section"><Trash2 size={15} /></button>
                </div>
              </div>
            ))}
          </div>
          <div className="website-editor">
            <b>EDIT SELECTED COMPONENT</b>
            {selectedBlock ? <>
              <label>Heading<input value={selectedBlock.title} onChange={event => updateSelected('title', event.target.value)} /></label>
              <label>Text / links / items<textarea value={selectedBlock.text} onChange={event => updateSelected('text', event.target.value)} rows={4} /></label>
              {selectedBlock.type === 'features' && <small>Separate feature cards with the | character.</small>}
            </> : <p>Select a section from the page list.</p>}
            {selectedBlock && <button className="builder-danger" onClick={removeSelected}><Trash2 size={15} />Remove selected</button>}
          </div>
        </div>
      </div>
      {showPreview && <div className="website-preview"><div className="website-list-head"><b><Eye size={15} /> LIVE WEBSITE PREVIEW</b><span>Updates as you edit</span></div><iframe title="Website live preview" sandbox="allow-forms" srcDoc={page} /></div>}
    </section>
  );
}
