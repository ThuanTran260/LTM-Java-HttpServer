# Original User Request

## Initial Request — 2026-10-10T02:30:55Z

This is a single self-contained fix; keep it small and focused.
Kiểm tra, đối chiếu và xác nhận tính toàn vẹn giữa mã nguồn Java trên nhánh `main` với tài liệu giảng dạy trong thư mục `pics/`, đảm bảo hệ thống thuần Java 100% (không có cú pháp C#) và sở hữu bộ kiểm thử tự động xác thực toàn bộ các kịch bản trong slide.

Working directory: d:\LapTrinhMang\LTM-Java-HttpServer
Integrity mode: development

## Requirements

### R1. Đối chiếu và Chuẩn hóa 100% Java
Mã nguồn phải hoàn toàn thuần Java, chuẩn hóa toàn bộ các điểm giao diện hoặc khái niệm bị nhầm lẫn cú pháp C# trong slide bài giảng (như HttpListener, async/await, Task) sang mô hình HTTP Server chuẩn của Java (`com.sun.net.httpserver`, virtual threads / thread pool, `java.net.http.HttpClient`).

### R2. Bảo đảm đầy đủ các Route và Bài tập mở rộng của Slide
Hệ thống HTTP Server phải đáp ứng chính xác hành vi được mô tả trong 20 slide tại `pics/`:
- `GET /`: Phục vụ trang HTML tĩnh hoặc thông báo hoạt động mặc định (200 OK, `text/html`).
- `GET /api/time`: Trả về thời gian hiện tại định dạng JSON `{"time":"HH:mm:ss"}` (200 OK, `application/json`).
- `POST /api/message`: Kiểm tra Content-Type bắt buộc là `application/json` (trả về 415 nếu không đúng), đọc nội dung body và trả về 201 Created `{"message":"Da nhan du lieu"}`.
- CRUD Sinh viên: Hỗ trợ truy vấn và quản lý dữ liệu sinh viên tại `/api/students` (truy vấn danh sách, lọc theo query string `?id=`, `?name=`, `?age=`, cập nhật và xóa).
- Phục vụ tệp tĩnh: Phục vụ an toàn các tệp từ thư mục `wwwroot` (HTML, CSS, PNG, TXT), chặn Directory Traversal.
- HTTP Client: Bộ mã nguồn demo client (`HttpClientGetDemo`, `HttpClientPostDemo`, `HttpClientErrorDemo`) mô phỏng gửi request và xử lý lỗi / timeout.
- Route không hợp lệ: Trả về mã lỗi 404 text/plain.
- Console Logger: Ghi nhật ký đầy đủ method, URL, status code, kích thước bytes và remote client.

### R3. Bộ kịch bản kiểm thử tự động độc lập
Cung cấp kịch bản kiểm tra tích hợp tự động (Integration Test Runner) có khả năng tự khởi động/gọi đến server và kiểm chứng toàn bộ ma trận request (GET, POST JSON, PUT, DELETE, static files, 404, 415) với kết quả đạt 100% PASS.

## Acceptance Criteria

### Tính chính xác về HTTP Protocol & Slide Specs
- [ ] Tất cả mã nguồn Java trong `src/` biên dịch thành công không cảnh báo lỗi (`javac -d out ...`).
- [ ] Server phản hồi `GET /` với Status Code 200 và Content-Type chứa `text/html`.
- [ ] Server phản hồi `GET /api/time` với Status Code 200, Content-Type `application/json` và body chứa trường `time`.
- [ ] Server phản hồi `POST /api/message` với Status Code 201 khi gửi kèm JSON hợp lệ; trả về 415 khi Header Content-Type khác `application/json`.
- [ ] Server phản hồi `GET /api/students` trả về JSON danh sách sinh viên; `GET /api/students?id=1` trả về JSON chi tiết sinh viên ID 1; trả về 404 nếu ID không tồn tại.
- [ ] Server phản hồi 404 với route không tồn tại (ví dụ: `GET /abc`).
- [ ] Tuyệt đối không còn bất kỳ cú pháp hay từ khóa C# nào (như `async Task`, `await`, `HttpListener`) trong mã nguồn Java.

### Kiểm thử độc lập (Programmatic Verification)
- [ ] Chạy kịch bản kiểm thử tích hợp tự động (ví dụ `client.CrossTestClient` hoặc test runner tương đương) thực hiện toàn bộ các ca kiểm thử trên và trả về kết quả 0 FAIL (exit code 0).
