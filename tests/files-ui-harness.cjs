// Reusable DOM/fetch/XHR doubles for wwwroot/files.js UI regression tests.
// boot() loads the real wwwroot/files.js in a fresh vm context; tests drive it
// via emit(element, event) and inspect elements/created/timers.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
class Element {
  constructor(tag = 'div') {
    this.tagName = tag.toUpperCase(); this.style = {}; this.value = '';
    this.disabled = false; this.children = []; this.listeners = {};
    this._text = ''; this.className = ''; this.files = [];
    const classes = new Set();
    this.classList = {add: x => classes.add(x), remove: x => classes.delete(x)};
  }
  set textContent(value) { this._text = String(value); this.children = []; }
  get textContent() { return this._text + this.children.map(x => x.textContent).join(''); }
  set innerHTML(value) {
    if (value !== '') throw new Error('Harness only supports clearing HTML');
    this._text = ''; this.children = [];
  }
  get innerHTML() { return this.textContent; }
  appendChild(child) { this.children.push(child); child.parent = this; return child; }
  remove() {
    if (this.parent) this.parent.children = this.parent.children.filter(x => x !== this);
  }
  addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
  focus() { this.focused = true; }
  setAttribute(name, value) { this[name] = String(value); }
  click() { return this.fire('click'); }
  async fire(type, extra = {}) {
    if (this.disabled) return;
    const event = {preventDefault() {}, stopPropagation() {}, target:this, ...extra};
    for (const fn of this.listeners[type] || []) await fn(event);
  }
}
class FakeXHR {
  static instances = [];
  constructor() {
    this.upload = {}; this.headers = {}; this.status = 0; this.responseText = '';
    FakeXHR.instances.push(this);
  }
  open(method, url) { this.method = method; this.url = url; }
  setRequestHeader(name, value) { this.headers[name] = value; }
  send(body) { this.body = body; this.sent = true; }
  abort() { this.aborted = true; this.onabort?.(); }
}
async function boot(options) {
  FakeXHR.instances = [];
  const elements = {}; const created = []; const timers = [];
  const root = path.resolve(__dirname, '..');
  const html = fs.readFileSync(path.join(root, 'wwwroot/files.html'), 'utf8');
  for (const match of html.matchAll(/\bid="([^"]+)"/g)) elements[match[1]] = new Element();
  const document = {
    getElementById: id => elements[id] || null,
    createElement: tag => { const e = new Element(tag); created.push(e); return e; },
    createTextNode: text => { const e = new Element('#text'); e.textContent = text; return e; }
  };
  const settle = async () => {
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));
  };
  const window = {location:{href:''}};
  vm.runInNewContext(fs.readFileSync(path.join(root, 'wwwroot/files.js'), 'utf8'), {
    document, fetch: options.fetch, XMLHttpRequest: options.XMLHttpRequest || FakeXHR,
    window, URLSearchParams, AbortController, console,
    setTimeout:fn => { timers.push(fn); return timers.length; }, clearTimeout() {}
  }, {filename:'wwwroot/files.js'});
  await settle();
  return {elements, created, timers, settle, window,
    emit: async (element, type, extra) => {
      if (!element) throw new Error(`Missing element for ${type}`);
      await element.fire(type, extra); await settle();
    }};
}
module.exports = {boot, FakeXHR};
