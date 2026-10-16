# BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)

Dự án hiện thực hóa trọn vẹn kiến thức **Bài 12: HTTP và HTTP Server** (Môn Lập Trình Mạng) bằng **100% Java thuần (JDK 24)**, không cần cài thêm bất kỳ thư viện bên ngoài nào.

---

## ⚡ 1. Khởi Động Nhanh (1-Click)

1. **Bật Java Server:** Nhấp đúp chuột vào file **`run_server.bat`**  
   *(Server lắng nghe tại `http://localhost:8080/`)*
2. **Chạy kiểm thử tự động toàn bộ ma trận chéo:** Nhấp đúp vào **`run_cross_test.bat`**  
   *(Tự động kiểm tra 6 ca test: GET, POST, Static, 404, Proxy sang IIS)*

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
- **`run_cross_test.bat`**: Chạy toàn bộ 6 bài kiểm thử ma trận chéo.

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
│   ├── server/       # SimpleHttpServer, RouterHandler, ProxyHandler, StaticFileHandler, ServerLogger, SiteManager, SiteFileHandler, SitesApiHandler
│   ├── service/      # StudentService (CRUD sinh viên dạng JSON, hỗ trợ lọc theo name/age)
│   ├── model/        # Student POJO
│   ├── client/       # HttpClientGetDemo, HttpClientPostDemo, HttpClientErrorDemo, CrossTestClient, WebFetchService, WebClientApp (Swing)
│   └── mock/         # MockIisServer (dự phòng cổng 8081 nếu máy không có IIS thật)
├── wwwroot/          # index.html, style.css (+ mọi file tĩnh khác: html, css, png, json, txt)
├── out/              # Bytecode .class sau khi biên dịch
└── *.bat             # Các file script chạy nhanh
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

## 9. Dừng hệ thống sau buổi thực hành

Thực hiện theo thứ tự sau để tránh ngắt một lượt truyền file đang chạy:

1. **Chờ upload/download hoàn tất** trong FileZilla và Java FTP Client.
2. Trong FileZilla, chọn **Server → Disconnect**. Ngắt kết nối và đóng Java FTP Client; đóng Web Client nếu đang mở.
3. Tại cửa sổ terminal chạy **Java FTP Server** (`:2121`), nhấn **Ctrl+C**.
4. Tại cửa sổ terminal chạy **Java HTTP Server** (`:8080`), nhấn **Ctrl+C**. Các website Java chạy trong cùng tiến trình, ví dụ site `:9001`, cũng dừng theo. Cấu hình trong `sites.json` vẫn được giữ lại.
5. Nếu tự mở **Mock IIS** (`:8081`) trong một terminal riêng, nhấn **Ctrl+C** tại terminal đó.
6. Nếu CMD hỏi `Terminate batch job (Y/N)?`, nhập **Y** rồi Enter.

### IIS thật của Windows

IIS là dịch vụ riêng, không tự dừng khi tắt Java Server. Nếu đã bật IIS cho buổi thực hành và không có ứng dụng khác cần dùng nó, mở terminal **Run as administrator** rồi chạy:

```cmd
net stop w3svc
```

Lệnh này dừng dịch vụ web IIS và có thể ảnh hưởng đến các website IIS khác trên máy. Nếu có dùng thêm IIS FTP, dịch vụ FTP đó cũng là một dịch vụ riêng; nó không phải Java FTP Server `:2121`.

### Kiểm tra các cổng đã ngừng lắng nghe

Trong PowerShell, chạy:

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in 2121, 8080, 8081, 9001 } |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

- **Không có kết quả:** không còn listener trên các cổng vừa kiểm tra.
- **Vẫn có kết quả:** dùng `OwningProcess` để xác định tiến trình còn chạy; cổng đó cũng có thể thuộc ứng dụng khác. Thêm các cổng site khác vào danh sách nếu đã tạo thêm website.
- Nếu vừa dừng IIS, có thể thêm cổng `80` vào danh sách để kiểm tra; một ứng dụng khác vẫn có thể sử dụng cổng này.

### Giữ dữ liệu để chạy lại

- Giữ `sites/`, `sites.json`, `ftp_workspace/` và `wwwroot/` để bảo toàn website và file demo. Không cần xóa `out/` khi chỉ dừng hệ thống.
- Giữ `ftp.local.bat` nếu muốn dùng lại mật khẩu FTP đã cấu hình. File chứa mật khẩu cục bộ này đã được `.gitignore` loại khỏi Git.
- Sau khi server dừng, có thể đóng các terminal để bỏ biến môi trường chỉ được đặt trong phiên đó. Việc đóng terminal không xóa cấu hình đã lưu trong file hoặc biến môi trường Windows được thiết lập lâu dài.
- Tránh dùng `taskkill /IM java.exe /F` để tắt toàn bộ: lệnh này có thể dừng nhầm các ứng dụng Java khác và ngắt lượt truyền file đang chạy.
