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
- Thêm web `blog` ở cổng 9001 → mở `http://localhost:9001/`. Sửa nội dung web trong `sites/blog/` (file `index.html` mặc định được tạo sẵn).

| API (chỉ từ máy local) | Kết quả |
|:---|:---|
| `GET /api/sites` | Danh sách web (tên, cổng, trạng thái, địa chỉ) |
| `POST /api/sites` `{"name":"blog","port":9001}` | `201` — tạo thư mục và chạy web; `409` nếu trùng tên/cổng hoặc cổng đang bị dùng; `400` nếu sai tên (a-z, 0-9, `-`) hoặc cổng ngoài 1024–65535 |
| `POST /api/sites/{tên}/start` · `/stop` | Bật / tắt web (cần header `Content-Type: application/json`) |
| `DELETE /api/sites/{tên}` | Xóa khỏi danh sách (thư mục file được giữ lại) |

Ví dụ: `curl -X POST http://localhost:8080/api/sites -H "Content-Type: application/json" -d "{\"name\":\"blog\",\"port\":9001}"`

Kiểm thử tự động: `java -cp out client.CrossTestClient` (mục 11, test S1–S39).
