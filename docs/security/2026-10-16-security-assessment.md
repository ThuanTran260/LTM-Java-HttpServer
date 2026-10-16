# Đánh giá bảo mật HTTP, FTP và client

Ngày: 16/10/2026. Mã nguồn được kiểm tra: `c54e347` trên `main`.
Môi trường mục tiêu do người dùng chọn: **máy cá nhân và LAN tin cậy**.
Trạng thái: đánh giá và đề xuất; chưa sửa mã nguồn.

## Kết luận

Hệ thống phù hợp làm bài thực hành chức năng, nhưng chưa đạt mức có thể mở mặc định cho các máy khác trong LAN. Những kiểm tra bảo mật hiện có chặn được một số đầu vào phổ biến, nhưng chưa bảo vệ đầy đủ ranh giới filesystem, địa chỉ mạng và nội dung HTML từ bên ngoài.

Ưu tiên trước demo LAN: đóng truy cập ngoài ý muốn, bỏ mật khẩu admin cố định, sửa thoát root qua junction, xác minh peer của data connection, chặn đường vòng từ fetch đến quản trị, khôi phục kiểm tra TLS và cô lập HTML được fetch. Không xem kết quả test chức năng là chứng nhận an toàn.

Plain FTP vẫn truyền rõ mật khẩu và file. Kế hoạch LAN chỉ giảm phạm vi và sửa lỗi ứng dụng; không cung cấp tính bí mật trước người nghe lén trong LAN. Chỉ dùng tài khoản riêng cho demo và file mẫu.

## Phạm vi và bằng chứng

Đã đọc các handler HTTP, quản lý multisite, FTP server, FTP client/library/Swing, WebFetchService và các runner liên quan. Graph hiện có không chứa các node FTP cần thiết; kết luận FTP dựa trên source và thử nghiệm trực tiếp.

Các thử nghiệm dùng server riêng, cổng tạm và file marker trong thư mục tạm. Request qua địa chỉ LAN của chính máy kiểm tra chứng minh được nhánh kiểm tra địa chỉ không phải loopback; chưa thay thế bài kiểm thử firewall từ máy thứ hai.

| Kiểm tra | Kết quả |
|---|---|
| `cmd /c compile.bat` | Exit 0, `COMPILED OK - javac 24` |
| FTP runner trong bản sao `out`, working directory tạm | 58 pass, 0 fail, exit 0 |
| HTTP runner trong bản sao `out` và `wwwroot`, headless | 147 pass, 0 fail, exit 0 |
| Truy cập `/api/sites` trực tiếp qua IP LAN | 403 |
| Truy cập `/fetch?url=http://127.0.0.1:<http-port>/api/sites` qua IP LAN | 200, đọc được danh sách site |
| HTTP GET qua junction ra ngoài web root | 200, nhận `HTTP_OUTSIDE_MARKER` |
| FTP anonymous RETR qua junction ra ngoài FTP root | Nhận `AUDIT_OUTSIDE_MARKER` |
| FTP admin STOR qua junction ra ngoài FTP root | File marker được tạo ngoài root |
| Data connection PASV từ `127.0.0.2`, control từ `127.0.0.1` | Nhận `AUDIT_SAMPLE`, server trả 226 |
| Fetch HTTPS với chứng chỉ localhost tự ký từ keystore mới | 200, nhận `UNTRUSTED_CERT_MARKER` |
| POST student qua IP LAN, không có thông tin xác thực | 201 |
| Download vào file có dữ liệu khi FTP client chưa kết nối | IOException, nhưng file cũ trở thành 0 byte |
| Parse LIST có tên `../outside.txt`, ghép đích theo cách GUI đang dùng | Đích canonical nằm ngoài thư mục download |
| Fetch raw HTML có script marker vô hại | Script được giữ nguyên; response không có CSP sandbox |

HTTP runner headless bỏ qua nhóm Swing 95–99, không xác minh đầy đủ tương tác giao diện. FTP runner ghi probe IIS cổng 21 đóng là skipped-pass. Chưa thực hiện thao tác FileZilla thủ công và chưa kiểm thử khai thác bằng trình duyệt. Không chạy flood, không làm đầy ổ, không đọc file thật ngoài các fixture đã tạo.

Các log tạm để đối chiếu trong phiên làm việc này:

- HTTP: `C:/Users/Admin/AppData/Local/Temp/http-security-audit-ufhndmxj/results.json`.
- HTTP suite: `C:/Users/Admin/AppData/Local/Temp/http-suite-audit-3kvo2ebu/suite.log`.
- FTP suite: `C:/Users/Admin/AppData/Local/Temp/ftp-security-audit-9g4s7c85/suite.log`.

Thư mục tạm không phải tài liệu lưu trữ lâu dài. Bảng trên ghi lại các quan sát cần thiết; khi triển khai sửa cần chuyển các tình huống thành regression test trong repo.

## Phát hiện

### SEC-01 — Cao: sandbox HTTP/FTP theo đường dẫn chuỗi, không theo đích filesystem

Vị trí: `src/server/ftp/FtpPathSandbox.java:138`, `src/server/StaticFileHandler.java:22`; multisite dùng chung StaticFileHandler.

`normalize()` và `startsWith(root)` không phát hiện thư mục bên trong root là junction/symlink trỏ ra ngoài. File API tiếp tục đi theo liên kết. Đã tái hiện đọc ngoài root qua HTTP và FTP anonymous, ghi ngoài root qua FTP admin. Điều kiện là liên kết đã tồn tại trong root; thử nghiệm không chứng minh client FTP tự tạo được junction.

Sửa: kiểm tra root và các thành phần đường dẫn theo filesystem thực, từ chối liên kết/reparse point chuyển đích; kiểm tra cả cha hiện hữu khi đích chưa được tạo. Áp dụng mọi thao tác đọc, ghi, liệt kê, đổi tên và chuẩn bị thư mục site. Cần test junction trên Windows, không chỉ `Files.isSymbolicLink`.

### SEC-02 — Cao: fetch vượt hạn chế localhost của API quản trị

Vị trí: `src/server/WebFetchHandler.java:139-196`, `src/server/SitesApiHandler.java:34`.

Fetcher nhận request LAN nhưng tạo request mới đến loopback. `/api/sites` nhìn thấy peer local và cho đọc. Đã chứng minh 403 trực tiếp trở thành 200 qua fetch. Chỉ xác nhận bypass **quyền đọc**; fetch hiện dùng GET, chưa chứng minh tự nó gọi được POST/DELETE quản trị.

Sửa: fetch/proxy chỉ phục vụ caller local trong cấu hình demo, kiểm tra Host/Origin, hạn chế target và kiểm tra lại từng redirect. Không cho fetch gọi HTTP control port, port multisite hoặc mạng riêng tùy ý; ngoại lệ IIS chỉ dùng origin được cấu hình cụ thể.

### SEC-03 — Cao, suy luận từ response: HTML không tin cậy được phục vụ cùng origin quản trị

Vị trí: `src/server/WebFetchHandler.java:280-318`, `src/server/ProxyHandler.java:44-48`.

Raw response có thể chứa script của upstream nhưng được trình duyệt nhận tại origin của Java HTTP server. `<base>` chỉ đổi cách giải quyết URL tương đối; không cô lập quyền của script. Khi người dùng mở trang fetch độc hại trên máy server, script có thể nhắm API quản trị cùng origin. Đã xác nhận script không bị loại và không có CSP sandbox; chưa chạy browser exploit hay chứng minh thao tác quản trị qua script trong lượt đánh giá này. Proxy raw cũng cần cùng chính sách nội dung.

Sửa: giữ raw body để tương thích demo nhưng gắn CSP sandbox không cho script/same-origin và `X-Content-Type-Options: nosniff` trên mọi response raw từ fetch/proxy. Đặt chính sách chặt cho form, frame và kết nối phụ; kiểm tra hành vi trên browser thật. JSON vẫn là JSON; không bật CORS rộng.

### SEC-04 — Cao: bỏ kiểm tra chuỗi chứng chỉ HTTPS

Vị trí: `src/server/WebFetchHandler.java:34-60`.

`checkServerTrusted` rỗng làm mất kiểm tra nguồn tin cậy của chứng chỉ. Đã nhận response thành công từ HTTPS localhost với chứng chỉ tự ký chưa được cài vào truststore. Thử nghiệm không kết luận hostname verification cũng bị tắt.

Sửa: dùng trust manager mặc định của JDK; nếu bài demo cần CA nội bộ thì dùng truststore riêng được cấu hình rõ, không có tùy chọn trust-all.

### SEC-05 — Cao: quyền FTP admin dùng mật khẩu công khai cố định

Vị trí: `src/server/ftp/FtpUserAuth.java:20-31`, `src/client/FtpClientApp.java:63,136-137`.

Admin dùng mật khẩu demo được hardcode và GUI điền sẵn. Anonymous nhận mọi password và đọc chung root. Đây là hành vi hiện tại, không phải thông tin đăng nhập thật thu thập từ môi trường. Không có giới hạn thử đăng nhập theo phiên.

Sửa: cấp secret qua biến môi trường, tắt admin nếu chưa cấu hình, bỏ secret mặc định trong UI; anonymous mặc định tắt và chỉ bật rõ ràng với dữ liệu mẫu. Giới hạn lần login sai, không ghi PASS vào log. Secret qua môi trường vẫn ở dạng rõ trong tiến trình, chỉ phù hợp phạm vi demo đã chọn.

### SEC-06 — Cao: FTP passive data connection không xác minh peer

Vị trí: `src/server/ftp/FtpDataChannel.java:62`.

Socket đầu tiên đến cổng PASV được dùng cho truyền dữ liệu bất kể nguồn kết nối. Đã nhận file bằng peer khác control connection. Cần biết hoặc tìm được port và thắng cuộc đua. Kiểm tra IP chỉ giảm rủi ro giữa các host; không phân biệt các tiến trình cùng IP/NAT.

Sửa: truyền peer của control connection vào data channel; từ chối nguồn khác và tiếp tục accept trong cùng deadline. Không coi mọi địa chỉ loopback là một peer. Giữ cleanup và timeout.

### SEC-07 — Cao đối với demo LAN: mở dịch vụ và API ghi ngoài ý muốn

Vị trí: `src/server/SimpleHttpServer.java:10`, `src/server/SiteManager.java:317`, `src/server/ftp/SimpleFtpServer.java:40`, `src/mock/MockIisServer.java:12`, `src/server/RouterHandler.java:118-227`.

Listener bind mọi interface, dù thông báo thường ghi localhost. Student API không xác thực; đã tạo dữ liệu qua IP LAN. Đây có thể là hành vi có chủ ý của bài CRUD, nhưng không phù hợp nếu người dùng kỳ vọng chỉ file/static được chia sẻ. Không xác nhận dữ liệu sinh viên hiện tại là dữ liệu cá nhân thật.

Sửa: mặc định loopback, bật LAN bằng bind IP cụ thể. Student API và quản trị chỉ local trong thiết kế này; chỉ mở FTP/static site được chọn. Bind của từng site phải tuân thủ policy chung. Không tin `X-Forwarded-For` để quyết định local.

### SEC-08 — Trung bình: thiếu hạn mức tài nguyên

Vị trí: `src/server/RouterHandler.java:86,131,150,174,208`, `src/server/SitesApiHandler.java:109`, `src/server/ProxyHandler.java:25,41`, `src/server/WebFetchHandler.java:247,269,274`, `src/server/ftp/FtpSessionHandler.java:67,649`, `src/server/ftp/SimpleFtpServer.java:48`, `src/client/FtpClient.java:202,313,331`.

Body/listing và dữ liệu giải nén có thể được giữ toàn bộ trong RAM; upload không có hạn mức; số session không bị chặn. `SO_TIMEOUT` là timeout đọc, không đảm bảo deadline toàn phiên hoặc timeout ghi socket. Đây là phát hiện qua source, chưa chạy DoS/đầy ổ.

Sửa: giới hạn byte lúc đọc, giới hạn sau giải nén, hạn mức truyền file và phiên, deadline tuyệt đối đóng socket, giới hạn dòng/reply/listing. Giới hạn theo từng file không thay thế quota tổng thư mục/ổ đĩa.

### SEC-09 — Trung bình: transfer thất bại có thể phá file cũ

Vị trí: `src/client/FtpClient.java:247-255`, `src/server/ftp/FtpSessionHandler.java:648-649`.

Client mở FileOutputStream trước khi kiểm tra download có thể tiến hành; đã làm file cũ thành 0 byte khi client chưa kết nối. STOR server dùng TRUNCATE_EXISTING trước khi hoàn tất nhận file; nguy cơ mất file khi transfer lỗi là kết luận qua source.

Sửa: ghi file tạm cùng thư mục, chỉ thay đích sau thành công hoàn chỉnh và FTP completion phù hợp. Upload thiếu dữ liệu do lỗi/timeout không được commit. FTP chuẩn không cung cấp độ dài upload bắt buộc; EOF sạch vẫn có thể là file ngắn hơn ý định của người gửi, cần ghi rõ giới hạn.

### SEC-10 — Trung bình: client tin tên từ server và dữ liệu lệnh FTP

Vị trí: `src/client/FtpClientApp.java:585`, `src/client/FtpFile.java:87-155`, `src/client/FtpClient.java:291-305,488`.

LIST có tên `../outside.txt` được parse và GUI ghép trực tiếp vào local directory. Đã xác nhận parse và đích canonical thoát thư mục; chưa thực hiện click tải bằng GUI. Khi file đích đã tồn tại GUI có hỏi overwrite, nhưng đường dẫn ngoài thư mục chưa tồn tại vẫn nguy hiểm. Lệnh FTP được nối CRLF mà không từ chối CR/LF/NUL trong đối số; nguy cơ command injection qua đầu vào client là kết luận qua source.

Sửa: tên nhận từ LIST chỉ là basename; từ chối separator, drive/UNC, tên đặc biệt Windows và control character; kiểm tra đích local theo filesystem thực. Chặn CR/LF/NUL ở mọi đường ghi command, kể cả active mode. Không cho logging che giấu lệnh nối sau PASS.

### SEC-11 — Trung bình: FTP client tin endpoint của data connection

Vị trí: `src/client/FtpClient.java:428-439,491`.

PASV có thể trỏ client đến IP khác control peer; active mode chấp nhận socket mà không so peer. Server FTP độc hại có thể điều hướng kết nối hoặc bên khác giành data connection. Phát hiện từ source, chưa thử server FTP độc hại đầy đủ.

Sửa: ưu tiên EPSV; với PASV dùng IP control peer và chỉ lấy port hợp lệ. Active mode xác minh peer, deadline và cleanup khi accept lỗi. Không thêm tùy chọn cho kết nối FTP bên thứ ba trong bài này.

## Những bảo vệ đã có

- FTP chặn lệnh file trước login; guest không được STOR/DELE/MKD/RMD/rename.
- Traversal `../` thông thường, null và một số dạng encoding bị chặn.
- PORT/EPRT có kiểm tra IP đích so với control peer, dù vẫn cần siết giá trị port và quy tắc loopback.
- HTTP quản trị kiểm tra địa chỉ socket local, POST yêu cầu JSON; site name và port có validation.
- Admin HTML dùng `textContent` khi hiển thị dữ liệu.
- Có timeout đọc/kết nối và cơ chế đóng passive socket; không đồng nghĩa đã có mọi hạn mức cần thiết.

## Lựa chọn và đề xuất

1. Chỉ demo local: ít thay đổi vận hành, nhưng vẫn cần sửa đường dẫn, fetch/TLS và mất dữ liệu vì người dùng có thể tự mở nội dung không tin cậy.
2. **Gia cố local và LAN tin cậy — đề xuất và dùng làm cơ sở kế hoạch:** giữ TCP Socket/JDK 24, mặc định local, mở đúng dịch vụ cần demo, thêm regression tests thực tế.
3. Internet/production: cần thêm TLS cho dịch vụ, quản lý danh tính, vận hành, giám sát và đánh giá độc lập; chưa nằm trong yêu cầu được chọn.

Kế hoạch: `docs/superpowers/plans/2026-10-16-security-hardening.md`.
