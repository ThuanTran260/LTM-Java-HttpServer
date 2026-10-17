// UI regression tests: drive the real wwwroot/files.js against DOM/fetch doubles.
const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, FakeXHR } = require('./files-ui-harness.cjs');
const json = (status, body) => ({status, ok: status >= 200 && status < 300,
  statusText: String(status), json: async () => body});
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
