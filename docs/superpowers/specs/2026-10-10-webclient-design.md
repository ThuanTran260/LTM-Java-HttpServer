# Web Client (Swing) - Design

Ngay: 2026-10-10 - Nhanh: tao tu `feature/web-fetcher`

## 1. Muc tieu

Mot ung dung desktop **Java thuan (Swing, JDK 24, khong thu vien ngoai)** dong vai **client**: nhap mot URL, goi
**WebFetch** cua Java Server (`GET http://localhost:8080/fetch?url=<URL>`) va **hien thi ma nguon (source code)** cua
trang web nhan ve. Phuc vu demo mon Lap Trinh Mang: `Web Client --HTTP--> Java Server :8080 --HTTP--> trang dich`.

### Tieu chi thanh cong
1. Chay `run_webclient.bat` (sau `run_server.bat`), nhap URL, bam Fetch -> tab **Source** hien source HTML cua trang.
2. Thanh trang thai hien Status, Content-Type, do dai (bytes), che do raw/json.
3. Cac tinh nang them da chon: checkbox **format=json**, nut **Save...**, tab **Rendered**, **lich su URL**.
4. Logic goi mang tach rieng, test duoc khong can man hinh; `java -cp out client.CrossTestClient` van 0 FAIL.
5. Server tat / URL sai khong lam treo hoac crash app: loi hien ro tren thanh trang thai.

### Ngoai pham vi (YAGNI)
Dang nhap/cookie, POST, duyet nhieu tab, tu sua URL khong co scheme, bo loc/tim kiem trong source, syntax highlight,
thay doi code `src/server/*`.

## 2. Quyet dinh da chot voi nguoi dung
- Hinh thuc app: **Swing** (khong phai console, khong phai trang web trong wwwroot).
- To chuc code **huong B**: `WebFetchService` (logic, khong Swing) + `WebClientApp` (giao dien).
- The `<base href>` do server `/fetch` tu chen vao HTML: **hien thi nguyen nhu server tra ve** + 1 dong chu thich tren
  giao dien (khong tu xoa, vi xoa khong dao nguoc hoan toan duoc va tab Rendered can chinh the nay).
- Chuoi trong code Java: tieng Viet **khong dau** (khop quy uoc cua code hien co). README thi co dau.

## 3. Kien truc

```
WebClientApp (Swing, EDT) --SwingWorker--> WebFetchService (java.net.http.HttpClient) --GET /fetch--> Java Server :8080
```

### 3.1 `src/client/WebFetchService.java` (khong dung Swing)
- `new WebFetchService(String serverBase)`; mac dinh `http://localhost:8080`.
- `String buildRequestUrl(String targetUrl, boolean json)` -> `<server>/fetch?[format=json&]url=<URL-encoded>`.
- `Result fetch(String targetUrl, boolean json)`: **khong nem exception**; moi loi nam trong `Result.error`.
- `record Result(requestUrl, jsonMode, status, contentType, contentLength, body, rawResponse, error)`; `ok()` = `error == null`.
  - Che do raw: `status`/`contentType` lay tu HTTP response; `body` = `rawResponse` = noi dung (giai ma theo charset).
  - Che do json: tach `{targetUrl,status,contentType,contentLength,body}` bang parser JSON phang tu viet;
    `rawResponse` = nguyen van JSON, `body` = truong `body` da giai ma escape.
  - Json mode nhung server tra loi `text/plain` (vi du 400 scheme khong hop le) -> khong parse JSON, hien nhu raw.
  - Noi dung khong phai text (vi du `image/png`) -> `body` la dong placeholder, `contentLength` la so byte that.
  - `error != null`: `status = -1`. Thong bao rieng cho: URL rong, khong ket noi duoc server, het thoi gian (15s), loi mang.
- Ham tien ich: `isHtml`, `isTextual`, `suggestFileName(Result)`, `charsetOf(contentType, bytes)` (header -> `<meta charset>` -> UTF-8),
  `parseFlatJson`.

### 3.2 `src/client/WebClientApp.java` (Swing)
Bo cuc: hang tren (o **Server WebFetch**; o **URL** dang `JComboBox` editable + checkbox `format=json` + nut **Fetch** + nut **Save...**),
giua `JTabbedPane` (**Source** = `JTextArea` monospace chi doc + dong chu thich `<base>`; **Rendered** = `JEditorPane` HTML),
duoi la thanh trang thai.
- Fetch (nut hoac Enter): `SwingWorker` goi `WebFetchService` o thread nen, nut Fetch bi khoa den khi xong (chong fetch trung).
- Tab Source hien `rawResponse` (json mode) hoac `body` (raw mode). Json mode bat xuong dong tu dong.
- Tab Rendered: HTML duoc parse o thread nen (ke ca tai CSS ngoai) voi `IgnoreCharsetDirective = true`; noi dung khong
  phai HTML -> thong bao "Khong phai HTML".
- Lich su: toi da 20 URL, moi nhat len dau, khong trung; chi them khi server tra loi duoc (`Result.ok()`).
- Save: ghi noi dung dang hien o tab Source ra file UTF-8 (hoi truoc khi ghi de); ten goi y theo `suggestFileName`.
- Loi: thanh trang thai mau do `Loi: <thong bao>`, tab Source hien cung thong bao, Rendered xoa trang.
- Giu timeout cho `URLConnection` cu (Swing tai CSS/anh): `sun.net.client.defaultConnectTimeout=5000`, `defaultReadTimeout=8000`.

## 4. Kiem thu
- `CrossTestClient` them muc "9. WEB CLIENT (client.WebFetchService)" (test 83-94, khong can man hinh) va
  muc "10. WEB CLIENT (client.WebClientApp, Swing)" (test 95-99, smoke test tao cua so khong hien thi,
  tu bo qua `[SKIP]` khi moi truong headless).
- Baseline truoc khi them: 85 PASS, 0 FAIL. Sau khi xong: 102 PASS, 0 FAIL (khi co man hinh).
- Kiem tay (nguoi dung): `run_server.bat` + `run_webclient.bat`, thu URL that (https), thu tat server.

## 5. Rui ro da biet
- Tab Rendered dung `JEditorPane` (HTML 3.2/CSS 1): trang hien dai se hien thi xau - dung nhu thong bao trong README.
- Tab Rendered tai anh/CSS **truc tiep tu trang dich** (khong qua WebFetch) nho the `<base href>`; day la hanh vi cua Swing.
- `WebFetchHandler` tin moi chung chi TLS va cho phep fetch localhost (khong chong SSRF) - client khong thay doi dieu nay.
