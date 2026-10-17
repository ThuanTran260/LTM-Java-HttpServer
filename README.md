# BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)

Dự án hiện thực hóa trọn vẹn kiến thức **Bài 12: HTTP và HTTP Server** kết hợp **FTP Server chuẩn RFC 959** (Môn Lập Trình Mạng) bằng **100% Java thuần (JDK 24)** sử dụng **Raw TCP Sockets** và **Virtual Threads**, không cần cài thêm bất kỳ thư viện bên ngoài nào.

---

## ⚡ 1. Khởi Động Nhanh (1-Click)

### Dịch vụ HTTP & Web
1. **Bật Java HTTP Server:** Nhấp đúp chuột vào file **`run_server.bat`**  
   *(Server lắng nghe tại `http://localhost:8080/`)*
2. **Chạy kiểm thử HTTP tự động toàn bộ ma trận chéo:** Nhấp đúp vào **`run_cross_test.bat`**  
   *(Tự động kiểm tra 6 ca test: GET, POST, Static, 404, Proxy sang IIS)*
3. **Mở Web Client (Swing):** Nhấp đúp vào **`run_webclient.bat`** để xem source code qua WebFetch.

### Dịch vụ FTP Server & Client (RFC 959)
1. **Bật Java FTP Server (Chuẩn):** Nhấp đúp vào **`run_ftp_server.bat`**  
   *(Lắng nghe tại `127.0.0.1:2121`, tài khoản demo `student` mặc định bị tắt)*
2. **Bật Java FTP Server (Chế độ Demo):** Chạy `run_ftp_server.bat --demo`  
   *(Kích hoạt tài khoản `student/123456` phục vụ thực hành bài học)*
3. **Mở Java FTP Client (Swing GUI):** Nhấp đúp vào **`run_ftp_client.bat`**  
   *(Giao diện đồ họa kết nối, duyệt cây file, upload/download, đổi tên và xóa file)*
4. **Chạy kiểm thử tự động toàn bộ ma trận FTP (80 test cases):** Nhấp đúp vào **`run_cross_test_ftp.bat`**

---

## 🌐 2. Kiểm Tra Bằng Trình Duyệt (Firefox / Chrome)

Mở trình duyệt và truy cập 3 địa chỉ sau để thấy sự "móc nối chéo":

| Đường dẫn | Nội dung hiển thị | Bản chất hoạt động |
|:---|:---|:---|
| **`http://localhost:8080/`** | Trang *Welcome to My Website* | Trình duyệt kết nối trực tiếp **Java Server (:8080)** |
| **`http://localhost:80/`** | Trang *Windows Server IIS* | Trình duyệt kết nối trực tiếp **IIS thật của Windows (:80)** |
| **`http://localhost:8080/iis/`** | Vẫn là trang *Windows Server IIS* | **CẦU NỐI PROXY:** Trình duyệt gọi Java Server, Java Server tự forward sang IIS rồi trả lại cho trình duyệt |

---

## 🧪 3. Các File Kiểm Thử Riêng Lẻ (Chạy Bằng 1 Click)

Nếu giảng viên yêu cầu kiểm tra từng tính năng riêng:

- **`run_client_get.bat`**: Demo Java Client gửi `GET /api/time` (nhận JSON thời gian).
- **`run_client_post.bat`**: Demo Java Client gửi `POST /api/message` (nhận `201 Created`).
- **`run_client_error.bat`**: Demo phân biệt giữa **HTTP Error** (mã lỗi 4xx/5xx có phản hồi từ server) và **Lỗi kết nối/Timeout** (không có phản hồi từ server).
- **`run_cross_test.bat`**: Chạy toàn bộ 6 bài kiểm thử ma trận chéo HTTP.
- **`run_cross_test_ftp.bat`**: Chạy toàn bộ 80 bài kiểm thử ma trận chéo FTP (8 Tier: Handshake, Auth, Pre-auth, Data Channels, File Ops, Sandbox Security, SHA-256 Integrity, Socket Leaks).

### Các API có sẵn trên `http://localhost:8080/`

| Đường dẫn | Method | Kết quả |
|:---|:---|:---|
| `/` hoặc `/index.html` | GET | Trang chủ từ `wwwroot/` (200 `text/html`) |
| `/style.css`, `/about.html`, `*.png`, ... | GET | Bất kỳ file tĩnh nào trong `wwwroot/` (Content-Type tự theo đuôi file) |
| `/api/time` | GET | `{"time":"HH:mm:ss"}` |
| `/api/students` | GET | Danh sách sinh viên (`?id=` tra 1 SV, `?name=` & `?age=` lọc) |
| `/api/students/{id}` | GET | Một sinh viên theo ID |
| `/api/students` | POST | Thêm sinh viên → `201 Created` |
| `/api/students/{id}` | PUT | Cập nhật sinh viên theo ID |
| `/api/students/{id}` | DELETE | Xóa sinh viên theo ID |
| `/api/message` | POST | Nhận JSON, trả `201`; sai `Content-Type` → `415` |
| `/iis/` | GET | Proxy sang IIS thật `:80`; trả `502` nếu không kết nối được IIS |

---

## 🛑 4. Quản Lý IIS Thật Của Windows (w3svc) Bằng Lệnh

Mở **Command Prompt (chuột phải chọn Run as Administrator)** hoặc **PowerShell (Admin)**:

- **Tắt IIS (để giải phóng cổng 80 khi không học nữa):**
  ```cmd
  net stop w3svc
  ```
  *(hoặc: `iisreset /stop`)*

- **Bật lại IIS (khi cần học/demo tiếp):**
  ```cmd
  net start w3svc
  ```
  *(hoặc: `iisreset /start`)*

- **Kiểm tra trạng thái IIS:**
  ```cmd
  sc query w3svc
  ```
  *(Hiện `STATE: 4 RUNNING` là đang chạy, `STATE: 1 STOPPED` là đã tắt hoàn toàn)*.

---

## 🛠️ 5. Biên Dịch Lại Mã Nguồn (Khi Có Sửa Code)

Nhấp đúp vào file:
👉 **`compile.bat`**  
*(Sử dụng trình biên dịch `javac` sẵn có của máy, mã nguồn dịch vào thư mục `out/`)*.

---

## 📁 6. Cấu Trúc Mã Nguồn

```text
Http server/
├── src/
│   ├── server/       # SimpleHttpServer, RouterHandler, ProxyHandler, StaticFileHandler, SafePaths, SiteManager...
│   │   └── ftp/      # SimpleFtpServer, FtpSessionHandler, FtpUserAuth, FtpPathSandbox (RFC 959)
│   ├── service/      # StudentService (CRUD sinh viên dạng JSON, hỗ trợ lọc theo name/age)
│   ├── model/        # Student POJO
│   └── client/       # CrossTestClient, FtpClient, FtpClientApp (Swing GUI), CrossTestFtpClient...
├── wwwroot/          # index.html, style.css (+ mọi file tĩnh web HTTP)
├── sites/            # Thư mục chứa các website độc lập (Multi-site)
├── ftp_workspace/    # Thư mục gốc sandbox của FTP Server (server_root/)
├── out/              # Bytecode .class sau khi biên dịch
└── *.bat             # Các file script chạy nhanh (HTTP & FTP)
```

---

## 🖥️ 7. Web Client (Swing) — xem source code qua WebFetch

Project không còn Mock IIS ở cổng `8081`. `/iis/` chỉ chuyển tiếp đến IIS thật ở cổng `80`. Fetch vẫn lấy nội dung từ URL được nhập, ví dụ `http://localhost/` khi IIS đã bật hoặc `http://localhost:9001/demo.html` khi website Java tương ứng đang chạy. URL đích chưa chạy sẽ trả `502`; Fetch không tự bật web server đích.

Các test Fetch tự tạo upstream fixture trên cổng loopback ngẫu nhiên rồi đóng sau khi chạy. Test IIS thật được bỏ qua và ghi `[SKIP]` khi cổng `80` chưa hoạt động; trường hợp proxy không kết nối được IIS vẫn được kiểm tra trả `502`.

Ứng dụng desktop Java thuần (Swing) đóng vai **client**: gọi `GET http://localhost:8080/fetch?url=<URL>` của Java Server rồi hiển thị **mã nguồn** trang web nhận về.

1. Bật server: **`run_server.bat`**
2. Mở app: **`run_webclient.bat`** (có thể truyền địa chỉ server khác, ví dụ `run_webclient.bat http://localhost:8080`)

| Thành phần | Chức năng |
|:---|:---|
| Ô **Server WebFetch** | Địa chỉ Java Server (mặc định `http://localhost:8080`) |
| Ô **URL** + nút **Fetch** (hoặc Enter) | URL cần xem (`http://` hoặc `https://`); ghi nhớ 20 URL gần nhất |
| Checkbox **format=json** | Gọi `/fetch?format=json`: tab Source hiện JSON metadata (`targetUrl`, `status`, `contentType`, `contentLength`, `body`) |
| Tab **Source** | Nội dung đúng như server trả về, kèm chú thích: server tự chèn thẻ `<base href>` vào HTML |
| Tab **Rendered** | Trang HTML render bằng `JEditorPane` (chỉ hiển thị tốt HTML cơ bản) |
| Nút **Save...** | Lưu nội dung tab Source ra file UTF-8 |
| Thanh trạng thái | Status, Content-Type, độ dài, chế độ raw/json; lỗi (ví dụ chưa bật server) hiện màu đỏ |

Kiểm thử tự động: `java -cp out client.CrossTestClient` (mục 9 và 10 kiểm tra `WebFetchService` và `WebClientApp`).

---

## 🌐 8. Quản lý nhiều web (Sites)

Java Server `:8080` có thể quản lý **nhiều web cùng lúc**, giống IIS Sites: mỗi web có **thư mục riêng** `sites/<tên>/` và chạy trên **cổng riêng**. Thêm / bật / tắt / xóa web **khi server đang chạy**; danh sách lưu trong `sites.json` nên khởi động lại vẫn còn (các web đã bật tự chạy lại).

- **Trang quản trị:** mở **`http://localhost:8080/admin.html`** (chỉ dùng được từ máy local).
- Bấm **Xem file** để xem cây file web trong `sites/<tên>/`. Bấm vào file để mở trên cổng của site (site phải đang bật).
- Thêm web `blog` ở cổng 9001 → mở `http://localhost:9001/`. Sửa nội dung web trong `sites/blog/` (file `index.html` mặc định được tạo sẵn).

| API (chỉ từ máy local) | Kết quả |
|:---|:---|
| `GET /api/sites` | Danh sách web (tên, cổng, trạng thái, địa chỉ) |
| `POST /api/sites` `{"name":"blog","port":9001}` | `201` — tạo thư mục và chạy web; `409` nếu trùng tên/cổng hoặc cổng đang bị dùng; `400` nếu sai tên (a-z, 0-9, `-`) hoặc cổng ngoài 1024–65535 |
| `POST /api/sites/{tên}/start` · `/stop` | Bật / tắt web (cần header `Content-Type: application/json`) |
| `DELETE /api/sites/{tên}` | Xóa khỏi danh sách (thư mục file được giữ lại) |
| `GET /api/sites/{tên}/files` | Cây file web của site (chỉ file mà site phục vụ được) |

Ví dụ: `curl -X POST http://localhost:8080/api/sites -H "Content-Type: application/json" -d "{\"name\":\"blog\",\"port\":9001}"`

Kiểm thử tự động: `java -cp out client.CrossTestClient` (mục 11, test S1–S49).

---

## 🚀 9. Java FTP Server (RFC 959) & Swing GUI Client

Hệ thống truyền nhận tệp chuẩn **RFC 959** được viết bằng **100% Java thuần (JDK 24)** sử dụng **Raw TCP Sockets** và **Virtual Threads**, không cần bất kỳ thư viện ngoài nào.

### 9.1 Khởi động nhanh (1-Click)

| File Batch | Chức năng | Cổng / Giao thức |
|:---|:---|:---|
| **`run_ftp_server.bat`** | Bật FTP Server ở chế độ chuẩn (tài khoản `student` bị tắt) | `127.0.0.1:2121` |
| **`run_ftp_server.bat --demo`** | Bật FTP Server chế độ Demo (kích hoạt tài khoản `student`) | `127.0.0.1:2121` |
| **`run_ftp_server.bat --help`** | Xem hướng dẫn sử dụng và các tham số | Thoát ngay với mã 0 |
| **`run_ftp_client.bat`** | Mở ứng dụng **FTP Client GUI** viết bằng Java Swing | Giao diện đồ họa kết nối `:2121` |
| **`run_cross_test_ftp.bat`** | Chạy toàn bộ 80 ca kiểm thử ma trận chéo E2E RFC 959 | Báo cáo chi tiết 0 FAIL |

---

### 9.2 Phân quyền tài khoản (Role-Based Authorization)

Thư mục gốc lưu trữ tệp của FTP Server: `ftp_workspace/server_root/`

| Tài khoản | Mật khẩu mặc định | Vai trò (Role) | Quyền hạn |
|:---|:---|:---|:---|
| **`admin`** | `admin123` *(hoặc tự đặt trong `ftp.local.bat`)* | **ADMIN** | Toàn quyền: Đọc, Ghi (`STOR`), Đổi tên (`RNFR`/`RNTO`), Xóa (`DELE`, `RMD`), Tạo thư mục (`MKD`). |
| **`student`** | `123456` *(Slide 30 standard)* | **USER** | Quyền đọc/ghi đầy đủ khi chạy với `--demo`. Chế độ chuẩn không có `--demo` sẽ bị khóa tuyệt đối (`530 Login incorrect`). |
| **`anonymous`** | Bất kỳ (VD: `guest@test.com`) | **GUEST** | Chỉ đọc (`RETR`, `LIST`, `PWD`, `CWD`). Mọi hành vi ghi/xóa/đổi tên đều bị từ chối với mã `550 Permission denied`. |

> [!TIP]
> **Tùy chỉnh mật khẩu Admin riêng cục bộ**:
> Tạo file `ftp.local.bat` trong thư mục dự án với nội dung:
> ```bat
> set "FTP_ADMIN_PASSWORD=MatKhauRiengCuaBan"
> ```
> File này đã được đưa vào `.gitignore` để bảo vệ bí mật, không bị đẩy lên Git.

---

### 9.3 Hướng dẫn kết nối bằng FileZilla Client

1. Mở **FileZilla**, điền thông tin kết nối nhanh (Quickconnect):
   - **Host:** `127.0.0.1`
   - **Port:** `2121`
   - **Username:** `admin` (hoặc `student` nếu bật `--demo`, hoặc `anonymous`)
   - **Password:** `admin123` (hoặc `123456`, hoặc `guest@test.com`)
2. Bấm **Quickconnect**: FileZilla kết nối, nhận mã chào mừng `220 Simple Java FTP Server ready (JDK 24 Virtual Threads)` và hiển thị cây thư mục remote.
3. Thao tác kiểm tra thực tế:
   - **LIST / Duyệt tệp:** Danh sách hiển thị theo định dạng chuẩn UNIX `ls -l` với kích thước, ngày giờ và quyền hạn.
   - **Upload / Download:** Kéo thả tệp bất kỳ giữa máy tính và server (hỗ trợ cả Passive mode `PASV` lẫn Active mode `PORT`).
   - **Đổi tên tệp:** Chuột phải $\rightarrow$ Rename $\rightarrow$ Server xử lý qua `350 RNFR` $\rightarrow$ `250 RNTO`.
   - **Xóa tệp / thư mục:** Chuột phải $\rightarrow$ Delete $\rightarrow$ Server xử lý qua `250 DELE` / `250 RMD`.

---

### 9.4 Các tính năng bảo mật nâng cao (Security Hardening)

- **Chroot Sandbox (`SafePaths`)**: Chống tấn công Path Traversal (`../`, `..\`, `%2e%2e`), Null-byte injection, ổ đĩa Windows (`C:\`), NTFS Alternate Data Streams (`:`), và tên thiết bị đặc biệt (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`).
- **Ngăn chặn NTFS Junction & Symlink Escape**: Từ chối liên kết trỏ ra ngoài sandbox; kiểm tra nghiêm ngặt chính thư mục root không được là junction/link.
- **Tải tệp nguyên tử (Atomic Download)**: Java FTP Client tải dữ liệu vào tệp tạm thời `.tmp`, đóng luồng trước khi di chuyển nguyên tử (`ATOMIC_MOVE`), tự động xóa tệp rác và bảo toàn nguyên vẹn mã băm SHA-256 của tệp đích nếu gặp sự cố truyền nhận (426/550).
- **Chống Brute-Force**: Tự động ngắt kết nối (`421`) nếu nhập sai thông tin xác thực liên tiếp 5 lần.
- **Chống Active Mode Bounce Attack**: Chỉ chấp nhận lệnh `PORT` trỏ về đúng địa chỉ IP của client điều khiển.

---

### 9.5 Dừng dịch vụ an toàn sau buổi thực hành

1. Trong FileZilla, chọn **Server → Disconnect**. Đóng ứng dụng Java FTP Client GUI.
2. Tại cửa sổ terminal đang chạy **`run_ftp_server.bat`**, nhấn tổ hợp phím **Ctrl+C**. Khi CMD hỏi `Terminate batch job (Y/N)?`, nhập **Y** và nhấn **Enter**.
3. Cổng `2121` được giải phóng hoàn toàn ngay lập tức. Dữ liệu upload nằm an toàn trong thư mục `ftp_workspace/server_root/`.

---

## 🗄️ 10. Quản Lý Tệp Qua Web (Web File Explorer & Shared Storage)

Trang quản lý tệp tin chuyên biệt (**`http://localhost:8080/files.html`**) hoạt động trực tiếp qua HTTP REST API (`/api/files`), dùng chung kho lưu trữ vật lý với FTP Server (`ftp_workspace/server_root/`), hoàn toàn độc lập với trang quản trị website (`/admin.html`).

### 10.1 Khởi động và cấu hình chung

1. **Khởi động dịch vụ:**
   - Bật Web Server: `run_server.bat` (lắng nghe tại `http://localhost:8080/`)
   - Bật FTP Server: `run_ftp_server.bat` (lắng nghe tại `127.0.0.1:2121`)
   - Cả hai script đều tự động nạp cấu hình mật khẩu từ `ftp.local.bat`.
   - Web hoạt động độc lập khi FTP Server tắt, và FTP hoạt động độc lập khi Web Server tắt.

2. **Cấu hình môi trường (`FileStoreConfig`):**

| Biến môi trường | Mặc định | Ý nghĩa |
|:---|:---|:---|
| `FTP_ROOT` | `ftp_workspace/server_root` | Thư mục kho dùng chung cho cả Web và FTP |
| `FTP_MAX_FILE_BYTES` | `67108864` (64 MiB) | Giới hạn dung lượng tối đa mỗi tệp tải lên |
| `WEB_FILES_ALLOW_GUEST` | `false` | Cho phép tài khoản `guest` đăng nhập chỉ đọc trên Web |
| `FTP_ADMIN_PASSWORD` | *(không mặc định)* | Mật khẩu admin cục bộ cấu hình trong `ftp.local.bat` |

> [!NOTE]
> Web không sử dụng mật khẩu fallback mặc định. Nếu `FTP_ADMIN_PASSWORD` chưa được đặt, Web API trả về `503 SETUP_REQUIRED` và giao diện sẽ hướng dẫn cấu hình `ftp.local.bat` rồi khởi động lại server.

### 10.2 Xác thực, phiên làm việc và bảo mật

- **Ranh giới Localhost:** Chỉ chấp nhận kết nối từ loopback socket (`127.0.0.1`, `::1`, `localhost`). Các request từ mạng ngoài bị từ chối `403 FORBIDDEN`. Không tin cậy `X-Forwarded-For`.
- **Phiên làm việc (Session):** Quản lý hoàn toàn trong bộ nhớ RAM server, tự hủy khi restart server. Cookie `FileExplorerSession` (`HttpOnly; SameSite=Strict; Path=/api/files`). Hết hạn sau 30 phút không hoạt động (idle) hoặc tối đa 8 giờ.
- **Đăng xuất (Logout):** Nút **Đăng xuất** dùng cho cả `admin` và `guest`; request logout bắt buộc CSRF hợp lệ, server thu hồi phiên ngay và trả cookie hết hạn (`Max-Age=0`). Cookie cũ sau đó nhận `401` và tải lại trang cũng đưa về màn hình đăng nhập.
- **Chống CSRF:** Mọi thao tác ghi (upload, mkdir, rename, delete) yêu cầu bắt buộc header `X-File-Explorer: 1` và token `X-CSRF-Token` khớp với phiên đăng nhập.
- **Phân quyền người dùng:**
  - `admin`: Toàn quyền duyệt, tải về, tạo thư mục, đổi tên, xóa và tải lên tệp tin.
  - `guest`: Chỉ đọc (duyệt thư mục, tải về tệp tin). Mọi thao tác ghi bị chặn `403 FORBIDDEN` ở cả UI lẫn API.
- **Cô lập nội dung không tin cậy (Upstream Isolation):** Toàn bộ nội dung thô trả về từ `/fetch` và proxy IIS được gắn chính sách CSP sandbox nghiêm ngặt (`sandbox; form-action 'none'; frame-ancestors 'none'`) cùng `X-Content-Type-Options: nosniff`. Trình duyệt sẽ ngăn chặn tuyệt đối các mã script trong trang web bên ngoài cố tình truy cập vào `/api/files`.

### 10.3 Giới hạn kỹ thuật và an toàn dữ liệu

- **Không ghi đè qua Web:** Web upload từ chối ghi đè tệp tin đã tồn tại (`409 CONFLICT`). FTP `STOR` hỗ trợ thay thế nguyên tử tệp tin cũ.
- **Giới hạn đồng thời:** Tối đa 2 tiến trình upload HTTP đồng thời trên server. Upload có deadline 120 giây và watchdog đọc 30 giây.
- **Không xóa thư mục có nội dung:** Chỉ cho phép xóa thư mục rỗng (`204`). Thư mục có chứa tệp tin hoặc thư mục con bị từ chối với `409 NOT_EMPTY`.
- **Không xem trước (Preview):** Tải về tệp tin luôn trả về dạng luồng nhị phân với `Content-Type: application/octet-stream` và `Content-Disposition: attachment`, ngăn chặn thực thi mã độc.
- **Phân trang thư mục:** Danh sách hiển thị theo từng trang (1..200 tệp/trang) với nút **Trang trước / Trang sau** và số trang; tổng tối đa 10.000 tệp mỗi thư mục. Thư mục vượt quá 10.000 mục trả về `422`; trang ngoài phạm vi được server sửa về trang hợp lệ.
- **Upload khi kết quả không chắc chắn:** Nếu upload bị ngắt kết nối/timeout/hủy gần lúc hoàn tất, giao diện không tự thử lại và không xếp thêm tệp trùng vào thư mục đích; thư mục được đánh dấu "chưa xác định" và trạng thái thật được đối soát ở lần làm mới/duyệt kế tiếp. Server không hứa hủy upload đang chạy.
- **Định danh thư mục cha (Fail-closed khi thiếu định danh):** Upload chỉ công bố tệp khi server đọc được định danh đáng tin cậy của thư mục cha và xác nhận nó không đổi giữa lúc nhận và lúc công bố. Nền tảng khác dùng `fileKey` của provider; riêng Windows, `BasicFileAttributes.fileKey()` của JDK luôn trả `null`, nên server đọc đúng mã tệp NTFS (volume serial + file ID 128-bit) mà `Files.isSameFile` dùng nội bộ qua API `java.lang.foreign` chuẩn JDK. Nếu định danh không đọc được (filesystem không hỗ trợ hoặc native access bị chặn), hoặc thư mục cha bị thay/thay thế, upload bị từ chối `409 CONFLICT` và không xuất bản gì. Trên JDK 24, lần dùng đầu tiên in cảnh báo `WARNING: A restricted method in java.lang.foreign...` ra stderr; thêm `--enable-native-access=ALL-UNNAMED` khi chạy server/test để tắt cảnh báo. Cơ chế này không cam kết chống người dùng hệ điều hành đang đua sửa đổi hệ thống tệp.
- **Khóa đa tiến trình (Cross-Process Concurrency):** Mọi thao tác ghi được bảo vệ bởi khóa đồng bộ trong JVM kết hợp khóa tệp OS trên `write.lock` (`<root>.state/write.lock`), đảm bảo không xung đột dữ liệu giữa hai tiến trình Web và FTP Server.
- **Dọn dẹp tệp tạm (Staging):** Tệp upload được nhận tại thư mục riêng `<root>.state/staging/` trước khi chuyển nguyên tử vào kho. Nếu server gặp sự cố crash, hãy **dừng cả hai tiến trình** trước khi xóa các tệp `.tmp` tồn đọng.

### 10.4 Chạy kiểm thử tự động toàn diện

Chạy toàn bộ 88 ca kiểm thử ma trận chéo Web File Explorer & Shared Storage:
```cmd
run_cross_test_files.bat all
```
Hoặc chạy từng nhóm kiểm thử riêng lẻ:
- `run_cross_test_files.bat config`: Kiểm tra cấu hình và biến môi trường (9 tests).
- `run_cross_test_files.bat storage`: Kiểm tra `SharedFileStore` và khóa nguyên tử (20 tests).
- `run_cross_test_files.bat ftp`: Kiểm tra tích hợp FTP qua wire protocol (12 tests).
- `run_cross_test_files.bat session`: Kiểm tra đăng nhập, session, CSRF và local guard (17 tests).
- `run_cross_test_files.bat isolation`: Kiểm tra chính sách cô lập nội dung upstream CSP sandbox (3 tests).
- `run_cross_test_files.bat api`: Kiểm tra toàn diện HTTP REST API `/api/files` qua wire (18 tests).
- `run_cross_test_files.bat concurrency`: Kiểm tra đồng thời hai tiến trình Web & FTP với mã băm SHA-256 (9 tests).
