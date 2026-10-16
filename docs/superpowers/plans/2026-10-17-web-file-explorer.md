# Web File Explorer — Option A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not start parallel edits to the same storage/auth files.

**Goal:** Thêm Web Explorer đăng nhập local, thao tác HTTP trực tiếp vào kho dùng chung với FTP, tách khỏi quản lý website.

**Architecture:** `FilesApiHandler` xử lý HTTP và `FileSessionAuth` quản lý session/local guard. `SharedFileStore` dùng SafePaths, staging và khóa OS để phối hợp với FTP server độc lập. Frontend HTML/CSS/JS thuần gọi API, không gọi FTP.

**Tech Stack:** Java thuần, JDK 24, HttpServer, FileChannel/FileLock, Swing/FTP hiện có, HTML/CSS/JS thuần, script Windows `.bat`; không dependency runtime mới.

**Design source:** `docs/superpowers/specs/2026-10-17-web-file-explorer-design.md`.

## Global Constraints

- Java thuần, JDK 24, không thêm dependency runtime.
- Web dùng HTTP API trực tiếp vào kho file; không tạo FTP client trung gian trong backend Web.
- Giữ Java FTP Server, Java FTP Client và FileZilla để demo FTP thật.
- Giữ `admin.html`, quản lý multisite, IIS thật và Fetch; không khôi phục Mock IIS.
- Không preview, không WebSocket, không tìm kiếm toàn kho, không xóa đệ quy, không ghi đè qua Web ở bản đầu.
- Chỉ Web Explorer/API mới bị giới hạn localhost; không mặc nhiên thay đổi phạm vi truy cập của các route cũ.
- Không đưa secret hoặc `ftp.local.bat` vào Git, log, URL hay localStorage.
- Tất cả ghi file qua cùng store; lớp khóa JVM không thay thế khóa OS giữa hai process.
- Không fallback non-atomic; file thành công chỉ hiện sau commit.
- Chưa triển khai trong lượt lập kế hoạch. Không tuyên bố đã chạy các test tính năng mới khi chỉ có tài liệu.

## Baseline và file map

Baseline source đã đọc: `0e93848`. SafePaths đã kiểm tra root/junction. FTP STOR vẫn TRUNCATE_EXISTING trực tiếp, RNTO thay đích, FTP path dùng URLDecoder và có thể biến `+` thành space. HTTP chưa có API files/session; run_server.bat chưa nạp cấu hình cục bộ. Fetch/proxy chưa sandbox raw upstream. Đây là các seam cần sửa, không xây thêm server/framework.

| File | Thay đổi |
|---|---|
| Create `src/server/FileStoreConfig.java` | Root/giới hạn/guest config, không chứa password |
| Create `src/server/SharedFileStore.java` | CRUD, read handle, staging, version snapshot, JVM/OS lock |
| Create `src/server/FileSessionAuth.java` | Local/Host/Origin guard, session, CSRF, login rate |
| Create `src/server/FilesApiHandler.java` | Routing, bounded metadata, upload stream, error mapping |
| Create `src/server/UpstreamContentPolicy.java` | CSP/nosniff cho nội dung từ Fetch/IIS |
| Create `wwwroot/files.html`, `files.css`, `files.js` | UI độc lập, thao tác cơ bản |
| Modify `src/server/SimpleHttpServer.java` | Tạo config/store/auth, đăng ký API, lifecycle |
| Modify `src/server/RouterHandler.java` | Guard files.html kể cả encoding/case alias; không sửa CRUD cũ |
| Modify `src/server/SafePaths.java` | Path control-character policy, root pin/revalidation seam |
| Modify `src/server/ftp/SimpleFtpServer.java` | Một store mỗi root, chia sẻ cho session |
| Modify `src/server/ftp/FtpSessionHandler.java` | Dùng store cho IO, rename ticket/reset state |
| Modify `src/server/ftp/FtpPathSandbox.java` | Giữ literal +/% và virtual directory semantics |
| Modify `src/server/WebFetchHandler.java`, `ProxyHandler.java` | Dùng UpstreamContentPolicy trên raw response |
| Modify `run_server.bat`, `run_ftp_server.bat` | Nạp local config, root chung, giữ help/demo |
| Modify `wwwroot/index.html`, `admin.html` | Thêm navigation link tối thiểu |
| Create `src/client/CrossTestFilesClient.java`, `run_cross_test_files.bat` | Runner fixture mới, nhóm test và exit code |
| Modify `src/client/CrossTestClient.java`, `CrossTestFtpClient.java` | Tương thích policy mới, regression hiện có |
| Modify `README.md`, `PROJECT.md`, `TEST_READY.md`, `TEST_INFRA.md` | Hướng dẫn và bằng chứng, không sửa lịch sử test thành PASS mới |

## Hợp đồng nội bộ dùng xuyên suốt

Không tự đổi tên API trong các task sau. Các type này là Java concrete class/nested record, không cần interface/factory trừ nơi test thật sự cần seam.

```java
// FileStoreConfig: no secrets in this record.
public record FileStoreConfig(Path root, long maxFileBytes, boolean allowWebGuest) {
    public static FileStoreConfig fromEnvironment();
}

// SharedFileStore nested records/classes:
public record Entry(String name, String path, boolean directory,
                    Long size, Instant modified, boolean blocked) {}
public record DirectoryPage(String path, String parent, int offset,
                            int limit, int total, List<Entry> entries) {}
public record Download(InputStream stream, long size, String name)
        implements AutoCloseable { public void close() throws IOException; }
public record RenameTicket(String source, String version) {}
public static final class StoreException extends IOException {
    public String code();
}

// SharedFileStore public surface:
public SharedFileStore(Path root, long maxFileBytes) throws IOException;
public Path root();
public DirectoryPage list(String path, int offset, int limit) throws IOException;
public Download openDownload(String path) throws IOException;
public Entry upload(String path, InputStream input, long expectedBytes,
                    boolean replace) throws IOException;
public Entry mkdir(String path) throws IOException;
public void delete(String path) throws IOException;
public RenameTicket prepareRename(String source) throws IOException;
public Entry rename(RenameTicket ticket, String target) throws IOException;
public Entry rename(String source, String target) throws IOException;
```

`expectedBytes=-1` chỉ dành cho FTP không biết độ dài trước; Web phải có expected >=0. `replace=false` cho Web, true cho STOR; phiên bản đích được chụp trước nhận và kiểm tra lại khi commit. StoreException code gồm `NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `NOT_EMPTY`, `BUSY`, `TOO_LARGE`, `TOO_MANY_ENTRIES`, `ATOMIC_UNSUPPORTED`, `NO_SPACE`. SecurityException được handler map về FORBIDDEN.

FileSessionAuth public surface: constructor `(String adminPassword, boolean allowGuest, Clock clock)`; `fromEnvironment(boolean allowGuest)`; `checkLocalRequest(HttpExchange ex)`; `login(String username, String password)` trả `SessionView(user, role, csrfToken, sessionToken)`; `requireSession(HttpExchange ex, boolean write)` trả SessionView; `logout(HttpExchange ex)`; `close()`. SessionView là nội bộ, handler không serialize sessionToken ra JSON. Role dùng lại `FtpUserAuth.Role`; Web chỉ login admin hoặc guest cho phép. Clock injectable để test hết hạn không sleep 30 phút. Secret null/blank làm login SETUP_REQUIRED.

FilesApiHandler constructor `(SharedFileStore store, FileSessionAuth auth)`; không đăng ký write API chưa có guard. UpstreamContentPolicy có `static void apply(HttpExchange ex)`; chỉ áp dụng nội dung upstream, không UI ứng dụng.

## Task 1 — Test harness, cấu hình dùng chung và startup

**Files:** tạo FileStoreConfig, CrossTestFilesClient, run_cross_test_files.bat; sửa hai launcher và startup config HTTP/FTP. Không mở API write trong task này.

**Interfaces:** `FileStoreConfig.fromEnvironment()` dùng FTP_ROOT/FTP_MAX_FILE_BYTES/WEB_FILES_ALLOW_GUEST theo spec. Giữ `SimpleHttpServer.startServer(int)` và constructor FTP cũ; thêm overload truyền store khi Task 3 cần.

- [ ] Tạo runner `client.CrossTestFilesClient` nhận một argument nhóm: `config`, `storage`, `ftp`, `session`, `api`, `isolation`, `concurrency`, `all`; không có argument thì all. Assertion fail phải làm exit nonzero, SKIP đếm riêng. Helper fixture thuộc runner có `startHttp(Path workspace, Map<String,String> env)`, `startFtp(Path workspace, Map<String,String> env)`, `close()`; giữ mọi Process để teardown.
- [ ] Fixture copy web assets/build vào temp workspace; chọn port rảnh, truyền process Java bằng argument array, chờ endpoint/banner trong 5 giây và kiểm tra process sống. Nếu bind race thì retry tối đa 3 lần. Không probe/reuse listener của người dùng. Test mới không gọi suite cũ lên server người dùng.
- [ ] Viết test config qua process env: thiếu secret, secret có `!`, root tùy chỉnh, limit sai, root junction, FTP CLI root ưu tiên. Đối chiếu canonical root hai server từ fixture, không log secret.
- [ ] Chạy compile và nhóm config, ghi thất bại trước khi sửa hành vi; không tính lỗi cú pháp test là bằng chứng hành vi bị lỗi.
- [ ] Nạp ftp.local.bat trong cả hai script sau help, trước startup, setlocal DisableDelayedExpansion. HTTP không tự bật demo student. Không đổi giá trị file local của người dùng. Sửa config root mặc định dùng chung, giữ FTP CLI override.
- [ ] HTTP thiếu config Web vẫn phục vụ route cũ, `/api/files/login` được chuẩn bị trả 503 khi đăng ký ở Task 5; trong task này kiểm tra config object và startup thay vì quảng cáo UI đã dùng được. Nếu root/limit không hợp lệ, startup HTTP bắt lỗi riêng của Explorer và đăng ký context unavailable trả JSON503; không truyền null store vào FilesApiHandler. Startup FTP báo lỗi cấu hình và thoát nonzero.
- [ ] Chạy config, help và kiểm tra biến môi trường không rò ra cmd cha. Commit: `feat(files): share local storage configuration between HTTP and FTP`.

Commands cho các task và fixture dùng cùng entrypoint:

```bat
cmd /c compile.bat
java -cp out client.CrossTestFilesClient config
```

`run_cross_test_files.bat` compile rồi gọi runner với `%*` và giữ exit code. Runner luôn chọn port/temp root riêng.

## Task 2 — SharedFileStore và atomic mutation

**Files:** tạo SharedFileStore; sửa SafePaths; thêm nhóm storage trong runner.

**Interfaces:** đúng surface ở trên. Store pin root/state lúc tạo; runtime không được tin root bị đổi thành junction. Không thay virtual path thành URL.

- [ ] Viết fixture với root/temp sibling state, canary ngoài root; test root/child/internal/dangling junction, leaf link, invalid Windows name, NUL/CR/LF, `../`, root delete/rename. Trường hợp file symlink không được quyền tạo phải SKIP riêng; junction Windows vẫn phải chạy khi có thể.
- [ ] Viết assertions storage tối thiểu:

```java
Path root = Files.createTempDirectory("files-store-").resolve("root");
Files.createDirectory(root);
SharedFileStore store = new SharedFileStore(root, 8);
store.upload("/a.bin", new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, false);
try {
    store.upload("/a.bin", new ByteArrayInputStream(new byte[]{9}), 1, false);
    throw new AssertionError("Existing target must not be replaced by Web");
} catch (SharedFileStore.StoreException expected) {
    if (!"CONFLICT".equals(expected.code())) throw expected;
}
if (!Arrays.equals(Files.readAllBytes(root.resolve("a.bin")), new byte[]{1, 2, 3}))
    throw new AssertionError("Original bytes changed");
```

- [ ] Chạy storage trước sửa; viết class với các chức năng theo từng test, không để stub thành deliverable cuối.
- [ ] Dùng streaming byte counter, expected size, stage ngoài root; chuyển file nguyên tử trong khóa commit. Test short/long body và limit N/N+1, file 0 byte, disk IO lỗi mô phỏng. Chỉ tạo thư mục cha khi lệnh mkdir đã tạo; Web upload không âm thầm tạo cây thiếu. Snapshot parent fileKey trước nhận và so sánh lúc commit; parent bị xóa rồi tạo lại cùng tên phải conflict. Provider không cho identity tin cậy thì fail closed upload, không bỏ bước kiểm tra âm thầm.
- [ ] Khóa theo canonical root gồm registry JVM và FileChannel.tryLock deadline 5 giây. Release trong finally. Mọi mutation kiểm tra lại root/parent/target dưới khóa. Snapshot version là loại + tồn tại + SHA-256 file; source directory rename dùng identity/fileKey cùng snapshot tên/mtime, không hash đệ quy; RNTO có conflict thì từ chối, không tiếp tục bằng Path cache.
- [ ] Thêm seam package-private cho bước move để test AtomicMoveNotSupportedException mà không ảnh hưởng mọi store/process; không dùng flag static toàn chương trình mới. Atomic unsupported phải giữ file cũ và xóa stage của lượt đó. Chỉ thao tác staging file được process tạo, không dọn file đang được process khác dùng.
- [ ] List một cấp, cap 10.000 entry, paginate 1..200, blocked entries không follow metadata; openDownload trả handle+size, không đọc cả file vào RAM. Download HTML/EXE vẫn là bytes, không thực thi.
- [ ] Chạy storage và SafePaths regression cũ. Commit: `feat(files): add shared atomic file storage with cross-process locking`.

## Task 3 — FTP dùng shared store, giữ tương thích client

**Files:** SimpleFtpServer, FtpSessionHandler, FtpPathSandbox, CrossTestFtpClient và nhóm ftp của runner mới.

**Interfaces:** SimpleFtpServer tạo một store, truyền `(Socket, SharedFileStore)` vào session handler; giữ overload cũ `(Socket, Path)` khi caller còn dùng. pendingRenameFrom Path chuyển thành RenameTicket. FTP vẫn có control/data socket độc lập, không gọi HTTP.

- [ ] Thêm test RETR/STOR/LIST/MKD/RMD/DELE/RNFR/RNTO qua wire với dữ liệu binary, Unicode, spaces, `a+b.txt`, `100%.txt`; file cũ giữ nguyên khi transfer lỗi; không thấy staging. Kiểm tra STOR thay file có sẵn thành công nếu không có concurrent mutation.
- [ ] Chạy nhóm ftp trước sửa để thấy bất tương thích literal + và upload phá file cũ; chọn tình huống lỗi socket thật thay vì coi mọi IOException là PASS.
- [ ] Đổi các handler IO sang store. FTP resolveVirtual tiếp tục điều hướng nhưng không decode form URL tên file. Giữ từ chối mẫu encoded traversal/null của suite cũ; đừng dùng URLDecoder làm thay `+`.
- [ ] STOR nhận data vào store.upload với expected=-1, replace=true; chỉ gửi 226 sau commit. Không khai báo byte count mong muốn mà giao thức chưa cung cấp. Map code: CONFLICT/NOT_EMPTY/NOT_FOUND/FORBIDDEN ->550; BUSY/stale change ->450; TOO_LARGE/NO_SPACE ->552/452; ATOMIC_UNSUPPORTED ->451; transfer IO ->426.
- [ ] RNFR tạo ticket; RNTO consume ticket trong finally, kiểm tra nguồn/đích lại dưới lock. USER/PASS xóa pending state và đóng data channel để phiên trước không rò sang phiên mới. Không tự hỗ trợ overwrite rename.
- [ ] Chạy FTP suite cũ trong temp workspace và nhóm ftp mới; nếu assertion cũ đòi overwrite rename thì cập nhật thành lỗi policy có giải thích, không nới policy vì test. Commit: `fix(ftp): coordinate file mutations with the shared storage layer`.

## Task 4 — Session local và cô lập Fetch/IIS upstream

**Files:** tạo FileSessionAuth, UpstreamContentPolicy; sửa WebFetchHandler, ProxyHandler; nhóm session/isolation trong runner.

**Interfaces:** FileSessionAuth/SessionView như hợp đồng. Không dùng FTP fallback password cho Web. UpstreamContentPolicy.apply đặt CSP sandbox và nosniff trước sendResponseHeaders.

- [ ] Test local guard bằng HttpExchange fixture có remote non-loopback thật/simulated, Host đúng/sai/giả mạo suffix, Origin khác port/null, X-Forwarded-For giả. Test expiry với Clock cố định, idle/max life, logout, session capacity, login throttle.
- [ ] Assert guest denied mutation ở backend, không chỉ UI. Web admin secret null/blank -> SETUP_REQUIRED; không chấp nhận admin123 do fallback. Token đủ random, độc lập; không lộ password trong SessionView/exception/log.
- [ ] Chạy session red rồi cài secure token, cookie HttpOnly/SameSite/Path, bounded sessions, role checks và CSRF. Password chỉ dùng trong xác thực; cấu hình secret giữ phía server, không trả client. SessionView không dùng toString mặc định làm lộ token: override redacted hoặc không chứa token trong object được log, và test logger không in chúng.
- [ ] Tạo fixture upstream có HTML script/form vô hại, kiểm tra header Fetch raw và IIS proxy raw đều có sandbox không allow-scripts/allow-same-origin. JSON vẫn parse được, HTML UI không bị sandbox.
- [ ] Chạy isolation red, thêm UpstreamContentPolicy. Không viết sanitizer HTML tự chế. Trình duyệt sẽ được kiểm tra thực ở Task 7; header test không được gọi là browser exploit đã bị chặn.
- [ ] Chạy session/isolation và HTTP suite cũ. Commit: `feat(files): add local sessions and isolate untrusted upstream content`.

## Task 5 — HTTP file API hoàn chỉnh

**Files:** tạo FilesApiHandler; sửa SimpleHttpServer/RouterHandler; nhóm api.

**Interfaces:** constructor nhận store/auth. Đăng ký context `/api/files`, dispatcher so exact route, từ chối `/api/filesXYZ`. API/status/body theo spec; lỗi API là JSON, không HTML redirect.

- [ ] Viết wire tests với HTTP process riêng: thiếu config, login/session/logout, admin CRUD, guest đọc/403, không cookie401, CSRF sai403, Origin/Host sai403, nonlocal403, method405 và route404. Không ghi token/cookie vào test failure dump.
- [ ] Chạy api trước đăng ký route để ghi red 404; sau đó cài login/local guard trước mọi IO và trước mọi write.
- [ ] GET list trả page một cấp, download stream attachment/octet-stream với filename* UTF-8 và nosniff/no-store. POST mkdir/rename JSON parse bounded 16KiB; dùng serializer escapeJson hiện có cho mọi tên và message.
- [ ] PUT upload raw octet-stream, X-File-Size bắt buộc; đếm thực tế dù chunked, tối đa 2 HTTP uploads, deadline120/read30 giây. Dùng shared daemon scheduler với remove-on-cancel để đóng exchange ở deadline, hủy task khi kết thúc; không tạo một thread timer cho mỗi request hoặc executor không dừng theo mỗi instance server. Khi đọc body/header lỗi không tạo thành công giả; finally cleanup stage và permit.
- [ ] Map StoreException theo bảng spec; không lộ absolute path/stacktrace. DELETE root hoặc nonempty directory bị từ chối. Mọi mutation cần X-File-Explorer và token; login cần app header/Origin guard nhưng chưa cần CSRF.
- [ ] Test một file HTML lấy qua download vẫn attachment, file 0 byte, Unicode filename header không CRLF; upload ngắt và atomic unsupported giữ hash. Test Web vẫn chạy với FTP server đã dừng.
- [ ] Guard đường files.html theo peer/case/encoding tương tự admin hiện có; API luôn có guard độc lập. Khi thiếu root/secret, feature báo setup lỗi nhưng API/time/sites cũ vẫn chạy.
- [ ] Chạy api/session/storage và HTTP suite. Commit: `feat(files): expose authenticated local file management APIs`.

## Task 6 — Giao diện file riêng và navigation

**Files:** tạo files.html/files.css/files.js; sửa index.html/admin.html; không dùng framework/CDN.

**Interfaces:** JS chỉ gọi API ở spec, giữ csrf trong RAM; cookie do browser gửi same-origin. User/password không ghi URL/storage. Không thêm nút mở file server như trang web.

- [ ] Tạo màn login/setup, bảng tên/loại/size/modified, breadcrumbs, parent, Làm mới, logout. Phân biệt loading/empty/error và có status region aria-live, label/keyboard focus.
- [ ] Thêm action theo quyền, backend vẫn kiểm tra. Rename chỉ nhập basename trong cùng thư mục ở UI; delete có xác nhận hiển thị tên bằng textContent; blocked entry không có action.
- [ ] Upload XHR gửi File raw, header X-File-Explorer/X-CSRF-Token/X-File-Size, tối đa2 request. Drop zone xử lý drag/drop mặc định, file picker vẫn dùng được bằng bàn phím. Thông báo per-file, chỉ success sau response201, conflict409 không tự ghi đè/retry.
- [ ] Refresh dùng AbortController/request generation tránh response cũ đổi nhầm thư mục. 401 quay login, xóa csrf; 503 setup hướng dẫn restart sau cấu hình. GET download dùng link cùng origin; không fetch cả file vào Blob RAM chỉ để tải.
- [ ] Thêm navigation link Trang chủ / Quản lý website / Quản lý file trên ba trang; giữ form site manager và Fetch cũ.
- [ ] Kiểm tra browser thật: text chứa HTML/script chỉ hiện chữ; upload hai file, cancel trong progress, lỗi mạng sát commit báo trạng thái chưa xác định và refresh; guest không có action ghi, gọi API vẫn403. Không tự tạo app/tab ngoài ý người dùng khi chưa cần trình diễn.
- [ ] Commit: `feat(ui): add a separate local web file explorer`.

## Task 7 — Tích hợp hai tiến trình và kiểm tra đồng thời

**Files:** CrossTestFilesClient, các test FTP/HTTP cũ nếu cần cập nhật theo policy đã nêu.

**Interfaces:** dùng process HTTP/FTP từ Task 1 với cùng root/state, không dùng shared Java singleton để giả lập cross-process.

- [ ] HTTP upload ZIP/binary/zero-byte/Unicode -> FTP RETR, so SHA-256; FTP STOR -> Web download so SHA-256; Web khi FTP tắt vẫn hoạt động; startup root khác báo rõ không cùng kho.
- [ ] Dùng latch/readiness thật để dừng transfer sau partial bytes. Hai upload cùng tên: HTTP create không đè; FTP snapshot stale trả450. Hai tên khác nhau thành công. Rename/delete trong lúc upload không làm publish vào vị trí ngoài ý định; stage không có mặt trong list cả hai kênh.
- [ ] Test hai SharedFileStore cùng JVM không ném OverlappingFileLockException; test hai JVM chờ/timeout khóa có kết quả409/450. Không dùng sleep ngẫu nhiên làm bằng chứng concurrency.
- [ ] Ép atomic unsupported, socket reset/426, HTTP body thiếu/thừa/oversize, target bị đổi thành junction trước commit; marker và file cũ nguyên vẹn. Error test phải assert đúng code và server đã tới stage cần kiểm tra, không chấp nhận bất kỳ IOException nào.
- [ ] Browser thật: login admin, mở Fetch upstream script cố gọi `/api/files`; script không chạy vì sandbox, không có mutation; admin UI thật vẫn hoạt động. Test này không được đánh PASS bằng kiểm tra header đơn thuần.
- [ ] FileZilla + Java FTP Client + browser cùng mở kho, Refresh thấy thay đổi, download bytes khớp. Demo rename/delete guest bị chặn. Quay lại admin.html tạo/bật/tắt site vẫn chạy; IIS/Fetch không bị bỏ.
- [ ] Chạy all ở temp workspace; thiếu browser/FileZilla/symlink permission phải ghi chưa kiểm hoặc SKIP, không đánh dấu nghiệm thu thủ công hoàn tất. Commit test: `test(files): cover cross-process storage and browser security boundaries`.

## Task 8 — Hướng dẫn chạy, review và cổng hoàn thành

**Files:** README, PROJECT, TEST_READY, TEST_INFRA; không sửa file secret.

- [ ] Ghi cách chạy hai script cùng FTP_ROOT; HTTP đọc local password, Web session mất khi restart; default student FTP tắt, guest Web opt-in. Đường `/files.html` khác `/admin.html` và upload không xuất bản site.
- [ ] Ghi maxfile64MiB, 2 Web uploads, folder cap/pagination, không overwrite qua Web, không xóa cây, không preview; 100% progress chưa chắc commit. Hướng dẫn dừng cả hai process trước khi dọn stage cũ do crash.
- [ ] Ghi khác biệt môi trường: cookie dành HTTP local, ATOMIC_MOVE cần được filesystem hỗ trợ, custom CLI root có thể lệch Web root; không khuyên chạy Java bằng Administrator hoặc tắt firewall để chữa lỗi.
- [ ] Chạy kiểm chứng cuối trong temp workspace:

```bat
cmd /c compile.bat
java -cp out client.CrossTestFilesClient all
java -Djava.awt.headless=true -cp out client.CrossTestClient
java -cp out client.CrossTestFtpClient
```

Lệnh HTTP trên được ghi theo CMD; trong PowerShell phải đặt dấu nháy quanh toàn bộ đối số `-D...`. Runner cũ dùng cổng cố định nên chỉ chạy trong fixture riêng/cổng test riêng khi không có dịch vụ người dùng; không dùng trực tiếp để ghi/xóa kho thật.

- [ ] Review diff: mọi mutation FTP/Web đi qua store, không còn STOR truncate trực tiếp, mọi API có guard, raw upstream được sandbox, secret không staged. Kiểm tra git diff --check và tests PASS/FAIL/SKIP; không chốt bằng số lượng test cố định.
- [ ] Lưu kết quả thủ công riêng; không tạo release/merge/push hoặc công bố tính năng đã hoàn tất khi còn nhóm bắt buộc chưa kiểm. Commit tài liệu: `docs(files): document local file explorer and shared FTP storage`.

## Dependencies, rollback và thứ tự thực hiện

Task 1 -> 2 -> 3. Task 4 dựa trên config và phải xong trước khi mở API Task 5. Task 5 -> 6 -> 7 -> 8. Không chia đồng thời hai agent sửa SharedFileStore/FtpSessionHandler; mỗi task review độc lập rồi chuyển bước tiếp.

Không public API ghi giữa chừng nếu chưa có session/CSRF/upstream isolation. Có thể revert UI/API riêng để trở lại FTP/HTTP cũ; không revert một phía khóa/staging rồi quảng cáo còn bảo đảm cross-process. Nếu Task 3 chưa xong, Web Explorer chỉ có thể được đánh giá UI/read-only nội bộ, chưa đủ nghiệm thu ghi đồng thời.

## Self-review của bản kế hoạch

- Phạm vi A/local/login được đưa vào Task 1,4,5; không gọi FTP từ Web.
- Root/secret nhất quán ở Task 1; bytes/literal names/atomic ở Task 2,3; API/UI ở Task 5,6.
- Ranh giới Fetch cùng origin có Task 4,7; khóa hai JVM có Task 2,7; không dùng static lock để thay bằng chứng cross-process.
- Preview/tree/WebSocket/Internet và quota cứng toàn ổ không nằm trong deliverable.
- Chỉ đánh hoàn thành sau tự động và manual checks; lượt này mới tạo thiết kế/kế hoạch.
