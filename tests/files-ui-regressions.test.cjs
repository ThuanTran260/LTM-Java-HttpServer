// UI regression tests: drive the real wwwroot/files.js against DOM/fetch doubles.
const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, FakeXHR } = require('./files-ui-harness.cjs');
const json = (status, body) => ({status, ok: status >= 200 && status < 300,
  statusText: String(status), json: async () => body});

test('initial signed-out visit does not announce an expired session', async () => {
  const ui = await boot({fetch: async () => json(401, {})});
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['status-region'].textContent, '');
});

test('late initial session response cannot discard a successful login', async () => {
  let resolveSession;
  const ui = await boot({fetch: async url => {
    if (url.endsWith('/session')) return new Promise(resolve => { resolveSession = resolve; });
    if (url.endsWith('/login')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'new'});
    return json(200, {path:'/',parent:null,offset:0,total:0,entries:[]});
  }});
  ui.elements.username.value = 'admin';
  ui.elements.password.value = 'password';
  await ui.emit(ui.elements['login-form'], 'submit');
  resolveSession(json(401, {}));
  await ui.settle();
  assert.equal(ui.elements['explorer-view'].style.display, 'block');
  assert.equal(ui.elements['lbl-user'].textContent, 'admin');
  assert.equal(ui.elements['status-region'].textContent, '');
});

test('401 during authenticated listing still asks the user to log in again', async () => {
  const ui = await boot({fetch: async url => {
    if (url.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    return json(401, {});
  }});
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.match(ui.elements['status-region'].textContent, /Phiên làm việc đã hết hạn/);
});
test('failed logout keeps explorer and does not claim success', async () => {
  const ui = await boot({fetch: async url => {
    if (url.endsWith('/session')) return json(200, {user:'guest',role:'GUEST',csrfToken:'csrf'});
    if (url.endsWith('/logout')) return json(403, {message:'Forbidden'});
    return json(200, {path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await ui.emit(ui.elements['btn-logout'], 'click');
  assert.equal(ui.elements['explorer-view'].style.display, 'block');
  assert.doesNotMatch(ui.elements['status-region'].textContent, /Đã đăng xuất/);
});

const fileEntry = (name, path) => ({name, path, directory:false, size:0, modified:null, blocked:false});
const dirEntry = (name, path) => ({name, path, directory:true, size:null, modified:null, blocked:false});
const manyFiles = (from, count) =>
  Array.from({length:count}, (_, i) => fileEntry(`f${from + i}.txt`, `/f${from + i}.txt`));

test('next page requests offset 200 and exposes entry 201', async () => {
  const calls = [];
  const ui = await boot({fetch: async raw => {
    const u = new URL(raw, 'http://localhost'); calls.push(u);
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    const offset = Number(u.searchParams.get('offset') || 0);
    const entries = offset === 200 ? [{name:'last.txt',path:'/last.txt',directory:false,
      size:0,modified:null,blocked:false}] : Array.from({length:200}, (_,i) => ({
      name:`f${i}.txt`,path:`/f${i}.txt`,directory:false,size:0,modified:null,blocked:false}));
    return json(200, {path:'/',parent:null,offset,limit:200,total:201,entries});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.equal(calls.at(-1).searchParams.get('offset'), '200');
  assert.equal(ui.elements['file-list-body'].children.length, 1);
  assert.equal(ui.elements['btn-next-page'].disabled, true);
  assert.equal(ui.elements['btn-prev-page'].disabled, false);
  assert.match(ui.elements['page-info'].textContent, /201/);
});

test('empty directory shows 0 mục and disables pagination', async () => {
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    return json(200, {path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  assert.ok(ui.elements['page-info'], 'page-info control exists');
  assert.equal(ui.elements['page-info'].textContent, '0 mục');
  assert.equal(ui.elements['btn-prev-page'].disabled, true);
  assert.equal(ui.elements['btn-next-page'].disabled, true);
});

test('exactly 200 items fill one page and disable both controls', async () => {
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    return json(200, {path:'/',parent:null,offset:0,limit:200,total:200,entries:manyFiles(0,200)});
  }});
  assert.ok(ui.elements['page-info'], 'page-info control exists');
  assert.equal(ui.elements['page-info'].textContent, '1–200 / 200');
  assert.equal(ui.elements['btn-prev-page'].disabled, true);
  assert.equal(ui.elements['btn-next-page'].disabled, true);
});

test('401 items expose three pages with exact labels and previous navigation', async () => {
  const offsets = [];
  const ui = await boot({fetch: async raw => {
    const u = new URL(raw, 'http://localhost');
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    const offset = Number(u.searchParams.get('offset') || 0);
    offsets.push(offset);
    const entries = offset === 400 ? manyFiles(400,1) : manyFiles(offset,200);
    return json(200, {path:'/',parent:null,offset,limit:200,total:401,entries});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  assert.equal(ui.elements['page-info'].textContent, '1–200 / 401');
  assert.equal(ui.elements['btn-prev-page'].disabled, true);
  assert.equal(ui.elements['btn-next-page'].disabled, false);
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.equal(offsets.at(-1), 200);
  assert.equal(ui.elements['page-info'].textContent, '201–400 / 401');
  assert.equal(ui.elements['btn-prev-page'].disabled, false);
  assert.equal(ui.elements['btn-next-page'].disabled, false);
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.equal(offsets.at(-1), 400);
  assert.equal(ui.elements['file-list-body'].children.length, 1);
  assert.equal(ui.elements['page-info'].textContent, '401–401 / 401');
  assert.equal(ui.elements['btn-next-page'].disabled, true);
  await ui.emit(ui.elements['btn-prev-page'], 'click');
  assert.equal(offsets.at(-1), 200);
  assert.equal(ui.elements['page-info'].textContent, '201–400 / 401');
  await ui.emit(ui.elements['btn-prev-page'], 'click');
  assert.equal(offsets.at(-1), 0);
  assert.equal(ui.elements['page-info'].textContent, '1–200 / 401');
  assert.equal(ui.elements['btn-prev-page'].disabled, true);
});

test('folder, breadcrumb and parent navigation reset offset while refresh preserves it', async () => {
  const calls = [];
  const ui = await boot({fetch: async raw => {
    const u = new URL(raw, 'http://localhost');
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    const path = u.searchParams.get('path');
    const offset = Number(u.searchParams.get('offset') || 0);
    calls.push({path, offset});
    if (path === '/sub/deep') return json(200, {path:'/sub/deep',parent:'/sub',offset:0,limit:200,total:0,entries:[]});
    if (path === '/sub') return json(200, {path:'/sub',parent:'/',offset:0,limit:200,total:1,entries:[dirEntry('deep','/sub/deep')]});
    if (offset === 200) return json(200, {path:'/',parent:null,offset:200,limit:200,total:201,entries:[dirEntry('sub','/sub')]});
    return json(200, {path:'/',parent:null,offset:0,limit:200,total:201,entries:manyFiles(0,200)});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.deepEqual(calls.at(-1), {path:'/', offset:200});
  assert.equal(ui.elements['page-info'].textContent, '201–201 / 201');

  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.deepEqual(calls.at(-1), {path:'/', offset:200}, 'refresh preserves offset');
  assert.equal(ui.elements['page-info'].textContent, '201–201 / 201');

  await ui.emit(ui.created.filter(e => e.className === 'item-link' && e.textContent === 'sub').at(-1), 'click');
  assert.deepEqual(calls.at(-1), {path:'/sub', offset:0}, 'folder click resets offset');
  assert.equal(ui.elements['page-info'].textContent, '1–1 / 1');

  await ui.emit(ui.created.filter(e => e.className === 'item-link' && e.textContent === 'deep').at(-1), 'click');
  assert.deepEqual(calls.at(-1), {path:'/sub/deep', offset:0});
  assert.equal(ui.elements['page-info'].textContent, '0 mục');

  await ui.emit(ui.created.filter(e => e.className === 'crumb-link' && e.textContent === 'sub').at(-1), 'click');
  assert.deepEqual(calls.at(-1), {path:'/sub', offset:0}, 'breadcrumb click resets offset');
  assert.equal(ui.elements['page-info'].textContent, '1–1 / 1');

  await ui.emit(ui.elements['btn-parent'], 'click');
  assert.deepEqual(calls.at(-1), {path:'/', offset:0}, 'parent click resets offset');
  assert.equal(ui.elements['page-info'].textContent, '1–200 / 201');
});

test('deleting the sole item of the last page refetches the last valid page once', async () => {
  let deleted = false;
  const listingOffsets = [];
  const ui = await boot({fetch: async (raw, options = {}) => {
    const u = new URL(raw, 'http://localhost');
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    if (options.method === 'DELETE') { deleted = true; return json(204, {}); }
    const offset = Number(u.searchParams.get('offset') || 0);
    listingOffsets.push(offset);
    const total = deleted ? 400 : 401;
    if (offset === 400) return json(200, {path:'/',parent:null,offset,limit:200,total,
      entries: deleted ? [] : [fileEntry('solo.txt', '/solo.txt')]});
    if (offset === 200) return json(200, {path:'/',parent:null,offset,limit:200,total,entries:manyFiles(200,200)});
    return json(200, {path:'/',parent:null,offset:0,limit:200,total,entries:manyFiles(0,200)});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.equal(ui.elements['page-info'].textContent, '401–401 / 401');
  const beforeDelete = listingOffsets.length;
  await ui.emit(ui.created.filter(e => e.textContent === 'Xóa').at(-1), 'click');
  await ui.emit(ui.elements['modal-btn-confirm'], 'click');
  await ui.settle();
  assert.deepEqual(listingOffsets.slice(beforeDelete), [400, 200],
    'delete refetches current offset, then the last valid page once');
  assert.equal(listingOffsets.slice(beforeDelete).filter(o => o === 200).length, 1);
  assert.equal(ui.elements['page-info'].textContent, '201–400 / 400');
  assert.equal(ui.elements['file-list-body'].children.length, 200);
  assert.equal(ui.elements['btn-prev-page'].disabled, false);
  assert.equal(ui.elements['btn-next-page'].disabled, true);
});

test('late delete-correction response cannot replace the page after a refresh', async () => {
  let deleted = false;
  let releaseFirstCorrection;
  const firstCorrectionGate = new Promise(resolve => { releaseFirstCorrection = resolve; });
  let correctionRequests = 0;
  const ui = await boot({fetch: async (raw, options = {}) => {
    const u = new URL(raw, 'http://localhost');
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    if (options.method === 'DELETE') { deleted = true; return json(204, {}); }
    const offset = Number(u.searchParams.get('offset') || 0);
    const total = deleted ? 400 : 401;
    if (offset === 400) return json(200, {path:'/',parent:null,offset,limit:200,total,
      entries: deleted ? [] : [fileEntry('solo.txt', '/solo.txt')]});
    if (offset === 200) {
      if (deleted && ++correctionRequests === 1) {
        await firstCorrectionGate;
        return json(200, {path:'/',parent:null,offset,limit:200,total:400,
          entries:[fileEntry('WRONG.txt', '/WRONG.txt')]});
      }
      return json(200, {path:'/',parent:null,offset,limit:200,total,entries:manyFiles(200,200)});
    }
    return json(200, {path:'/',parent:null,offset:0,limit:200,total,entries:manyFiles(0,200)});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  await ui.emit(ui.created.filter(e => e.textContent === 'Xóa').at(-1), 'click');
  await ui.emit(ui.elements['modal-btn-confirm'], 'click');
  await ui.settle();
  assert.equal(correctionRequests, 1, 'delete correction requested');
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.equal(correctionRequests, 2, 'refresh triggers its own single correction');
  assert.equal(ui.elements['page-info'].textContent, '201–400 / 400');
  releaseFirstCorrection();
  await ui.settle();
  assert.equal(ui.elements['file-list-body'].children.length, 200, 'stale correction ignored');
  assert.equal(ui.elements['page-info'].textContent, '201–400 / 400');
  assert.doesNotMatch(ui.elements['file-list-body'].textContent, /WRONG\.txt/);
});

test('failed fetch restores pagination state from the last accepted page', async () => {
  let mode = 'ok';
  const ui = await boot({fetch: async raw => {
    const u = new URL(raw, 'http://localhost');
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    if (mode === 'reject') throw new Error('network down');
    if (mode === 'fail') return json(500, {message:'boom'});
    const offset = Number(u.searchParams.get('offset') || 0);
    const entries = offset === 200 ? manyFiles(200,1) : manyFiles(0,200);
    return json(200, {path:'/',parent:null,offset,limit:200,total:201,entries});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.equal(ui.elements['page-info'].textContent, '201–201 / 201');
  assert.equal(ui.elements['btn-prev-page'].disabled, false);
  assert.equal(ui.elements['btn-next-page'].disabled, true);

  mode = 'fail';
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.match(ui.elements['status-region'].textContent, /Lỗi tải danh mục/);
  assert.equal(ui.elements['page-info'].textContent, '201–201 / 201');
  assert.equal(ui.elements['file-list-body'].children.length, 1);
  assert.equal(ui.elements['btn-prev-page'].disabled, false, 'previous restored from last accepted page');
  assert.equal(ui.elements['btn-next-page'].disabled, true, 'next restored from last accepted page');

  mode = 'reject';
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.match(ui.elements['status-region'].textContent, /Lỗi kết nối máy chủ/);
  assert.equal(ui.elements['page-info'].textContent, '201–201 / 201');
  assert.equal(ui.elements['btn-prev-page'].disabled, false);
  assert.equal(ui.elements['btn-next-page'].disabled, true);
});

test('stale listing response delivered last cannot replace the visible page', async () => {
  let releaseStale;
  const staleGate = new Promise(resolve => { releaseStale = resolve; });
  const calls = [];
  const ui = await boot({fetch: async raw => {
    const u = new URL(raw, 'http://localhost');
    if (u.pathname.endsWith('/session')) return json(200, {user:'admin',role:'ADMIN',csrfToken:'c'});
    calls.push(u);
    const offset = Number(u.searchParams.get('offset') || 0);
    if (offset === 200) {
      await staleGate;
      return json(200, {path:'/stale-dir',parent:'/',offset:200,limit:200,total:201,
        entries:[fileEntry('last.txt','/stale-dir/last.txt')]});
    }
    return json(200, {path:'/',parent:null,offset:0,limit:200,total:201,entries:manyFiles(0,200)});
  }});
  assert.ok(ui.elements['btn-next-page'], 'next page control exists');
  await ui.emit(ui.elements['btn-next-page'], 'click');
  assert.equal(ui.elements['btn-next-page'].disabled, true, 'controls disabled while loading');
  assert.equal(ui.elements['btn-prev-page'].disabled, true, 'controls disabled while loading');
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.equal(ui.elements['file-list-body'].children.length, 200);
  assert.equal(ui.elements['page-info'].textContent, '1–200 / 201');
  releaseStale();
  await ui.settle();
  assert.equal(ui.elements['file-list-body'].children.length, 200, 'stale page cannot replace current page');
  assert.equal(ui.elements['page-info'].textContent, '1–200 / 201');
  assert.equal(ui.elements['btn-prev-page'].disabled, true);
  assert.equal(ui.elements['btn-next-page'].disabled, false);
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.equal(calls.at(-1).searchParams.get('path'), '/', 'stale response cannot change the current path');
});

// ---------------------------------------------------------------------------
// Upload uncertainty & reconciliation (Task 4)
// ---------------------------------------------------------------------------

const putPaths = () => FakeXHR.instances
  .filter(x => x.method === 'PUT')
  .map(x => new URL(x.url, 'http://localhost').searchParams.get('path'));
async function upload(ui, ...names) {
  ui.elements['file-picker'].files = names.map(name => ({name, size:1}));
  await ui.emit(ui.elements['file-picker'], 'change');
}
const uncertainRow = ui => ui.created.some(e => e.textContent === 'Chưa xác định');
const adminSession = url => url.endsWith('/session')
  ? json(200, {user:'admin', role:'ADMIN', csrfToken:'c'}) : null;

test('aborted upload is uncertain and triggers directory reconciliation', async () => {
  const requests = [];
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    requests.push(url);
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  ui.elements['file-picker'].files = [{name:'a.txt',size:1}];
  await ui.emit(ui.elements['file-picker'], 'change');
  const before = requests.length;
  FakeXHR.instances.at(-1).abort(); await ui.settle();
  assert.ok(requests.length > before, 'refresh issued after abort');
  assert.ok(ui.created.some(e => /Chưa xác định/.test(e.textContent)));
  assert.ok(!ui.created.some(e => e.textContent === 'Đã hủy'));
});

test('abort after the server already published keeps the row uncertain and lists the file', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    const session = adminSession(url);
    if (session) return session;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  await upload(ui, 'a.txt');
  FakeXHR.instances.at(-1).abort(); await ui.settle();
  assert.ok(uncertainRow(ui), 'row stays uncertain even though the file is visible');
  assert.equal(ui.elements['status-region'].textContent,
    'Chưa xác định kết quả tải lên. Đang kiểm tra lại danh sách.');
  assert.match(ui.elements['file-list-body'].textContent, /a\.txt/, 'reconciled listing shows the file');
  await upload(ui, 'a.txt');
  assert.equal(FakeXHR.instances.length, 2, 'retry allowed after a fresh listing');
});

test('network error is uncertain and reconciles', async () => {
  const requests = [];
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    requests.push(url);
    const session = adminSession(url);
    if (session) return session;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt');
  const before = requests.length;
  FakeXHR.instances.at(-1).onerror(); await ui.settle();
  assert.ok(requests.length > before, 'refresh issued after network error');
  assert.ok(uncertainRow(ui), 'network error row is uncertain');
  assert.equal(ui.elements['status-region'].textContent,
    'Chưa xác định kết quả tải lên. Đang kiểm tra lại danh sách.');
});

test('upload timeout is uncertain and reconciles', async () => {
  const requests = [];
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    requests.push(url);
    const session = adminSession(url);
    if (session) return session;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt');
  const before = requests.length;
  FakeXHR.instances.at(-1).ontimeout?.(); await ui.settle();
  assert.ok(requests.length > before, 'refresh issued after timeout');
  assert.ok(uncertainRow(ui), 'timeout row is uncertain');
});

test('failed reconciliation retains the marker and blocks retries until a listing succeeds', async () => {
  let failListing = false;
  let listings = 0;
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    const session = adminSession(url);
    if (session) return session;
    listings++;
    if (failListing) return json(500,{message:'boom'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt');
  failListing = true;
  FakeXHR.instances.at(-1).abort(); await ui.settle();
  assert.equal(listings, 2, 'one reconciliation attempt, no automatic listing retry');
  assert.match(ui.elements['status-region'].textContent, /Lỗi tải danh mục/);
  assert.ok(uncertainRow(ui));
  await upload(ui, 'a.txt');
  await ui.settle();
  assert.equal(FakeXHR.instances.length, 1, 'retry waits while the marker is pending');
  failListing = false;
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.equal(FakeXHR.instances.length, 2, 'queued retry starts after a successful listing');
  assert.equal(putPaths().at(-1), '/a.txt');
});

test('folder change during transfer does not reconcile the old directory', async () => {
  const requests = [];
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async raw => {
    const u = new URL(raw, 'http://localhost');
    requests.push(u);
    const session = adminSession(raw);
    if (session) return session;
    if (u.searchParams.get('path') === '/sub') {
      return json(200,{path:'/sub',parent:'/',offset:0,limit:200,total:0,entries:[]});
    }
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[dirEntry('sub','/sub')]});
  }});
  await upload(ui, 'a.txt');
  await ui.emit(ui.created.filter(e => e.className === 'item-link' && e.textContent === 'sub').at(-1), 'click');
  const before = requests.length;
  FakeXHR.instances.at(-1).abort(); await ui.settle();
  assert.ok(uncertainRow(ui), 'outcome still recorded as uncertain');
  assert.equal(requests.length, before, 'no listing for a directory the user left');
  assert.equal(ui.elements['page-info'].textContent, '0 mục');
  await ui.emit(ui.elements['btn-refresh'], 'click');
  assert.equal(requests.at(-1).searchParams.get('path'), '/sub', 'view did not move');
});

test('in-flight folder navigation is not cancelled by an abort reconciliation', async () => {
  let rootRequests = 0;
  let releaseRoot;
  const rootGate = new Promise(resolve => { releaseRoot = resolve; });
  const paths = [];
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async raw => {
    const session = adminSession(raw);
    if (session) return session;
    const u = new URL(raw, 'http://localhost');
    const path = u.searchParams.get('path');
    paths.push(path);
    if (path === '/sub') {
      return json(200,{path:'/sub',parent:'/',offset:0,limit:200,total:0,entries:[]});
    }
    if (++rootRequests > 1) await rootGate;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[dirEntry('sub','/sub')]});
  }});
  await ui.emit(ui.created.filter(e => e.className === 'item-link' && e.textContent === 'sub').at(-1), 'click');
  await upload(ui, 'a.txt');
  const before = paths.length;
  await ui.emit(ui.elements['btn-parent'], 'click');
  assert.deepEqual(paths.slice(before), ['/'], 'parent navigation in flight');
  FakeXHR.instances.at(-1).abort(); await ui.settle();
  assert.ok(uncertainRow(ui));
  assert.deepEqual(paths.slice(before), ['/'],
    'abort must not add a listing while a navigation is in flight');
  releaseRoot(); await ui.settle();
  assert.equal(ui.elements['page-info'].textContent, '1–1 / 1', 'navigation completed');
  assert.equal(ui.elements['file-list-body'].children.length, 1);
});

test('duplicate terminal events settle an upload once', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    const session = adminSession(url);
    if (session) return session;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt','b.txt','c.txt','d.txt');
  assert.equal(FakeXHR.instances.length, 2);
  const first = FakeXHR.instances[0];
  first.status = 201;
  first.onload();
  first.onload(); first.onabort(); await ui.settle();
  assert.equal(FakeXHR.instances.length, 3, 'one slot freed once, so only c.txt started');
  assert.equal(putPaths().at(-1), '/c.txt');
  assert.ok(!uncertainRow(ui), 'a settled success cannot be rewritten by later events');
});

test('queue keeps at most two uploads active and starts the next when one settles', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    const session = adminSession(url);
    if (session) return session;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt','b.txt','c.txt');
  assert.deepEqual(putPaths(), ['/a.txt','/b.txt'], 'two active uploads');
  const b = FakeXHR.instances[1];
  b.status = 201;
  b.onload(); await ui.settle();
  assert.deepEqual(putPaths(), ['/a.txt','/b.txt','/c.txt']);
});

test('queued uploads wait for a pending directory while other directories proceed', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async raw => {
    const session = adminSession(raw);
    if (session) return session;
    const u = new URL(raw, 'http://localhost');
    if (u.searchParams.get('path') === '/sub') {
      return json(200,{path:'/sub',parent:'/',offset:0,limit:200,total:0,entries:[]});
    }
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[dirEntry('sub','/sub')]});
  }});
  await upload(ui, 'b1.txt','b2.txt');
  await upload(ui, 'w1.txt');
  await ui.emit(ui.created.filter(e => e.className === 'item-link' && e.textContent === 'sub').at(-1), 'click');
  await upload(ui, 'o1.txt');
  FakeXHR.instances[0].abort(); await ui.settle();
  assert.deepEqual(putPaths(), ['/b1.txt','/b2.txt','/sub/o1.txt'],
    'queued item for the pending directory must not start');
  const b2 = FakeXHR.instances[1];
  b2.status = 201;
  b2.onload(); await ui.settle();
  assert.deepEqual(putPaths(), ['/b1.txt','/b2.txt','/sub/o1.txt'], 'still no silent overwrite');
  await ui.emit(ui.elements['btn-parent'], 'click');
  assert.deepEqual(putPaths(), ['/b1.txt','/b2.txt','/sub/o1.txt','/w1.txt'],
    'queued item proceeds after returning to a freshly listed directory');
});

test('mkdir 401 returns to login and disables further mutations', async () => {
  const calls = [];
  const ui = await boot({fetch:async (url, options={}) => {
    calls.push({url,options});
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'old'});
    if (url.endsWith('/mkdir')) return json(401,{code:'UNAUTHORIZED'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  ui.elements['new-folder-name'].value = 'new';
  await ui.emit(ui.elements['btn-mkdir'],'click');
  assert.equal(ui.elements['login-view'].style.display,'block');
  assert.equal(ui.elements['explorer-view'].style.display,'none');
  assert.equal(ui.elements['admin-toolbar'].style.display,'none');
  const count = calls.length;
  await ui.emit(ui.elements['btn-mkdir'],'click');
  assert.equal(calls.length,count,'stale UI cannot send another mutation');
});

test('same-name retry sends no PUT before a fresh listing resolves', async () => {
  let holdListing = false;
  let releaseListing;
  const listingGate = new Promise(resolve => { releaseListing = resolve; });
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    const session = adminSession(url);
    if (session) return session;
    if (holdListing) await listingGate;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt');
  holdListing = true;
  FakeXHR.instances.at(-1).abort(); await ui.settle();
  await upload(ui, 'a.txt');
  assert.equal(FakeXHR.instances.length, 1, 'retry must wait for the listing');
  releaseListing(); await ui.settle();
  assert.equal(FakeXHR.instances.length, 2, 'retry starts once the fresh listing lands');
  assert.equal(putPaths().at(-1), '/a.txt');
});

// ---------------------------------------------------------------------------
// Expired-session transitions (Task 5)
// ---------------------------------------------------------------------------

const EXPIRED = 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';

test('rename 401 returns to login and stale UI cannot rename again', async () => {
  const calls = [];
  const ui = await boot({fetch: async (raw, options={}) => {
    calls.push({url:raw, options});
    if (raw.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (raw.endsWith('/rename')) return json(401,{code:'UNAUTHORIZED'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  await ui.emit(ui.created.filter(e => e.textContent === 'Đổi tên').at(-1), 'click');
  ui.created.filter(e => e.tagName === 'INPUT').at(-1).value = 'b.txt';
  await ui.emit(ui.elements['modal-btn-confirm'], 'click');
  assert.equal(calls.filter(c => c.url.endsWith('/rename')).length, 1);
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['admin-toolbar'].style.display, 'none');
  assert.equal(ui.elements['status-region'].textContent, EXPIRED);

  await ui.emit(ui.created.filter(e => e.textContent === 'Đổi tên').at(-1), 'click');
  ui.created.filter(e => e.tagName === 'INPUT').at(-1).value = 'c.txt';
  await ui.emit(ui.elements['modal-btn-confirm'], 'click');
  assert.equal(calls.filter(c => c.url.endsWith('/rename')).length, 1, 'stale UI cannot rename again');
});

test('delete 401 returns to login', async () => {
  const calls = [];
  const ui = await boot({fetch: async (raw, options={}) => {
    calls.push({url:raw, options});
    if (raw.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (options.method === 'DELETE') return json(401,{code:'UNAUTHORIZED'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  await ui.emit(ui.created.filter(e => e.textContent === 'Xóa').at(-1), 'click');
  await ui.emit(ui.elements['modal-btn-confirm'], 'click');
  assert.equal(calls.filter(c => c.options.method === 'DELETE').length, 1);
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['status-region'].textContent, EXPIRED);
});

test('upload 401 returns to login, aborts other uploads and drops the queue', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    const session = adminSession(url);
    if (session) return session;
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt','b.txt','c.txt');
  assert.equal(FakeXHR.instances.length, 2, 'two active uploads');
  const first = FakeXHR.instances[0];
  first.status = 401;
  first.responseText = JSON.stringify({code:'UNAUTHORIZED'});
  first.onload(); await ui.settle();
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['admin-toolbar'].style.display, 'none');
  assert.equal(ui.elements['drop-zone'].style.display, 'none');
  assert.equal(ui.elements['status-region'].textContent, EXPIRED);
  assert.equal(FakeXHR.instances[1].aborted, true, 'other in-flight upload aborted');
  assert.equal(FakeXHR.instances.length, 2, 'queued upload never sent');
  assert.equal(ui.elements['progress-container'].children.length, 0, 'stale upload rows cleared');
  assert.ok(!ui.created.some(e => e.textContent === 'Chưa xác định'), 'reset abort is not uncertain evidence');
});

test('a fetch 401 aborts active uploads, drops queued ones and fresh login uploads with the new token', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async (url, options={}) => {
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'old'});
    if (url.endsWith('/login')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'new'});
    if (url.endsWith('/mkdir')) return json(401,{code:'UNAUTHORIZED'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt','b.txt','c.txt');
  assert.equal(FakeXHR.instances.length, 2);
  ui.elements['new-folder-name'].value = 'x';
  await ui.emit(ui.elements['btn-mkdir'],'click');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(FakeXHR.instances.length, 2, 'queued upload never sent after the reset');
  assert.ok(FakeXHR.instances.every(x => x.aborted), 'active uploads aborted by the reset');
  assert.equal(ui.elements['progress-container'].children.length, 0);

  ui.elements['username'].value = 'admin';
  ui.elements['password'].value = 'pw';
  await ui.emit(ui.elements['login-form'], 'submit');
  assert.equal(ui.elements['explorer-view'].style.display, 'block');
  await upload(ui, 'd.txt');
  assert.equal(FakeXHR.instances.length, 3, 'new upload proceeds after re-login');
  assert.equal(putPaths().at(-1), '/d.txt');
  assert.equal(FakeXHR.instances.at(-1).headers['X-CSRF-Token'], 'new');
});

test('download prechecks the session and navigates when authenticated', async () => {
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  await ui.emit(ui.created.filter(e => e.textContent === 'Tải về').at(-1), 'click');
  assert.equal(ui.window.location.href, '/api/files/download?path=' + encodeURIComponent('/a.txt'));
});

test('download precheck 401 returns to login without navigating', async () => {
  let sessionChecks = 0;
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) {
      sessionChecks++;
      if (sessionChecks > 1) return json(401,{code:'UNAUTHORIZED'});
      return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    }
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  await ui.emit(ui.created.filter(e => e.textContent === 'Tải về').at(-1), 'click');
  assert.equal(ui.window.location.href, '', 'no navigation on an expired session');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['status-region'].textContent, EXPIRED);
});

test('successful logout reuses the reset and aborts in-flight uploads', async () => {
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch:async url => {
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (url.endsWith('/logout')) return json(204,{});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await upload(ui, 'a.txt','b.txt');
  await ui.emit(ui.elements['btn-logout'], 'click');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['admin-toolbar'].style.display, 'none');
  assert.equal(ui.elements['status-region'].textContent, 'Đã đăng xuất.');
  assert.equal(FakeXHR.instances[0].aborted, true);
  assert.equal(FakeXHR.instances[1].aborted, true);
  assert.equal(ui.elements['progress-container'].children.length, 0);
});

test('logout 401 announces the expired session', async () => {
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (raw.endsWith('/logout')) return json(401,{code:'UNAUTHORIZED'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  await ui.emit(ui.elements['btn-logout'], 'click');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['status-region'].textContent, EXPIRED);
});

test('expired session closes an open confirmation modal', async () => {
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (raw.endsWith('/mkdir')) return json(401,{code:'UNAUTHORIZED'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  await ui.emit(ui.created.filter(e => e.textContent === 'Đổi tên').at(-1), 'click');
  assert.equal(ui.elements['modal-overlay'].style.display, 'flex', 'rename modal open');
  ui.elements['new-folder-name'].value = 'x';
  await ui.emit(ui.elements['btn-mkdir'], 'click');
  assert.equal(ui.elements['modal-overlay'].style.display, 'none', 'reset closed the modal');
  assert.equal(ui.elements['login-view'].style.display, 'block');
});

test('late mkdir success after logout cannot reopen the explorer', async () => {
  let releaseMkdir;
  const gate = new Promise(resolve => { releaseMkdir = resolve; });
  const ui = await boot({fetch: async (url, options={}) => {
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (url.endsWith('/mkdir')) { await gate; return json(201,{}); }
    if (url.endsWith('/logout')) return json(204,{});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  ui.elements['new-folder-name'].value = 'late';
  const pending = ui.elements['btn-mkdir'].fire('click');
  await ui.settle();
  await ui.emit(ui.elements['btn-logout'], 'click');
  assert.equal(ui.elements['status-region'].textContent, 'Đã đăng xuất.');
  releaseMkdir();
  await pending; await ui.settle();
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['status-region'].textContent, 'Đã đăng xuất.');
  assert.equal(ui.elements['new-folder-name'].value, 'late', 'late 201 must not run the success branch');
});

test('late listing response after logout cannot repaint the explorer', async () => {
  let releaseListing;
  const gate = new Promise(resolve => { releaseListing = resolve; });
  let listings = 0;
  const ui = await boot({fetch: async raw => {
    if (raw.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'c'});
    if (raw.endsWith('/logout')) return json(204,{});
    if (++listings > 1) {
      await gate;
      return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('LATE.txt','/LATE.txt')]});
    }
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  const pending = ui.elements['btn-refresh'].fire('click');
  await ui.settle();
  await ui.emit(ui.elements['btn-logout'], 'click');
  assert.equal(ui.elements['status-region'].textContent, 'Đã đăng xuất.');
  releaseListing();
  await pending; await ui.settle();
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['status-region'].textContent, 'Đã đăng xuất.');
  assert.doesNotMatch(ui.elements['file-list-body'].textContent, /LATE\.txt/, 'late listing ignored');
  assert.match(ui.elements['file-list-body'].textContent, /a\.txt/, 'visible page unchanged');
});

test('successful re-login uses the fresh CSRF token for later mutations', async () => {
  const mkdirOptions = [];
  const ui = await boot({fetch: async (url, options={}) => {
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'old'});
    if (url.endsWith('/login')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'new'});
    if (url.endsWith('/mkdir')) {
      mkdirOptions.push(options);
      if (mkdirOptions.length === 1) return json(401,{code:'UNAUTHORIZED'});
      return json(201,{});
    }
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  ui.elements['new-folder-name'].value = 'one';
  await ui.emit(ui.elements['btn-mkdir'],'click');
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(mkdirOptions[0].headers['X-CSRF-Token'], 'old');

  ui.elements['username'].value = 'admin';
  ui.elements['password'].value = 'secret';
  await ui.emit(ui.elements['login-form'], 'submit');
  assert.equal(ui.elements['explorer-view'].style.display, 'block');
  assert.equal(ui.elements['lbl-user'].textContent, 'admin');

  ui.elements['new-folder-name'].value = 'two';
  await ui.emit(ui.elements['btn-mkdir'],'click');
  assert.equal(mkdirOptions.length, 2);
  assert.equal(mkdirOptions[1].headers['X-CSRF-Token'], 'new');
  assert.equal(JSON.parse(mkdirOptions[1].body).path, '/two');
});

test('two overlapping 401 responses leave one expired-session state', async () => {
  const releases = [];
  const ui = await boot({fetch: async (url, options={}) => {
    if (url.endsWith('/session')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'old'});
    if (url.endsWith('/login')) return json(200,{user:'admin',role:'ADMIN',csrfToken:'fresh'});
    if (url.endsWith('/mkdir')) {
      await new Promise(resolve => releases.push(resolve));
      return json(401,{code:'UNAUTHORIZED'});
    }
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:0,entries:[]});
  }});
  ui.elements['new-folder-name'].value = 'a';
  const first = ui.elements['btn-mkdir'].fire('click');
  ui.elements['new-folder-name'].value = 'b';
  const second = ui.elements['btn-mkdir'].fire('click');
  await ui.settle();
  assert.equal(releases.length, 2, 'both mkdir requests in flight');
  releases.forEach(resolve => resolve());
  await Promise.all([first, second]); await ui.settle();
  assert.equal(ui.elements['login-view'].style.display, 'block');
  assert.equal(ui.elements['explorer-view'].style.display, 'none');
  assert.equal(ui.elements['status-region'].textContent, EXPIRED);

  ui.elements['username'].value = 'admin';
  ui.elements['password'].value = 'pw';
  await ui.emit(ui.elements['login-form'], 'submit');
  assert.equal(ui.elements['explorer-view'].style.display, 'block', 'the second 401 did not break re-login');
});

test('guest sessions can read listings but cannot mutate', async () => {
  const calls = [];
  const ui = await boot({XMLHttpRequest:FakeXHR, fetch: async (raw, options={}) => {
    calls.push({url:raw, options});
    if (raw.endsWith('/session')) return json(200,{user:'guest',role:'GUEST',csrfToken:'g'});
    return json(200,{path:'/',parent:null,offset:0,limit:200,total:1,entries:[fileEntry('a.txt','/a.txt')]});
  }});
  assert.equal(ui.elements['explorer-view'].style.display, 'block');
  assert.equal(ui.elements['admin-toolbar'].style.display, 'none');
  assert.equal(ui.elements['drop-zone'].style.display, 'none');
  assert.match(ui.elements['file-list-body'].textContent, /a\.txt/, 'guest can read the listing');
  assert.ok(ui.created.some(e => e.textContent === 'Tải về'), 'guest can still download');
  assert.ok(!ui.created.some(e => e.textContent === 'Đổi tên'), 'no rename control for guests');

  ui.elements['new-folder-name'].value = 'x';
  await ui.emit(ui.elements['btn-mkdir'],'click');
  ui.elements['file-picker'].files = [{name:'g.txt',size:1}];
  await ui.emit(ui.elements['file-picker'],'change');
  assert.ok(!calls.some(c => c.url.endsWith('/mkdir')), 'guest mkdir blocked');
  assert.equal(FakeXHR.instances.length, 0, 'guest upload blocked');
  assert.match(ui.elements['status-region'].textContent, /không có quyền/);
});
