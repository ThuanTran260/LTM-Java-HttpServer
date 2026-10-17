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
