# Thiết kế Web File Explorer — phương án A

Ngày: 17/10/2026. Baseline: `0e93848`.
Người dùng đã chốt phương án A, giao diện quản lý file riêng, có đăng nhập và chỉ truy cập localhost. Tài liệu này ghi thiết kế để lập kế hoạch; chưa triển khai tính năng.

## 1. Mục tiêu và giới hạn

- Java thuần, JDK 24, không thêm dependency runtime.
- Web dùng HTTP API trực tiếp vào kho file; không tạo FTP client trung gian trong backend Web.
- Giữ Java FTP Server, Java FTP Client và FileZilla để demo FTP thật.
- Giữ `admin.html`, quản lý multisite, IIS thật và Fetch; không khôi phục Mock IIS.
- Chỉ Web Explorer/API mới bị giới hạn localhost; không mặc nhiên thay đổi phạm vi truy cập của các route cũ.
- Web Explorer chỉ được coi hoàn thành sau khi có bảo vệ phiên, file tạm và kiểm thử đồng thời với FTP.
- Không preview, không WebSocket, không tìm kiếm toàn kho, không xóa đệ quy, không ghi đè qua Web ở bản đầu.
- Chỉ dùng file mẫu; không cam kết chống người có quyền sửa filesystem trực tiếp hoặc nghe lén plain FTP.

## 2. Phân vai và dữ liệu

| Thành phần | Trách nhiệm |
|---|---|
| `wwwroot/files.html`, `files.css`, `files.js` | Giao diện, đăng nhập, danh sách, thao tác HTTP |
| `/api/files` và các subroute | Kiểm tra local/session/quyền rồi gọi SharedFileStore |
| `ftp_workspace/server_root/` mặc định | Kho dữ liệu dùng chung, không phải web root |
| `wwwroot/` | Mã giao diện ứng dụng, không chứa file người dùng upload |
| `sites/<name>/` | Nội dung website do chức năng quản lý site phục vụ |
| FTP `:2121` | Giao thức FTP độc lập, dùng cùng lớp thao tác kho file |

Web hoạt động khi FTP server tắt. FTP hoạt động khi HTTP server tắt. Cả hai thấy dữ liệu mới khi tải lại danh sách; không quảng cáo đồng bộ giao diện thời gian thực. Upload HTML vào kho không tự xuất bản website.

## 3. Cấu hình dùng chung

`FileStoreConfig` đọc biến môi trường, không chứa secret trong `toString` hay response:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `FTP_ROOT` | `ftp_workspace/server_root` | Root mặc định cho cả hai server |
| `FTP_MAX_FILE_BYTES` | `67108864` | 64 MiB mỗi upload; áp dụng cả HTTP và FTP |
| `WEB_FILES_ALLOW_GUEST` | `false` | Cho Web guest đăng nhập chỉ đọc |
| `FTP_ADMIN_PASSWORD` | Không có mặc định cho Web | Secret admin dùng chung với FTP |

`run_server.bat` và `run_ftp_server.bat` đều nạp file `ftp.local.bat` nếu tồn tại, bên trong `setlocal DisableDelayedExpansion`. Không đưa mật khẩu cục bộ vào tài liệu hoặc commit. Không đọc/ghi file cấu hình này qua Web.

Web không dùng fallback `admin123`: nếu thiếu secret, login trả 503 `SETUP_REQUIRED`, giao diện hướng dẫn cấu hình rồi restart HTTP server. Các chức năng HTTP cũ vẫn chạy. FTP giữ hành vi admin/anonymous/student hiện có để không thay đổi ngoài phạm vi; mặc định script thường vẫn tắt student. Web bản đầu chỉ hỗ trợ admin và guest tùy chọn, không mở thêm tài khoản student.

CLI FTP `port root` vẫn được ưu tiên hơn `FTP_ROOT`. Log nêu root thực để người dùng nhận ra cấu hình lệch; không có tuyên bố đồng bộ khi hai root khác nhau. Cấu hình mới chỉ có hiệu lực sau restart. Khi dùng Web Explorer, hướng dẫn chạy hai script với cùng `FTP_ROOT`, không dùng `sites/ftp-demo` làm kho chung.

## 4. Ranh giới filesystem và đồng thời giữa hai JVM

`SharedFileStore` là điểm duy nhất thực hiện mutation cho cả Web và FTP. Tái sử dụng `SafePaths`, xác minh root trước tạo state và pin real root lúc startup. Root/ancestor chuyển hướng hoặc không xác minh được bị từ chối; root không được xóa/rename. Kiểm tra lại nguồn/đích/cha ngay trước commit.

State nội bộ là thư mục sibling `<root-name>.state/`, gồm `write.lock` và `staging/`, nằm ngoài root nên không xuất hiện qua LIST hoặc Web. State phải cùng filesystem với root; không cho state là symlink/junction. File tạm tên ngẫu nhiên được tạo độc quyền bằng `Files.createTempFile`; không dùng tên do người dùng quyết định.

Mỗi mutation cuối được bảo vệ bằng khóa JVM dùng chung theo canonical root, sau đó `FileChannel.lock` trên `write.lock`. Registry khóa JVM tránh `OverlappingFileLockException` khi HTTP/FTP chạy cùng JVM; khóa OS phối hợp khi chúng chạy ở hai JVM. Thứ tự luôn JVM lock rồi OS lock; chờ tối đa 5 giây qua tryLock/retry, lỗi trả busy. Không giữ khóa trong suốt lúc nhận upload qua mạng.

HTTP upload không ghi đè. FTP STOR vẫn cho thay file hiện hữu như trước, nhưng phải staging và atomic commit. Với FTP, chụp trạng thái đích trước khi nhận dữ liệu; dưới khóa commit, so sánh loại, sự tồn tại và SHA-256 của nội dung đích với snapshot. Nếu đã đổi thì từ chối 450; không đè thay đổi từ phiên khác. Cả hai kênh chụp identity thư mục cha trước nhận; nếu cha bị xóa/đổi thành thư mục khác cùng tên trước commit thì từ chối conflict, không publish vào thư mục mới. Dùng fileKey của provider; khi không có identity tin cậy thì fail closed cho upload, không tự coi parent chỉ cùng chuỗi path là cùng thư mục. Khi cả hai upload cùng tên chưa tồn tại, chỉ lần commit đầu thành công. Các upload khác tên không bị hủy chỉ vì một file khác thay đổi.

Mkdir/delete/rename đều dùng cùng khóa. Rename mặc định không thay đích đã tồn tại; FTP RNFR lưu ticket nguồn và snapshot, RNTO xác minh lại rồi tiêu thụ ticket dù thành công hay lỗi. USER/PASS đổi phiên phải xóa ticket và data channel. Download mở handle đọc, không giữ khóa trong suốt truyền file; nếu Windows từ chối rename/delete file đang được đọc, báo conflict, không ép đóng reader của phiên khác.

Không fallback non-atomic khi publish: atomic move không được hỗ trợ thì báo lỗi, giữ file cũ, dọn staging của lượt đó. Không tự quét/xóa staging của process khác khi startup. Staging còn lại sau crash chỉ được dọn thủ công khi cả hai server đã dừng; README ghi rõ cách xác minh. Không tuyên bố có quota toàn ổ đĩa: bản đầu có giới hạn mỗi file, số upload HTTP và xử lý disk-full an toàn.

Đường dẫn API là virtual path `/...`; query decode đúng một lần. Từ chối control characters, traversal, drive/UNC/ADS, tên Windows cấm và hậu tố dot/space. FTP không URL-decode tên file thành phần: giữ nguyên dấu `+`/`%` hợp lệ, tiếp tục từ chối các mẫu encoded traversal/null đã có. Liệt kê link/junction chỉ trả tên với `blocked=true`, không đọc size/mtime của đích và không cho mở/thao tác.

## 5. Đăng nhập, session và cô lập nội dung không tin cậy

- Mọi `/api/files` kiểm tra remote socket là loopback, không tin `X-Forwarded-For`.
- Host phải đúng `localhost`, `127.0.0.1` hoặc `[::1]` và cổng HTTP đang chạy; không so substring. Origin nếu có phải khớp chính xác scheme/host/port; `Origin: null` bị từ chối.
- Request ghi, kể cả login, bắt buộc `X-File-Explorer: 1`; không bật CORS/preflight cho origin khác. Sau login, request ghi còn bắt buộc token `X-CSRF-Token` đúng session. Native test không có Origin vẫn phải có header và session/token tương ứng.
- Token session và CSRF độc lập, mỗi token 32 byte SecureRandom, encode Base64 URL-safe. Session giữ trong RAM; không lưu password sau login.
- Cookie `FileExplorerSession`: `HttpOnly; SameSite=Strict; Path=/api/files`, không có Domain. HTTP localhost không gắn Secure; không dùng cấu hình này cho Internet. Không đặt password/token session vào URL, localStorage hoặc log.
- Tối đa 32 session; idle 30 phút, tối đa 8 giờ; hết hạn/logout/restart làm mất phiên. Login sai 5 lần trong 60 giây trả 429 và Retry-After; không khóa tài khoản FTP vì login Web sai.
- Admin có đầy đủ quyền. Guest chỉ được session/list/download; guest mutation trả 403, kể cả gửi API trực tiếp.
- Mọi API trả `Cache-Control: no-store` và `X-Content-Type-Options: nosniff`; response không chứa đường dẫn đĩa, secret, stacktrace hay token ngoài dữ liệu session được thiết kế.

Điều kiện trước khi mở Web mutation: raw upstream từ Fetch và proxy IIS phải có CSP sandbox không `allow-scripts`/`allow-same-origin`, `form-action 'none'`, `frame-ancestors 'none'` và nosniff. JSON Fetch vẫn là JSON. Không gắn chính sách sandbox đó lên HTML giao diện của chính ứng dụng. Browser test phải chứng minh trang upstream không thao tác được `/api/files` trong một phiên đã đăng nhập. Đây là sửa ranh giới cần thiết vì có thêm Web quản trị file, không phải một đợt sửa toàn bộ SSRF/TLS.

## 6. Hợp đồng HTTP API

Body JSON cho login/mkdir/rename tối đa 16 KiB. Upload dùng raw bytes `application/octet-stream`, một file/request để không phải tự viết multipart parser. Tên/path truyền bằng URLSearchParams. Mutation chỉ trả thành công sau commit.

| Method và route | Request | Thành công |
|---|---|---|
| POST `/api/files/login` | JSON `{username,password}`, header ứng dụng | 200, cookie và `{user,role,csrfToken}` |
| GET `/api/files/session` | Cookie | 200, `{user,role,csrfToken}` |
| POST `/api/files/logout` | Cookie, CSRF | 204, xóa cookie/session |
| GET `/api/files?path=/&offset=0&limit=200` | Cookie | 200, directory page |
| GET `/api/files/download?path=/a.zip` | Cookie | 200, stream attachment |
| PUT `/api/files/content?path=/a.zip` | Cookie, CSRF, `X-File-Size`, raw bytes | 201, metadata file mới |
| POST `/api/files/mkdir` | JSON `{path}` | 201, metadata thư mục |
| POST `/api/files/rename` | JSON `{from,to}` | 200, metadata đích |
| DELETE `/api/files?path=/a.zip` | Cookie, CSRF | 204; chỉ file/thư mục rỗng |

`/api/filesXYZ` không phải API và phải 404. Khi không đăng nhập trả 401, không redirect HTML. Lỗi JSON `{code,message}` có status: 400 input sai, 403 quyền/path/local guard, 404 không tồn tại, 409 trùng tên/nonempty/stale/busy, 413 quá giới hạn, 415 sai media type, 422 thư mục quá lớn, 429 login rate, 503 setup/atomic unsupported, 507 hết dung lượng, 500 IO ngoài dự kiến. Method sai trả 405 kèm Allow.

Directory page: `{path,parent,offset,limit,total,entries}`; entry `{name,path,directory,size,modified,blocked}`. `size`/`modified` null nếu blocked; modified là ISO-8601 UTC. Liệt kê tối đa 10.000 entry/thư mục, limit 1..200, folders trước rồi tên; không quét đệ quy. Nếu vượt 10.000 trả 422, không âm thầm giấu phần còn lại.

Download luôn `application/octet-stream`, `Content-Disposition: attachment` với filename ASCII fallback và UTF-8 filename*, không inline và không thực thi. File 0 byte trả đúng 0 byte. Không làm Range/resume download Web ở bản đầu.

Upload tối đa 64 MiB mặc định, tối đa 2 upload HTTP đang chạy; header X-File-Size bắt buộc là số nguyên không âm và không vượt giới hạn. Đếm byte thực trong stream, kể cả chunked; thiếu/thừa so expected thì không commit. FTP không có expected-size bắt buộc nên EOF sạch được xem là kết thúc; không cam kết phát hiện mọi upload FTP bị người gửi đóng sớm nhưng không gây lỗi socket.

Upload HTTP có deadline tuyệt đối 120 giây, timeout đọc 30 giây, đóng exchange khi hết thời gian và dọn staging. Không dùng readAllBytes cho file. Body metadata giới hạn 16 KiB; chỉ đọc từng thư mục, không trả toàn bộ filesystem vào RAM.

## 7. Giao diện

`files.html` có thanh điều hướng Trang chủ / Quản lý website / Quản lý file. Trang login và thông báo setup ở cùng trang. Sau đăng nhập hiển thị user/role, Đăng xuất, breadcrumbs, bảng, thông tin thư mục, Làm mới.

Toolbar: chọn file, drop zone, tạo thư mục; mỗi row có download, rename, delete theo quyền. Bảng có tên/loại/kích thước/thời gian; dùng nhãn văn bản/icon đơn giản, không chỉ dựa vào màu. Có loading, empty, lỗi, progress và trạng thái hàng đợi. Tên file/thông báo từ server dùng textContent; không innerHTML với dữ liệu ngoài.

Upload dùng XMLHttpRequest để hiển thị progress, hàng đợi tối đa 2 request. 100% gửi xong chưa phải thành công: chờ response commit. Nếu mất response sau commit hoặc người dùng hủy sát thời điểm commit, hiển thị chưa xác định và refresh trước retry; không tự retry mutation mù. Refresh dùng request id/AbortController để kết quả cũ không ghi đè thư mục mới. Refresh định kỳ, tree, preview không nằm trong bản đầu.

Web không auto-download file do bên khác vừa upload. Session hết hạn trả về màn login, xóa CSRF trong RAM. Không đưa đường dẫn tuyệt đối server vào UI. Thêm link điều hướng tối thiểu vào index/admin, không thiết kế lại các trang cũ.

## 8. Kiểm thử và nghiệm thu

Runner mới không dùng server của người dùng: temp workspace, env fixture riêng, process HTTP và FTP riêng, cổng động/retry startup hữu hạn, teardown finally. Kiểm thử process khác nhau là bắt buộc để chứng minh khóa OS; thêm trường hợp hai store cùng JVM.

Các nhóm: config; storage; session; API; FTP integration; concurrency; upstream isolation; UI/manual. Kết quả báo PASS/FAIL/SKIP, không hardcode tổng test; SKIP junction/file symlink có giải thích và không được gọi đã kiểm chứng.

Nghiệm thu bắt buộc:

1. HTTP bật, FTP tắt: tất cả thao tác Web vẫn chạy.
2. Web upload và FTP RETR cùng file khớp SHA-256; FTP STOR và Web download khớp SHA-256.
3. Tên Unicode, khoảng trắng, `+`, `%`, file binary/HTML, file 0 byte đi qua cả hai kênh đúng bytes.
4. Trùng tên, write cùng tên, rename/delete trong lúc transfer có kết quả rõ; không xuất hiện file một phần trong kho.
5. Upload lỗi/over-limit/atomic unsupported không phá file cũ và không còn staging của lượt đó.
6. Root/child junction, traversal/ADS/device/control bị chặn; marker ngoài kho nguyên vẹn.
7. LAN request, thiếu session, guest mutation, CSRF/Origin/Host sai bị chặn bằng request API thật.
8. Browser đăng nhập rồi mở upstream HTML có script: script không truy cập được API; UI app vẫn chạy.
9. FileZilla và Java FTP Client hoạt động; Web không gọi FTP nội bộ; site manager/IIS/Fetch không bị mất chức năng.

## 9. Ngoài phạm vi

Không public Internet, không FTPS/SFTP, không user database, không quota cứng toàn root/ổ, không xóa cây, không preview, không tự đồng bộ trình duyệt, không tự xuất bản file thành website, không thay toàn bộ HTTP server bằng framework. Các giới hạn filesystem race với người có quyền OS và data connection cùng IP được ghi rõ; SafePaths không được gọi là bảo mật tuyệt đối.
