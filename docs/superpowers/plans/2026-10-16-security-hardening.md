# Local/LAN Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sửa các ranh giới bảo mật đã xác định để demo HTTP/FTP trên máy cá nhân và LAN tin cậy, giữ mục tiêu học lập trình mạng Java.

**Architecture:** Giữ các server/client hiện tại; bổ sung policy nhỏ dùng chung cho cấu hình mạng, kiểm tra local request, đường dẫn filesystem và đọc có giới hạn. Sửa FTP và HTTP theo từng nhóm độc lập, rồi tích hợp client và các bộ kiểm thử. Mặc định chỉ loopback; LAN chỉ mở những listener được cấu hình cụ thể.

**Tech Stack:** Java thuần, JDK 24, raw TCP Socket cho FTP, `com.sun.net.httpserver`, `java.net.http.HttpClient`, Swing, script `.bat`; không thêm dependency runtime.

## Global Constraints

- Phạm vi người dùng chọn: **máy cá nhân và LAN tin cậy**.
- Giữ các lệnh FTP, PASV/EPSV/PORT/EPRT, CRUD demo, multisite và Swing; được thay đổi mặc định truy cập để an toàn hơn.
- Giữ cổng mặc định HTTP 8080, FTP 2121, Mock IIS 8081; cho phép test dùng cổng tạm.
- Không trust-all TLS; không mở firewall hoặc đổi cấu hình IIS tự động.
- Không dùng dữ liệu thật trong test; không dùng server đang chạy của người dùng; không reset/xóa FTP root hoặc site của người dùng.
- Mọi test mới chạy từ working directory tạm và sở hữu rõ các server nó khởi tạo; không coi SKIP là PASS.
- Test filesystem Windows phải thực sự tạo junction. Nếu môi trường không hỗ trợ, ghi SKIP và chưa chốt tiêu chí junction; không tự báo đã an toàn.
- Chưa triển khai trong lượt lập kế hoạch này. Không cam kết mã hóa plain FTP trong LAN.
- Không xây hệ thống tài khoản/database/framework mới cho bài học; admin FTP cấu hình từ môi trường và HTTP quản trị giữ local-only.

## Quyết định thiết kế

### Mạng và quyền truy cập

`APP_BIND_ADDRESS` và `FTP_BIND_ADDRESS` mặc định `127.0.0.1`. Giá trị LAN phải là IP cụ thể thuộc interface máy; không dùng wildcard trong cấu hình demo. Các site mới/khi load thừa hưởng HTTP bind đã cấu hình. Mock IIS luôn local. `SimpleHttpServer.startServer(int)` vẫn tồn tại và chọn loopback để giữ caller cũ.

HTTP main khi bật LAN chỉ chia sẻ static content. `/api/sites`, `/admin.html`, `/api/students`, alias `/students`, `/fetch`, `/api/fetch`, `/iis` và các route ghi chỉ phục vụ caller loopback. `/api/time` có thể tiếp tục đọc từ LAN. Local policy còn kiểm tra Host thuộc host/IP được cấu hình và Origin cùng origin nếu Origin hiện diện; từ chối `Origin: null`. Native client không gửi Origin vẫn được dùng từ loopback. Không dựa vào X-Forwarded-For. Không bật CORS cho quản trị.

### Filesystem

Tạo helper `server.SafePaths` dùng cho FTP/static/site và đích download. Root được chủ động chọn, tạo và xác minh lúc startup. Từ root đã xác minh, từng thành phần hiện hữu phải không chuyển hướng filesystem; kiểm tra canonical/real path chứ không chỉ normalize. Junction Windows phải bị chặn kể cả khi `Files.isSymbolicLink` trả false. Với đích mới, xác minh cha hiện hữu rồi tạo từng thư mục và xác minh lại trước thao tác. Multisite phải kiểm tra cả root của site nằm trong base `sites/` đã xác minh.

Helper không được nhận đường dẫn HTTP thô hoặc tự URL decode: mỗi giao thức decode đúng một lần ở ranh giới, sau đó truyền path tương đối đã validate. Từ chối NUL/control, UNC/drive/ADS, tên thiết bị và tên Windows có hậu tố dot/space gây nhập nhằng. FTP không phải URL, do đó không biến dấu `+` trong tên thành dấu cách. Giữ regression test cho encoded traversal đang có; thay đổi tên chứa `%` phải được mô tả rõ và test.

Giới hạn thực tế: Java portable không bảo đảm loại bỏ mọi cuộc đua filesystem/hardlink trước đối thủ local có quyền sửa cùng thư mục. Root cần thuộc tài khoản chạy ứng dụng, không chia sẻ quyền ghi cho tài khoản không tin cậy. Không gọi helper này là chroot của hệ điều hành.

### Fetch/proxy

Khôi phục TLS mặc định. `/fetch` local-only. Public HTTP/HTTPS vẫn dùng được từ desktop local; chặn IP loopback/private/link-local/multicast/unspecified và các biểu diễn IPv4/IPv6 tương ứng cho target tổng quát. Ngoại lệ chỉ là origin IIS được cấu hình cụ thể, mặc định `http://localhost:80` và `http://localhost:8081`; không cho target cổng HTTP control hay multisite. Không chấp nhận userinfo trong URL.

Tắt auto redirect, kiểm tra từng Location theo cùng policy, tối đa 5 bước trong deadline tổng 10 giây. Kiểm tra mọi địa chỉ DNS trả về; không cho một kết quả public che kết quả private. Java HttpClient tự resolve khi kết nối nên kiểm tra trước không hoàn toàn triệt DNS TOCTOU; bảo đảm chính của phạm vi này là fetch chỉ dành cho caller local có Host/Origin hợp lệ. Internet hoặc DNS đối kháng cần proxy egress có kiểm soát và đánh giá riêng.

Raw body giữ phục vụ demo, nhưng response fetch/proxy luôn có `Content-Security-Policy: sandbox; default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline' http: https:; form-action 'none'; base-uri http: https:; frame-ancestors 'none'` và `X-Content-Type-Options: nosniff`. Không thêm `allow-scripts`/`allow-same-origin`. Đây là chính sách chỉ dành cho nội dung upstream, không gắn lên admin HTML của ứng dụng. JSON có đúng Content-Type và nosniff. Browser test phải chứng minh nội dung upstream không gọi được API quản trị.

### Giới hạn ban đầu

Các số dưới là mức demo khởi đầu, có thể chỉnh khi thực thi nếu kiểm thử chỉ ra nhu cầu cụ thể; không bỏ giới hạn để làm test pass.

| Tài nguyên | Giới hạn |
|---|---|
| HTTP request body | 64 KiB, vượt trả 413 cả Content-Length và chunked |
| Fetch/proxy upstream | 8 MiB trước giải nén và 8 MiB sau giải nén |
| HTTP handler đang xử lý | 32; vượt trả 503, không xếp hàng vô hạn |
| Tổng HTTP connection/header idle | 64 connection, 64 header fields, 16 KiB headers, 5 giây nhận request và 15 giây response |
| Site cùng lúc | 16; từ chối site thứ 17 bằng 409 |
| FTP control sessions | 16, thêm socket trả 421 rồi đóng |
| FTP control command | 4096 byte trước khi decode UTF-8; quá dài trả 500 rồi đóng |
| FTP login sai | 5 lần/phiên, sau đó 421 và đóng |
| FTP idle control | 300 giây |
| FTP data accept/connect/read | 30/10/60 giây |
| FTP transfer deadline tuyệt đối | 120 giây, đóng socket kể cả khi write bị block |
| FTP file upload/download | 64 MiB/file |
| FTP root tổng | 256 MiB, gồm dung lượng tạm/reservation; khóa reserve/release để upload đồng thời không vượt quota |
| FTP client listing | 2 MiB, tối đa 10.000 entry |
| FTP client reply | 4096 byte/dòng, tối đa 128 dòng và 256 KiB/reply |

Các system property được [tài liệu chính thức JDK 24](https://docs.oracle.com/en/java/javase/24/docs/api/jdk.httpserver/module-summary.html) hỗ trợ: `jdk.httpserver.maxConnections=64`, `sun.net.httpserver.maxReqHeaders=64`, `sun.net.httpserver.maxReqHeaderSize=16384`, `sun.net.httpserver.maxReqTime=5000`, `sun.net.httpserver.maxRspTime=15000`, `sun.net.httpserver.idleInterval=5`, `sun.net.httpserver.maxIdleConnections=16`. Truyền bằng `-D` ở script trước khi HttpServer được khởi tạo, kể cả test process; kiểm chứng trên đúng runtime cài đặt. maxReqTime/maxRspTime dùng milliseconds, idleInterval dùng seconds; timer có độ hạt của implementation nên không hứa ngắt đúng từng millisecond. Không giả định semaphore handler giải quyết slowloris trước dispatch.

## File map

| File | Trách nhiệm dự kiến |
|---|---|
| `src/server/SecurityConfig.java` (mới) | Đọc bind/config không bí mật, validation các giới hạn |
| `src/server/LocalRequestGuard.java` (mới) | Kiểm tra peer, Host, Origin cho route local |
| `src/server/SafePaths.java` (mới) | Kiểm tra đường dẫn thực và đường dẫn tạo mới |
| `src/server/BoundedInput.java` (mới) | Đọc tối đa byte, báo vượt giới hạn; dùng lại cho HTTP, FTP client |
| `src/server/FetchTargetPolicy.java` (mới) | Xác minh URI, địa chỉ IP, DNS và redirect |
| `src/server/ftp/*` | Cấu hình FTP, xác thực, peer, hạn mức và transfer an toàn |
| `src/server/SimpleHttpServer.java`, `SiteManager.java`, `MockIisServer.java` | Bind, hạn mức, shutdown |
| `src/server/RouterHandler.java`, `SitesApiHandler.java`, `StaticFileHandler.java` | Local policy, body/path validation |
| `src/server/WebFetchHandler.java`, `ProxyHandler.java` | TLS, target, bounded response, CSP |
| `src/client/FtpClient.java`, `FtpClientApp.java`, `FtpFile.java` | Đích download, command/data validation, file tạm |
| `src/client/WebFetchService.java`, `WebClientApp.java` | Giới hạn response; không tự tải tài nguyên HTML khi chỉ cần xem source |
| `src/client/SecurityRegressionTest.java` (mới) | Runner test bảo mật JDK-only, filter theo nhóm |
| `run_security_tests.bat` (mới) | Compile rồi chạy runner, truyền filter, giữ exit code |
| `src/client/CrossTestClient.java`, `CrossTestFtpClient.java` | Cập nhật assertion cho policy mới, cấu hình test riêng, tách SKIP |
| `README.md`, `PROJECT.md`, `TEST_READY.md`, `TEST_INFRA.md` | Cách chạy local/LAN và giới hạn được kiểm chứng |

Helper đặt trong package `server` để tương thích compile hiện tại; nếu client dùng helper filesystem/byte reader, chỉ import utility thuần JDK, không phụ thuộc vòng đời server. Không tạo interface/factory cho các helper này.

## Task 1 — P0: khóa mặc định mạng và cấu hình quyền FTP

**Files:** tạo SecurityConfig, LocalRequestGuard và runner; sửa các startup, SiteManager, RouterHandler, SitesApiHandler, FtpUserAuth, FtpClientApp và script chạy.

**Interfaces:** giữ API startup hiện tại; thêm overload nhận bind IP/config đã validate. FTP auth nhận secret/config từ instance, không đọc môi trường mỗi lần login. Runner `client.SecurityRegressionTest` nhận filter `exposure`, `paths`, `ftp-peer`, `fetch`, `limits`, `transfers`, `client`, `lifecycle`, hoặc `all`.

- [ ] Viết runner trước: tạo temp root, cổng 0, fake secret chỉ dùng test, khởi động và dừng server trong finally. Script trả nonzero nếu fail; SKIP được đếm riêng.
- [ ] Viết assertion red: mặc định không connect được qua IP LAN; LAN mode không đọc/ghi student/admin/fetch/proxy từ peer LAN; anonymous không được login khi tắt; admin không có secret phải bị vô hiệu; mật khẩu demo cũ không được dùng mặc định.
- [ ] Chạy `cmd /c run_security_tests.bat exposure`; ghi bằng chứng test fail trên hành vi cũ. Không coi lỗi compile hoặc chưa có runner là bằng chứng tái hiện lỗ hổng.
- [ ] Thêm bind explicit, local guard cho cả alias, Host/Origin policy và exact media type JSON (cho phép tham số charset hợp lệ); giữ UI admin dùng request same-origin. Với native client Origin vắng vẫn cần peer local và Host hợp lệ.
- [ ] Đọc `FTP_ADMIN_PASSWORD` lúc startup; không cấu hình thì admin bị tắt. `FTP_ALLOW_ANONYMOUS=false` mặc định. GUI không điền password; runner dùng cấu hình test riêng, không đổi biến môi trường toàn hệ thống. Sai login 5 lần đóng phiên; USER mới reset role/current directory/pending rename/data channel.
- [ ] Chạy lại nhóm exposure, test Origin khác/null, Host lạ, header X-Forwarded-For giả; cập nhật README cách mở IP LAN cụ thể. Commit riêng sau khi kiểm tra diff.

**Acceptance:** chỉ dịch vụ được chọn nghe LAN; tài khoản admin không còn password cố định; các route local không truy cập được qua peer LAN.

## Task 2 — P0: sửa filesystem confinement dùng chung

**Files:** tạo SafePaths; sửa FtpPathSandbox, FtpSessionHandler, StaticFileHandler, SiteManager và nhóm `paths` trong runner.

**Interfaces:** SafePaths nhận root được xác minh và path tương đối; trả Path đã kiểm tra hoặc ném SecurityException/IOException. Hai trường hợp tách rõ: đích hiện hữu và đích tạo mới. Không decode URL ở helper. Giữ resolveVirtual cho semantics FTP.

- [ ] Fixture tạo root/outside riêng và marker; tạo junction bằng `cmd /c mklink /J <fixture-link> <fixture-outside>`. Đường dẫn phải là fixture sở hữu bởi test; cleanup link trước, không walk xuyên link.
- [ ] Assert red: RETR/LIST/CWD/SIZE/MDTM/STOR/MKD/DELE/RMD/RNFR/RNTO qua link phải bị chặn; marker bên ngoài giữ nguyên. HTTP static và site cũng không đọc link. Thử link nằm ngay ở root site và link ở cha của file chưa tồn tại.
- [ ] Chạy `cmd /c run_security_tests.bat paths`, ghi trường hợp thoát root trước sửa.
- [ ] Cài policy đường dẫn thực như phần thiết kế; chuyển mọi file operation qua cùng helper, revalidate nguồn và đích khi rename, không chỉ RNFR. Không cho thao tác xóa/đổi tên root.
- [ ] Test thêm `../`, encoding, drive/UNC/ADS, tên thiết bị, dot/space cuối tên, Unicode, khoảng trắng và dấu `+`; test file hợp lệ vẫn truyền được. HTTP decode một lần và không dùng URLDecoder dạng form để thay `+` trong path.
- [ ] Chạy paths và các nhóm chức năng file hiện có; ghi rõ giới hạn local filesystem race/hardlink, rồi commit riêng.

**Acceptance:** tất cả marker ngoài root nguyên vẹn; HTTP không trả marker, FTP không đọc/ghi ngoài root; test junction Windows thực sự chạy.

## Task 3 — P0: ràng buộc peer của FTP data channel

**Files:** FtpDataChannel, FtpSessionHandler, FtpClient và nhóm `ftp-peer`.

**Interfaces:** `FtpDataChannel` nhận InetAddress của control peer lúc tạo; accept dùng deadline còn lại thay vì reset timeout sau mỗi peer sai. Client PASV dùng IP control peer, EPSV được ưu tiên.

- [ ] Fixture hai nguồn `127.0.0.1`/`127.0.0.2`: control nguồn thứ nhất, data nguồn thứ hai phải không nhận RETR và không ghi STOR. Thêm client active với server giả gửi data từ peer sai.
- [ ] Chạy `cmd /c run_security_tests.bat ftp-peer`, ghi lỗ hổng trước sửa.
- [ ] Server từ chối data peer khác rồi tiếp tục chờ peer đúng trong một deadline. PORT kiểm tra đủ 6 số 0..255, EPRT kiểm tra protocol/address/port; từ chối port <1024 hoặc >65535, bỏ ngoại lệ coi các loopback khác nhau như nhau.
- [ ] Client dùng EPSV/control peer, PASV chỉ lấy port hợp lệ. Active accept kiểm tra server peer, set read timeout, đóng listener/socket nếu gửi command, read reply hoặc accept lỗi.
- [ ] Kiểm tra nguồn sai đến trước rồi nguồn đúng vẫn truyền được; không có kết nối tới IP PASV do server giả chỉ định. Chạy lại PASV/EPSV/PORT/EPRT hợp lệ rồi commit riêng.

**Acceptance:** peer khác không đọc/ghi được dữ liệu; cả passive và active hợp lệ vẫn hoạt động.

## Task 4 — P0: bảo vệ fetch, TLS và nội dung upstream

**Files:** tạo FetchTargetPolicy; sửa WebFetchHandler, ProxyHandler, LocalRequestGuard, WebFetchService/WebClientApp và nhóm `fetch`.

**Interfaces:** policy nhận URI cùng danh sách origin IIS được phép, trả URI đã kiểm tra; redirect loop gọi lại policy mỗi bước. Helper response raw đặt CSP/nosniff trước khi gửi headers.

- [ ] Fixture HTTP public-policy giả, loopback admin, redirect và HTTPS self-signed từ keystore tạm. Không cần Internet thật để chạy nhóm test này.
- [ ] Assert red: LAN caller không fetch được admin; local fetch không tự gọi control port; redirect đến private/control target bị chặn; chứng chỉ chưa tin cậy không thành công; response raw luôn có CSP sandbox không allow-scripts/allow-same-origin.
- [ ] Chạy `cmd /c run_security_tests.bat fetch`, lưu assertion thất bại trước sửa.
- [ ] Bỏ X509TrustManager trust-all. Dùng truststore test chỉ cho positive TLS test; cert không tin, hết hạn và sai hostname đều fail. Không hạ HTTPS xuống HTTP khi lỗi.
- [ ] Áp dụng local guard cho fetch/proxy và target policy như thiết kế; Redirect.NEVER, max 5 hop, resolve Location tương đối, deadline tổng. Không forward cookie/Authorization của người gọi sang upstream.
- [ ] Gắn CSP/nosniff trên raw upstream và JSON nosniff, giữ Content-Type hợp lệ. Swing Rendered mặc định không tự tải CSS/ảnh từ HTML bên ngoài; người dùng vẫn xem Source và nội dung HTML cơ bản được.
- [ ] Browser kiểm chứng bằng trang marker thử gọi `/api/sites`: script upstream không chạy, form bị chặn, không có request quản trị; admin HTML của ứng dụng vẫn hoạt động. Kiểm tra /iis raw cũng có cùng policy.
- [ ] Chạy lại fetch, HTTP suite và WebFetchService tests; cập nhật các test cũ dùng self-fetch `/api/time` sang upstream fixture được phép thay vì nới policy. Commit riêng.

**Acceptance:** bypass đọc admin qua fetch không còn; TLS không tin cậy bị từ chối; browser không cấp quyền origin quản trị cho upstream HTML.

## Task 5 — P1: hạn mức tài nguyên, deadline và cleanup

**Files:** tạo BoundedInput; sửa SecurityConfig, HTTP handlers/startup, SiteManager, FTP server/session/data channel và client readers; nhóm `limits`, `lifecycle`.

**Interfaces:** reader giới hạn byte trong lúc đọc, không tin riêng Content-Length. Config chứa các mức trong bảng; FTP root quota có reserve/release dùng chung giữa các phiên.

- [ ] Test biên N và N+1 với HTTP body chunked, upstream gzip/deflate, command không newline, reply/listing dài, upload vượt file quota và hai upload đồng thời vượt root quota. Không dùng payload lớn thật: test config có giới hạn nhỏ hơn.
- [ ] Chạy nhóm limits trước sửa, ghi assertion red; không flood host.
- [ ] Thay readAllBytes/ofByteArray không giới hạn bằng bounded streaming/subscriber; ngừng đọc và đóng upstream khi vượt. Gzip/deflate cũng đọc qua giới hạn sau giải nén; lỗi giải nén trả 502 thay vì bỏ qua.
- [ ] Cài giới hạn concurrent/session/site và request body; reader FTP đếm byte trước newline/UTF-8. FTP quá command limit trả 500 và đóng; HTTP body lớn trả 413; upstream quá lớn trả 502; quá phiên FTP trả 421; quá handler HTTP trả 503.
- [ ] Watchdog theo deadline tuyệt đối đóng socket để ngắt cả write bị block. Không giữ monitor lock khiến close không thể ngắt accept/read/write. FTP stop đóng mọi session đang sống; HTTP shutdown dừng sites và executor.
- [ ] Cấu hình các system property JDK 24 nêu trên trong mọi script/test JVM trước khi HttpServer được khởi tạo; test header vượt 16 KiB, connection thứ 65 và request chậm bằng vài kết nối/giới hạn test nhỏ hơn có kiểm soát. Nêu riêng giới hạn chưa giải quyết được ở mức parser/listener nếu có.
- [ ] Quota tính cả reservation và temp files; release trong finally; dọn temp do ứng dụng sở hữu khi startup. Test fail-transfer không làm quota rò và server còn phục vụ request bình thường.
- [ ] Chạy limits/lifecycle, lặp 20 lần start-transfer-stop có kiểm tra socket/port/session count về baseline, rồi commit riêng.

**Acceptance:** vượt giới hạn bị từ chối có kiểm soát; không cần OOM/đầy ổ để test; stop không để session sống; không báo write timeout chỉ dựa vào SO_TIMEOUT.

## Task 6 — P1: bảo toàn file khi truyền lỗi

**Files:** FtpSessionHandler, FtpClient và nhóm `transfers`.

**Interfaces:** upload server và download client ghi temp sibling trong thư mục đích đã được SafePaths xác minh; commit sau khi hoàn tất; caller API downloadFile vẫn giữ chữ ký hiện tại.

- [ ] Assert red: file cũ giữ hash khi download chưa kết nối, RETR 550, data ngắt, completion 426, STOR timeout hoặc vượt quota. Test successful replacement và file 0 byte.
- [ ] Chạy `cmd /c run_security_tests.bat transfers`, ghi kích thước/hash thay đổi trước sửa.
- [ ] Dùng Files.createTempFile ở parent an toàn, ghi/đóng file, chờ FTP completion 226 cho download rồi mới move. STOR chỉ move sau nhận hoàn tất không lỗi và kiểm tra quota; gửi 226 sau commit thành công.
- [ ] Nếu ATOMIC_MOVE không được hỗ trợ thì fail an toàn, giữ file cũ và báo rõ; không fallback sang truncate/copy đích. Dọn temp trong finally. Không để LIST hiển thị file tạm ứng dụng đang dùng.
- [ ] Xác minh lại đường dẫn trước commit; test concurrent upload cùng tên có chính sách rõ: khóa theo đích hoặc từ chối phiên thứ hai, không trộn dữ liệu. Với interrupted transfer báo lỗi, không báo success.
- [ ] Chạy transfers và integrity SHA-256 của suite FTP rồi commit riêng.

**Acceptance:** lỗi có thể phát hiện không phá file đích cũ; truyền thành công giữ nguyên bytes. EOF sạch của upload không có expected size vẫn là giới hạn của giao thức, không hứa phát hiện mọi file ngắn.

## Task 7 — P1: chặn đầu vào nguy hiểm ở FTP client

**Files:** FtpClientApp, FtpClient, FtpFile, SafePaths và nhóm `client`.

**Interfaces:** GUI helper nhận Path localDirectory + basename từ LIST và trả đích an toàn; library downloadFile vẫn cho caller đáng tin chọn đích rõ ràng nhưng giữ atomic transfer. Mọi đường ghi command dùng chung validation/framing, kể cả active path hiện ghi trực tiếp.

- [ ] Fake FTP server gửi LIST chứa `../x`, `..\\x`, drive/UNC, ADS, control và tên hợp lệ Unicode/spaces; assert GUI target helper từ chối trước tạo file ngoài thư mục.
- [ ] Đưa CR/LF/NUL vào user/password/path đối với login/CWD/STOR/RETR/DELE/MKD và sendCommand; fake server phải không nhận lệnh phụ. Chạy cả active/passive.
- [ ] Chạy `cmd /c run_security_tests.bat client`, ghi regression red trước sửa.
- [ ] Validate basename trước hiển thị/ghép đích; không sửa tên độc hại thành tên khác âm thầm. Từ chối lệnh có CR/LF/NUL trước log và trước write; đảm bảo PASS luôn được che ở cả lỗi và observer.
- [ ] Áp dụng giới hạn listing/reply từ Task 5; đóng socket khi framing sai để tránh reply lệch phiên. Test Unicode/tên hợp lệ vẫn truyền được và UI hỏi overwrite như cũ.
- [ ] Chạy client/transfers/ftp-peer rồi commit riêng.

**Acceptance:** server không tin cậy không tạo đích ngoài thư mục download, không chèn lệnh, không đổi endpoint data sang host khác; file cũ vẫn được bảo vệ khi lỗi.

## Task 8 — P1: chốt regression, tài liệu và demo LAN

**Files:** CrossTestClient, CrossTestFtpClient, SecurityRegressionTest, các script và tài liệu trong file map.

- [ ] Chuyển mọi suite sang isolated fixture, cổng tạm/config test và finally cleanup. Không dò server người dùng rồi test ghi/xóa lên đó. Các probe IIS/FileZilla/Swing headful là optional, ghi SKIP riêng.
- [ ] Chạy toàn bộ từ workspace tạm theo lệnh sau, giữ log và exit code từng bước:

```bat
cmd /c compile.bat
cmd /c run_security_tests.bat all
cmd /c run_cross_test.bat
cmd /c run_cross_test_ftp.bat
```

- [ ] So sánh coverage theo SEC-01..SEC-11; test cũ đổi vì policy mới phải có lý do, không xóa assertion chỉ để giảm số fail. GUI headless không thay thế test Swing/FileZilla thủ công.
- [ ] Chạy FileZilla Client với Java FTP: login admin cấu hình mới, anonymous tùy chọn, PASV/EPSV, list/upload/download/rename/delete, xác nhận hash và lỗi quyền. FileZilla Server là kiểm thử tương thích tùy chọn cho Java client.
- [ ] Từ máy LAN thứ hai kiểm tra: listener mặc định không truy cập được; khi bật đúng IP thì FTP/static được chọn truy cập được, admin/student/fetch/proxy trả 403. Không mở firewall tự động; nếu firewall chặn thì ghi rõ bài test chưa hoàn thành.
- [ ] README ghi bind IP/cổng thực, env secret, anonymous default off, chỉ dữ liệu mẫu, plain FTP chưa mã hóa, lý do raw HTML bị hạn chế script. PROJECT/TEST_READY/TEST_INFRA tách functional/security/manual evidence.
- [ ] Review diff và lưu báo cáo cuối gồm test PASS/FAIL/SKIP, phạm vi chưa kiểm, giới hạn DNS TOCTOU/filesystem race. Không gọi hệ thống production-ready. Commit tích hợp sau khi kiểm chứng.

## Thứ tự và tiêu chí hoàn thành

1. Task 1 trước để đóng phơi lộ mặc định và tạo test harness.
2. Task 2 và 3 xử lý ranh giới FTP/filesystem; Task 4 xử lý HTTP. Có thể review riêng từng nhóm, nhưng kế hoạch không yêu cầu tự tạo subagent.
3. Task 5 trước khi chốt Task 6/7 để dùng cùng giới hạn và quota.
4. Task 8 là cổng kiểm chứng cuối.

P0 gồm Task 1–4. Chỉ chốt **đạt phạm vi demo local/LAN** sau toàn bộ Task 1–8, không còn fail trong security suite và test LAN/FileZilla bắt buộc đã thực hiện hoặc báo rõ chưa kiểm. Không có kết luận đã sửa nào chỉ dựa vào bộ 58/147 test cũ.

## Rollback và giới hạn phạm vi

Mỗi task là một commit nhỏ có test và tài liệu liên quan; rollback bằng revert commit tương ứng sau kiểm tra dependency. Không rollback sang wildcard bind, trust-all hay mật khẩu cố định để chữa lỗi tương thích; giữ local mode khi một nhóm chưa hoàn tất.

Không làm trong kế hoạch này: FTPS/SFTP, tài khoản nhiều người/database, công bố Internet, cấu hình firewall/IIS toàn máy, thay server bằng framework, refactor UI ngoài phần đầu vào và an toàn file. Nếu mục tiêu đổi sang Internet, phải lập thiết kế vận hành và giao thức mã hóa riêng trước khi mở dịch vụ.
