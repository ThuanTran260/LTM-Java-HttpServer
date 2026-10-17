# File Explorer and FTP Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sửa 7 vấn đề phát hiện khi review `c54e347...4df6755`, bổ sung test hồi quy chứng minh hành vi trước/sau và giữ tương thích HTTP/FTP hiện có.

**Architecture:** Giữ `SharedFileStore` là nơi duy nhất mutation dưới khóa JVM và OS; bổ sung ràng buộc loại đối tượng cho lệnh FTP ngay trong critical section. Tách bảo vệ CSRF của session khỏi quyền ghi file, chuẩn hóa lỗi công khai tại HTTP boundary, và sửa trạng thái phân trang/session/upload trong giao diện hiện có. Không thay framework hoặc thiết kế lại ứng dụng.

**Tech Stack:** Java thuần JDK 24, Java HttpClient, raw FTP socket, HTML/CSS/JavaScript; Node.js 24 built-in `node:test`, `assert`, `vm` chỉ dùng cho test giao diện, không thêm dependency runtime hay npm package.

## Global Constraints

Các dòng sau trích nguyên văn từ `docs/superpowers/specs/2026-10-17-web-file-explorer-design.md`:

- Java thuần, JDK 24, không thêm dependency runtime.
- Web dùng HTTP API trực tiếp vào kho file; không tạo FTP client trung gian trong backend Web.
- Giữ Java FTP Server, Java FTP Client và FileZilla để demo FTP thật.
- Giữ `admin.html`, quản lý multisite, IIS thật và Fetch; không khôi phục Mock IIS.
- Không preview, không WebSocket, không tìm kiếm toàn kho, không xóa đệ quy, không ghi đè qua Web ở bản đầu.
- Không fallback non-atomic khi publish: atomic move không được hỗ trợ thì báo lỗi, giữ file cũ, dọn staging của lượt đó.
- Dùng fileKey của provider; khi không có identity tin cậy thì fail closed cho upload, không tự coi parent chỉ cùng chuỗi path là cùng thư mục.
- Không đặt password/token session vào URL, localStorage hoặc log.
- Tên file/thông báo từ server dùng textContent; không innerHTML với dữ liệu ngoài.
- Session hết hạn trả về màn login, xóa CSRF trong RAM.

Ràng buộc của đợt sửa: không sửa ồ ạt graphify artifacts, không đổi secret của người dùng, không chạy test mutation vào server đang dùng. Chỉ dùng fixture riêng và teardown `finally`/try-with-resources. Test mock không thay thế nghiệm thu trình duyệt thực.

---

## Context, scope and decisions

Baseline đã kiểm tra: `4df6755`; `compile.bat` thành công, HTTP 153 PASS, FTP 80 PASS, Files 88 PASS khi chạy ngoài sandbox. Đây là baseline lịch sử, không phải kết quả sau sửa. Số lượng test mới lấy từ runner, không hardcode tổng PASS.

| Finding | Deliverable | Task |
|---|---|---|
| RMD xóa file / DELE xóa thư mục | Kiểm tra loại dưới cùng khóa với delete | 1 |
| Guest logout bị 403 nhưng UI báo thành công | CSRF cho logout không đòi quyền ghi | 2 |
| UI chỉ hiển thị 200 mục | Previous/Next và số mục theo `total` | 3 |
| Hủy upload có thể đã commit | Trạng thái chưa xác định, refresh trước retry | 4 |
| Mutation 401 không xóa session UI | Một đường xử lý hết phiên cho fetch/XHR | 5 |
| Lỗi lộ đường dẫn đĩa | Public message allowlist, giữ status/code | 6 |
| Parent identity dùng creationTime | Identity đáng tin cậy hoặc fail closed | 7 |

Một kế hoạch chung phù hợp vì đây là đợt sửa hồi quy của cùng tính năng đã có spec. Mỗi task là một thay đổi có thể review và kiểm thử riêng; không cần thêm đặc tả cho hệ thống mới. Thứ tự thực hiện: 1 → 2 → 3 → 4 → 5 → 6 → 7. Task 4 và 5 cùng sửa queue/session nên thực hiện tuần tự.

So sánh phương án: kiểm tra loại trong FTP handler đơn giản nhưng tạo khoảng hở giữa check/delete; chọn kiểm tra trong store. Đối với listing, chọn phân trang 200 mục thay vì tự gom tối đa 10.000 mục vào UI. Đối với upload bị abort, chọn hiển thị chưa xác định và đối chiếu listing; không tự retry mutation.

## File map

| File | Responsibility / change |
|---|---|
| `src/server/SharedFileStore.java` | Typed deletion và reliable directory identity |
| `src/server/ftp/FtpSessionHandler.java` | Map RMD/DELE sang typed deletion |
| `src/server/FileSessionAuth.java` | Session + CSRF, tách khỏi quyền ghi |
| `src/server/FilesApiHandler.java` | Logout và public error messages |
| `wwwroot/files.js` | Phân trang, session reset, upload reconciliation |
| `wwwroot/files.html`, `wwwroot/files.css` | Previous/Next, nhãn trang, bố cục nhỏ |
| Create `src/client/CrossTestReviewFixes.java` | Regression runner theo group, tái sử dụng ProcessFixture |
| Create `tests/files-ui-harness.cjs` | DOM/fetch/XHR test doubles chạy mã UI thật bằng `vm` |
| Create `tests/files-ui-regressions.test.cjs` | Tests hành vi bằng `node:test` |
| Create `src/server/SharedFileStoreIdentityTest.java` | Test seam identity package-private, chạy Java main |
| `README.md`, `TEST_READY.md` | Cách chạy regression, phân trang và giới hạn provider |

Không chuyển toàn bộ `files.js` sang module/framework. Chỉ thêm helper có người gọi cụ thể. Nếu line number thay đổi, tìm theo tên method trong plan.

## Shared test conventions

Task 1 tạo `CrossTestReviewFixes` trong package `client`. Dùng import Java chuẩn (`java.io.*`, `java.net.*`, `java.net.http.*`, `java.nio.file.*`, `java.util.*`); thêm group mới vào `main` khi task đó có test. Một assertion sai phải throw để process exit nonzero, không in PASS rồi nuốt exception.

```java
static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
}

public static void main(String[] args) throws Exception {
    String group = args.length == 0 ? "ftp-delete" : args[0];
    switch (group) {
        case "ftp-delete" -> testFtpDeleteTypes();
        default -> throw new IllegalArgumentException("Unknown group: " + group);
    }
    System.out.println("PASS " + group);
}
```

Task 2 thêm `guest-logout`; task 6 thêm `public-errors`. Các helper mới đều khai báo trong task tương ứng. `CrossTestFilesClient.ProcessFixture` hiện là `public static`, có `createWorkspace()`, `startFtp(Path, Map<String,String>, List<String>)`, `startHttp(Path, Map<String,String>)`; `ServerInstance` có `port()`, `root()`, `process()`. Dùng đúng các chữ ký này, không sao chép bootstrap.

Mọi chu kỳ Java: `cmd /c compile.bat` phải exit 0 trước khi chạy test, tránh chạy bytecode cũ. Test RED phải thất bại ở assertion hành vi, không phải lỗi compile, classpath hay fixture startup.

### Task 1: Preserve FTP delete command semantics under lock

**Files:** Modify `src/server/SharedFileStore.java:335`, `src/server/ftp/FtpSessionHandler.java:420`; create `src/client/CrossTestReviewFixes.java`.

**Interfaces:** Consumes `withLock`, `SafePaths.resolveSafe`, `FtpClient.sendCommand(String): FtpReply`. Produces `SharedFileStore.DeleteKind { ANY, FILE, DIRECTORY }` và `delete(String virtualPath, DeleteKind kind): void throws IOException`; giữ `delete(String)` cho HTTP, delegate `ANY`.

- [ ] **Step 1: Add the failing FTP wire test** vào runner mới:

```java
static void testFtpDeleteTypes() throws Exception {
    try (var fixture = new CrossTestFilesClient.ProcessFixture()) {
        Path ws = fixture.createWorkspace();
        var server = fixture.startFtp(ws,
                Map.of("FTP_ADMIN_PASSWORD", "ReviewOnly_2026!"), List.of());
        Path file = server.root().resolve("keep.txt");
        Path dir = server.root().resolve("keep-dir");
        Files.writeString(file, "keep");
        Files.createDirectory(dir);
        try (FtpClient ftp = new FtpClient()) {
            ftp.connect("127.0.0.1", server.port());
            check(ftp.login("admin", "ReviewOnly_2026!"), "admin login");
            check(ftp.sendCommand("RMD keep.txt").code() == 550, "RMD rejects file");
            check(Files.readString(file).equals("keep"), "RMD preserves file bytes");
            check(ftp.sendCommand("DELE keep-dir").code() == 550, "DELE rejects dir");
            check(Files.isDirectory(dir), "DELE preserves directory");
            check(ftp.sendCommand("DELE keep.txt").code() == 250, "DELE accepts file");
            check(ftp.sendCommand("RMD keep-dir").code() == 250, "RMD accepts empty dir");
        }
    }
}
```

- [ ] **Step 2: Run RED.** `cmd /c compile.bat`, rồi `java -cp out client.CrossTestReviewFixes ftp-delete`. Expected: assertion `RMD rejects file` fails on baseline.
- [ ] **Step 3: Add typed deletion.** Move existing delete body into overload. Resolve/revalidate target with `SafePaths.resolveSafe(root, virtualPath)` inside `withLock`; reject root as before. Immediately before the existing empty-directory/delete code, use:

```java
boolean directory = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
boolean regular = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
if ((kind == DeleteKind.FILE && !regular)
        || (kind == DeleteKind.DIRECTORY && !directory)) {
    throw new StoreException("FORBIDDEN", "Target type does not match command");
}
```

`handleRmd` calls `store.delete(targetVirtual, SharedFileStore.DeleteKind.DIRECTORY)`; `handleDele` calls `FILE`. Existing mapping FORBIDDEN → FTP 550 stays unchanged. `delete(String)` calls `delete(virtualPath, DeleteKind.ANY)`.
- [ ] **Step 4: Run GREEN.** Compile; run `ftp-delete`, `java -cp out client.CrossTestFilesClient storage`, `java -cp out client.CrossTestFilesClient ftp`, `java -cp out client.CrossTestFtpClient`. Verify nonempty directory still rejected and HTTP delete still accepts either permitted kind.
- [ ] **Step 5: Commit.** `git add src/server/SharedFileStore.java src/server/ftp/FtpSessionHandler.java src/client/CrossTestReviewFixes.java`, then `git commit -m "fix(ftp): preserve file and directory deletion semantics"`.

### Task 2: Allow guest logout with CSRF and truthful UI feedback

**Files:** Modify `src/server/FileSessionAuth.java:239`, `src/server/FilesApiHandler.java:157`, `wwwroot/files.js:223`; modify regression runner; create both JS test files.

**Interfaces:** Produces `requireSessionWithCsrf(HttpExchange): SessionView throws AuthException` for both roles; keeps `requireSession(HttpExchange, boolean)` semantics for file mutation. UI harness produces `boot({fetch, XMLHttpRequest}): Promise<{elements, created, emit, settle}>`; no production test-only exports.

- [ ] **Step 1: Add RED Java test** `testGuestLogout()` and selector `guest-logout`. Use fixture `startHttp(ws, Map.of("FTP_ADMIN_PASSWORD", "ReviewOnly_2026!", "WEB_FILES_ALLOW_GUEST", "true"))`. Add helper below in the same runner:

```java
static HttpResponse<String> request(int port, String route, String method,
        String body, String cookie, String csrf) throws Exception {
    var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("X-File-Explorer", "1").header("Content-Type", "application/json");
    if (cookie != null) b.header("Cookie", cookie);
    if (csrf != null) b.header("X-CSRF-Token", csrf);
    b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
}
```

Test core, inside the fixture block with local `int port = server.port()`:

```java
var login = request(port, "/api/files/login", "POST",
        "{\"username\":\"guest\",\"password\":\"\"}", null, null);
check(login.statusCode() == 200, "guest login");
String cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
var matcher = java.util.regex.Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(login.body());
check(matcher.find(), "csrf exists");
String csrf = matcher.group(1);
check(request(port, "/api/files/logout", "POST", null, cookie, "wrong").statusCode()
        == 403, "wrong CSRF rejected");
var logout = request(port, "/api/files/logout", "POST", null, cookie, csrf);
check(logout.statusCode() == 204, "guest logout succeeds");
check(logout.headers().firstValue("set-cookie").orElse("").contains("Max-Age=0"), "cookie expires");
check(request(port, "/api/files/session", "GET", null, cookie, null).statusCode()
        == 401, "old cookie cannot restore session");
```

Also assert missing CSRF and missing application header return 403; repeat login as admin and verify normal logout. Existing guest upload/mkdir/delete 403 tests must remain green.
- [ ] **Step 2: Build reusable JS harness and failing UI test.** Load actual `wwwroot/files.js` with `vm.runInNewContext`; read HTML IDs from actual `wwwroot/files.html` so absent new controls fail tests. Implement element doubles with style/value/disabled/textContent, children, `appendChild`, `remove`, `focus`, `classList.add/remove`, `addEventListener`; setting `innerHTML=''` must empty children. `emit(element,event)` awaits registered async listeners and calls `settle()`; `settle()` awaits two `setImmediate` ticks. Keep timer callbacks queued rather than automatically removing progress rows. Expose `created` elements to find row buttons; use the supplied fake fetch and XHR. No JS production code extraction or string-matching assertions.

```javascript
// tests/files-ui-regressions.test.cjs
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
```

- [ ] **Step 3: Run RED.** Compile + `java -cp out client.CrossTestReviewFixes guest-logout`; `node --test tests/files-ui-regressions.test.cjs`. Expected guest logout 403 instead of 204; UI hides explorer despite failure.
- [ ] **Step 4: Split auth responsibilities.** Factor existing session/local checks into `requireSession(ex,false)` and add `requireSessionWithCsrf` that checks `X-File-Explorer: 1` and CSRF against returned view, without role rejection. `requireSession(ex,true)` delegates to that method then rejects GUEST. Avoid recursion: the new method calls only the `false` branch. Logout calls the new method before invalidation. UI checks response status: 204 clears state and announces success; 401 clears stale UI; other status/network failure reports logout failure and preserves current view. Do not remove CSRF protection to make guest logout pass.
- [ ] **Step 5: Verify and commit.** Compile; run `guest-logout`, files `session` and `api`, JS suite. Stage auth, API handler, UI, Java regression runner and both JS test files; commit with `fix(files): allow csrf-protected guest logout`.

### Task 3: Expose all directory entries through pagination

**Files:** Modify `wwwroot/files.html:109`, `wwwroot/files.css`, `wwwroot/files.js:238`; extend JS regression suite.

**Interfaces:** `loadDirectory(path, offset = 0): Promise<boolean>` returns true only when this request successfully updates the visible page. Produces `currentOffset`, `currentTotal`, `PAGE_SIZE=200` and controls `btn-prev-page`, `btn-next-page`, `page-info`.

- [ ] **Step 1: Add failing pagination test** using the Task 2 harness:

```javascript
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
```

- [ ] **Step 2: Run RED.** `node --test tests/files-ui-regressions.test.cjs`; baseline has no Next control.
- [ ] **Step 3: Add controls and state.** Insert `<nav aria-label="Phân trang tệp"><button id="btn-prev-page" type="button">Trang trước</button><span id="page-info" aria-live="polite"></span><button id="btn-next-page" type="button">Trang sau</button></nav>` after table. Render `0 mục` for total 0, otherwise `${offset+1}–${Math.min(offset+entries.length,total)} / ${total}` via textContent. Disable previous at offset 0; next at offset+limit >= total; disable both while loading. Previous/Next call loadDirectory with ±200. Folder/breadcrumb/parent navigation resets offset to 0; refresh preserves offset. After delete, if current offset >= new total and total > 0, fetch last valid page once, guarded by request generation.

```javascript
// Update fetch URL in loadDirectory; use captured offset for this request.
const query = new URLSearchParams({path, offset:String(offset), limit:String(PAGE_SIZE)});
const res = await fetch('/api/files?' + query, {
  method:'GET', headers:{'Cache-Control':'no-store'}
});
```

Keep generation check before every render; older responses cannot change path, page label or buttons. Return false for errors/stale responses, true after successful render. On errors restore button state from last accepted page.
- [ ] **Step 4: Extend and run GREEN tests.** Add cases for 0/200/201/401 items, previous page, navigation reset, deleting the sole last-page item, failed fetch and out-of-order responses. Use deferred Promise fetch responses to deliver an old request last; assert current visible page is unchanged. Run JS suite and files `api` group. Browser check: keyboard-operable controls and no horizontal overflow at 375px.
- [ ] **Step 5: Commit.** Stage HTML/CSS/JS and JS tests; `git commit -m "fix(files): paginate explorer directory listings"`.

### Task 4: Reconcile uncertain upload outcomes before retry

**Files:** Modify `wwwroot/files.js:611-735`; extend JS suite and harness XHR double.

**Interfaces:** Consumes `loadDirectory(path,offset): Promise<boolean>`. Produces `pendingReconciliation: Set<string>`, containing target directory paths that require a successful fresh listing before another upload to that directory. Introduces `finishUpload(outcome)` local to each upload; each terminal event settles that upload once.

- [ ] **Step 1: Add controllable XHR test double** with constructor registry `FakeXHR.instances`, `upload={}`, `open`, `setRequestHeader`, `send`, `abort`; abort calls `onabort`, never onload. Tests invoke `xhr.onload/onerror` explicitly and set status/responseText. Queue files by setting `ui.elements['file-picker'].files=[{name:'a.txt',size:1}]` and emitting `change`.

```javascript
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
```

`FakeXHR` is defined in `tests/files-ui-harness.cjs` and exported for this test; reset instances in each `boot` so tests do not share state.
- [ ] **Step 2: Run RED.** JS suite; baseline abort neither refreshes nor marks outcome uncertain.
- [ ] **Step 3: Change terminal outcomes.** Abort, timeout and network error after request start use row label `Chưa xác định`, message `Chưa xác định kết quả tải lên. Đang kiểm tra lại danh sách.`. Add directory to pendingReconciliation before refresh. A 201 remains the only upload success. Do not infer cancellation from `isCancelled` or 100% progress.

```javascript
// At the start of terminal-event cleanup inside each startUpload closure:
let settled = false;
function finishUpload(outcome) {
  if (settled) return;
  settled = true;
  activeUploads--;
  btnCancel.remove();
}
```

Add explicit branches inside this helper after cleanup: success schedules visible-page refresh; rejected HTTP status shows server failure; uncertain marks row and calls `loadDirectory(targetDir,0)` only if user still views that directory. Clear the pending marker only on a successful, newly initiated listing of that directory; do not navigate away from the current folder to reconcile a background upload. A later user visit can reconcile it. Block queued uploads targeting a pending directory; leave other directories eligible. If refresh fails, retain marker and offer normal Làm mới; never auto-retry upload. Do not claim that refreshed absence proves an in-flight server request can never commit; keep the original row uncertain. Preserve the uncertainty notice across refresh: `loadDirectory` currently clears status at entry, so use the row label as durable evidence and restore the status only if auth/session generation remains unchanged.
- [ ] **Step 4: Run GREEN and edge cases.** Test abort after fake server already published, network error, refresh failure, folder change during transfer, duplicate terminal events and max two active uploads. Assert same-name retry sends no PUT before fresh listing resolves. Queued items must not silently overwrite or retry. Run all JS tests.
- [ ] **Step 5: Commit.** `git add wwwroot/files.js tests/files-ui-harness.cjs tests/files-ui-regressions.test.cjs`; `git commit -m "fix(files): reconcile uncertain upload outcomes"`.

### Task 5: Clear expired sessions consistently for fetch and XHR

**Files:** Modify `wwwroot/files.js:163,430-552,611-735`; extend JS tests.

**Interfaces:** Produces `handleSessionExpired(status): boolean`, `sessionGeneration` integer and `activeUploadRequests: Set<XMLHttpRequest>`. Returns true for 401 after invalidating UI/session; false otherwise. Consumes Task 4 terminal cleanup and pagination generation handling.

- [ ] **Step 1: Add RED mutation test:**

```javascript
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
```

- [ ] **Step 2: Run RED.** `node --test tests/files-ui-regressions.test.cjs`; baseline stays in explorer on mkdir 401.
- [ ] **Step 3: Centralize expiration transition.** `handleSessionExpired(401)` increments session/fetch generations before aborting outstanding XHR, clears upload queue, CSRF/user/role and page state, hides admin toolbar/dropzone, closes modal, shows login and expiration message. Reuse this reset for successful logout, with its own success message. Abort callbacks after reset must only clean up their own row/counter and must not refresh, restart queue or overwrite auth status. New authenticated requests capture sessionGeneration and ignore their responses when it changes.

```javascript
// Immediately after each authenticated fetch response, before body/status branching:
if (handleSessionExpired(res.status)) return;
// In XHR onload, after idempotent per-upload cleanup:
if (handleSessionExpired(xhr.status)) return;
```

Apply to mkdir, rename, delete, list, session restore, logout and upload. Login 401 remains invalid credentials, not an expired-session announcement. Add `currentRole !== 'ADMIN'` early returns to mutation handlers; queue processing also requires an active admin session. Preserve guest read permissions. Direct download navigations cannot expose status to JS; on download click check `/api/files/session` first, then navigate only if still authenticated; retain server enforcement because expiry can race the check.
- [ ] **Step 4: Run GREEN.** Add rename/delete/upload 401, download precheck 401, queued uploads not sent after 401, late listing/201 after logout cannot reopen explorer, successful re-login gets fresh CSRF, and repeated 401 reset is idempotent. Assert outgoing mutations after re-login contain new token, not old token. Run JS suite and files `session`/`api` groups.
- [ ] **Step 5: Commit.** Stage JS/tests; `git commit -m "fix(files): invalidate expired explorer sessions consistently"`.

### Task 6: Remove filesystem details from public HTTP errors

**Files:** Modify `src/server/FilesApiHandler.java:104-115`, optionally simplify path-bearing store messages in `src/server/SharedFileStore.java:357,428`; extend Java regression runner.

**Interfaces:** Produces private `publicStoreMessage(String code): String`; status mapping and JSON `{code,message}` stay unchanged. Untrusted exception messages never reach responses. Native exception cause remains available internally.

- [ ] **Step 1: Add deterministic RED test** `testPublicErrors()` selector `public-errors`. Use an actual local HttpServer on port 0 and `FilesApiHandler` with an anonymous `SharedFileStore` subclass whose `list` throws the exception below. Use root from `ProcessFixture.createWorkspace().resolve("root")`; close HttpServer in finally and auth with try-with-resources.

```java
SharedFileStore store = new SharedFileStore(root, 1024) {
    @Override public DirectoryPage list(String path, int offset, int limit)
            throws IOException {
        throw new StoreException("CONFLICT", "locked: C:\\private-canary\\secret.txt");
    }
};
```

Create FileSessionAuth with fixture-only password; call `auth.login("admin", "ReviewOnly_2026!")` directly to obtain token, attach cookie to HTTP GET using Task 2 request helper. Assert 409, `CONFLICT`, no `private-canary`, no drive prefix. Repeat with `SecurityException("C:\\private-canary")` → 403 and `IOException("C:\\private-canary")` → 500; parameterize exception kind with a local AtomicInteger or separate handlers. All three should fail sanitization assertions on baseline.
- [ ] **Step 2: Run RED.** Compile + `java -cp out client.CrossTestReviewFixes public-errors`.
- [ ] **Step 3: Use fixed public messages.** StoreException catch uses allowlisted message by code; SecurityException uses `Access denied`; IllegalArgumentException uses `Invalid request`; generic exception uses `Unexpected server error`. Keep known AuthException messages, which originate from auth validation; do not log credential fields/request bodies/cookies/tokens. Do not implement regex stripping of paths, which misses UNC and provider-specific formats.

```java
private static String publicStoreMessage(String code) {
    return switch (code) {
        case "NOT_FOUND" -> "File or directory not found";
        case "FORBIDDEN" -> "Access denied";
        case "CONFLICT" -> "Operation conflicts with current file state";
        case "NOT_EMPTY" -> "Directory is not empty";
        case "BUSY" -> "Storage is busy; try again later";
        case "TOO_LARGE" -> "File exceeds the upload limit";
        case "TOO_MANY_ENTRIES" -> "Directory exceeds the entry limit";
        case "ATOMIC_UNSUPPORTED" -> "Atomic storage is unavailable";
        case "SETUP_REQUIRED" -> "File storage requires configuration";
        case "NO_SPACE" -> "Insufficient storage space";
        default -> "File operation failed";
    };
}
```

- [ ] **Step 4: Run GREEN.** Compile; `public-errors`, Files `api`/`storage`, JS suite. Extend injected path canary cases to `\\server\share`, message quotes/control characters and generic unsupported code. Assert status/code preservation and valid JSON, no stacktrace/secret. A real Windows file-lock test may supplement but not replace deterministic injection.
- [ ] **Step 5: Commit.** Stage Java handler/store/regression runner; `git commit -m "fix(files): sanitize public storage errors"`.

### Task 7: Require reliable parent directory identity without breaking supported Windows uploads

**Files:** Modify `src/server/SharedFileStore.java:215,265,558`; create `src/server/SharedFileStoreIdentityTest.java`; update `README.md`, `TEST_READY.md`.

**Interfaces:** Replace static `captureDirectoryIdentity(BasicFileAttributes)` with package-private instance `directoryIdentity(Path directory): Object throws IOException`. Both pre-stage and pre-commit paths call this same method. It rejects link/junction parents via SafePaths validation and obtains provider `fileKey`; missing reliable key returns null, which upload treats as `CONFLICT` before publication. No public test toggle.

- [ ] **Step 1: Investigate provider behavior and write RED tests.** The latest commit intentionally introduced creationTime fallback for Windows; removing it blindly may disable all uploads. Probe real JDK 24 Windows attributes with/without `NOFOLLOW_LINKS` on a verified normal directory and record which read obtains non-null key. Keep existing link/root checks. If the provider cannot return any reliable key, document unsupported uploads explicitly; never silently reintroduce timestamp identity to pass tests.

Add package `server` Java main test. For baseline RED, invoke existing private static `captureDirectoryIdentity` reflectively with a `BasicFileAttributes` dynamic Proxy returning null fileKey and fixed FileTime creationTime; assert returned identity is null. This fails against timestamp fallback. After replacing method, switch to package-private seam test below; do not retain a test coupled to the removed private method.

```java
SharedFileStore noIdentity = new SharedFileStore(root, 1024) {
    @Override Object directoryIdentity(Path directory) { return null; }
};
boolean rejected = false;
try {
    noIdentity.upload("/new.bin", new ByteArrayInputStream(new byte[]{1}), 1, false);
} catch (SharedFileStore.StoreException e) {
    rejected = e.code().equals("CONFLICT");
}
if (!rejected || Files.exists(root.resolve("new.bin"))) {
    throw new AssertionError("Upload must fail closed without parent identity");
}
```

`root` belongs to a fresh fixture workspace. Add a replace=true case with existing file and assert original bytes unchanged and staging empty. Add a counting input stream and assert initial missing identity rejects before reading any bytes.
- [ ] **Step 2: Run baseline RED.** Compile and run reflective baseline test; expected assertion that creationTime was accepted. Then add seam/API test during implementation and run until GREEN.
- [ ] **Step 3: Implement reliable identity reading.** Validate canonical parent through `SafePaths.resolveSafe` before reading attributes. Prefer verified provider fileKey; if Windows `NOFOLLOW_LINKS` yields null but normal attribute read returns reliable key, use the normal read only after explicitly rejecting links/junctions and verifying containment. Keep filesystem threat model unchanged: this does not promise protection from an OS user racing filesystem mutation. Remove timestamp fallback. At commit, null or unequal identity must throw CONFLICT and finally clean staging. Preserve SHA-256 destination snapshot and atomic move behavior.

```java
Object before = directoryIdentity(parent);
if (before == null) throw new StoreException("CONFLICT", "Cannot determine parent directory identity");
// Read stream into staging using existing bounded streaming loop.
// Under withLock, after validating the parent still exists and is a safe directory:
Object current = directoryIdentity(parent);
if (current == null || !before.equals(current)) {
    throw new StoreException("CONFLICT", "Parent directory identity changed");
}
```

- [ ] **Step 4: Verify identity edge cases and supported platform.** Package-private override returns key A before staging and B/null at commit: upload must fail, publish nothing, leave old destination bytes intact, remove staging. Equal timestamps with unequal keys must fail. Real Windows provider must pass normal HTTP/FTP uploads and parent replacement concurrency tests. Run compile; `java -cp out server.SharedFileStoreIdentityTest`; Files `storage`, `ftp`, `api`, `concurrency`. Any missing provider capability must be a reported limitation, not counted as a security PASS.
- [ ] **Step 5: Complete docs and full verification.** README documents Previous/Next, guest logout, uncertain-upload refresh and fail-closed provider requirement. TEST_READY lists regression commands and separates browser manual checks from mocked JS tests. Run sequentially:

```powershell
cmd /c compile.bat
java -cp out client.CrossTestReviewFixes ftp-delete
java -cp out client.CrossTestReviewFixes guest-logout
java -cp out client.CrossTestReviewFixes public-errors
java -cp out server.SharedFileStoreIdentityTest
node --test tests/files-ui-regressions.test.cjs
java -cp out client.CrossTestClient
java -cp out client.CrossTestFtpClient
java -cp out client.CrossTestFilesClient all
git diff --check
git status --short
```

Stop on each nonzero exit; do not interpret later success as overriding an earlier failure. If sandbox prevents resolving Windows temp paths, rerun the affected command with platform-approved permissions and preserve both outcomes in the report.
- [ ] **Step 6: Browser acceptance and commit.** Use a disposable workspace and loopback server with fixture credentials. Check guest logout then refresh, browse 201 files both directions, expire session before each mutation, cancel near completed upload and refresh, and confirm upload still works on Windows. Visit upstream `/fetch`/`/iis` content in an authenticated browser to verify isolation remains intact; do not claim browser verification from CSP header assertions. Record PASS/FAIL/SKIP with reasons. Commit identity/test/docs as `fix(files): require reliable upload parent identity`.

## Acceptance checklist and self-review

- [ ] Task 1 preserves target bytes/type for wrong FTP deletion commands and keeps valid commands working.
- [ ] Task 2 invalidates guest session/cookie with correct CSRF; guest file mutation remains forbidden.
- [ ] Task 3 makes entry 201 and later accessible without stale responses replacing current navigation.
- [ ] Task 4 never promises server-side cancellation, never retries blindly, and accounts each active upload once.
- [ ] Task 5 clears RAM CSRF/queue on 401 and rejects responses belonging to previous sessions.
- [ ] Task 6 never serializes arbitrary filesystem exception messages into API responses.
- [ ] Task 7 fails closed for missing identity and retains positive Windows upload coverage.
- [ ] Existing local/Host/Origin/CSRF, size/deadline/atomicity, lock, guest, upstream policy and protocol tests remain green.
- [ ] No build artifacts, test credentials, local config or generated knowledge graph are staged.

Plan self-review: all seven review findings map to tasks; shared interfaces are defined before consumers. Production signatures remain backwards-compatible except private identity implementation. Tests cover protocol/HTTP/UI behavior, not just implementation text. Node is a test-only prerequisite already available locally (`v24.19.0` at planning); mocked DOM tests are explicitly separate from browser acceptance. Optional smell cleanup (rename snapshot duplication and FTP message-based status mapping) remains outside this bugfix scope.

## Test harness implementation reference

Create this code during Task 2, not while reviewing this plan. Task 4 uses the exported XHR double. These doubles implement only browser APIs used by the existing UI; add a browser API only when a behavioral test needs it. Default Node tests in this file must run sequentially because `FakeXHR.instances` is reset by boot; do not enable concurrent test execution within the file.

```javascript
// tests/files-ui-harness.cjs
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
  vm.runInNewContext(fs.readFileSync(path.join(root, 'wwwroot/files.js'), 'utf8'), {
    document, fetch: options.fetch, XMLHttpRequest: options.XMLHttpRequest || FakeXHR,
    window:{location:{href:''}}, URLSearchParams, AbortController, console,
    setTimeout:fn => { timers.push(fn); return timers.length; }, clearTimeout() {}
  }, {filename:'wwwroot/files.js'});
  await settle();
  return {elements, created, timers, settle,
    emit: async (element, type, extra) => {
      if (!element) throw new Error(`Missing element for ${type}`);
      await element.fire(type, extra); await settle();
    }};
}
module.exports = {boot, FakeXHR};
```

For Task 7 baseline RED, use this executable reflection core inside `SharedFileStoreIdentityTest.main`, before switching to the final seam tests:

```java
var attrs = (java.nio.file.attribute.BasicFileAttributes)
        java.lang.reflect.Proxy.newProxyInstance(
            SharedFileStoreIdentityTest.class.getClassLoader(),
            new Class<?>[]{java.nio.file.attribute.BasicFileAttributes.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "fileKey" -> null;
                case "creationTime", "lastModifiedTime", "lastAccessTime" ->
                    java.nio.file.attribute.FileTime.fromMillis(1000);
                case "isDirectory" -> true;
                case "isRegularFile", "isSymbolicLink", "isOther" -> false;
                case "size" -> 0L;
                default -> throw new UnsupportedOperationException(method.getName());
            });
var identity = SharedFileStore.class.getDeclaredMethod("captureDirectoryIdentity",
        java.nio.file.attribute.BasicFileAttributes.class);
identity.setAccessible(true);
if (identity.invoke(null, attrs) != null) {
    throw new AssertionError("Missing fileKey must not become timestamp identity");
}
```

## Execution handoff

The plan is ready for review; no source changes have been implemented by writing this document. Choose **Subagent-Driven** (fresh agent per task with review between tasks) or **Inline Execution** (execute in this session using executing-plans and checkpoints). Create an isolated worktree at execution time if needed, following using-git-worktrees; do not auto-merge or push as part of these fixes.
