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
| `/iis/` | GET | Proxy sang IIS thật `:80`, dự phòng Mock `:8081` |

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
│   ├── client/       # CrossTestClient, FtpClient, FtpClientApp (Swing GUI), CrossTestFtpClient...
│   └── mock/         # MockIisServer (dự phòng cổng 8081 nếu máy không có IIS thật)
├── wwwroot/          # index.html, style.css (+ mọi file tĩnh web HTTP)
├── sites/            # Thư mục chứa các website độc lập (Multi-site)
├── ftp_workspace/    # Thư mục gốc sandbox của FTP Server (server_root/)
├── out/              # Bytecode .class sau khi biên dịch
└── *.bat             # Các file script chạy nhanh (HTTP & FTP)
```

---

## 🖥️ 7. Web Client (Swing) — xem source code qua WebFetch

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
