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
│   ├── server/       # SimpleHttpServer, RouterHandler, ProxyHandler, StaticFileHandler, Logger
│   ├── service/      # StudentService (CRUD sinh viên dạng JSON)
│   ├── model/        # Student POJO
│   ├── client/       # HttpClientGetDemo, HttpClientPostDemo, HttpClientErrorDemo, CrossTestClient
│   └── mock/         # MockIisServer (dự phòng cổng 8081 nếu máy không có IIS thật)
├── wwwroot/          # index.html, style.css (tài nguyên web tĩnh)
├── out/              # Bytecode .class sau khi biên dịch
└── *.bat             # Các file script chạy nhanh
```
