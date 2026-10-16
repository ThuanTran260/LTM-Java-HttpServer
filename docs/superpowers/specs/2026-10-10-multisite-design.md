# Multi-site hosting (quan ly nhieu web) - Design

Ngay: 2026-10-10 - Nhanh de xuat: `feature/multi-site` (cat tu `feature/web-client` SAU KHI plan WebClient xong)

## 1. Muc tieu

Hien Java Server `:8080` chi phuc vu **mot** web (thu muc `wwwroot/`). Can **quan ly va them duoc nhieu web**:
moi web (site) co **thu muc rieng** va chay tren **cong rieng** (giong IIS Sites/bindings), them / bat / tat / xoa
**khi server dang chay**, qua **trang quan tri** va **REST API** tren `:8080`, danh sach **luu vao `sites.json`**
de khoi dong lai van con.

### Tieu chi thanh cong
1. `POST /api/sites {"name":"blog","port":9001}` -> `201`; ngay sau do `http://localhost:9001/` tra trang cua site `blog`
   (thu muc `sites/blog/`, co `index.html` mac dinh); khong can khoi dong lai server.
2. Bat / tat / xoa tung site; tat thi cong dong, bat lai thi mo lai dung cong do.
3. Khoi dong lai server: cac site da bat tu chay lai (doc `sites.json`); site da tat van tat.
4. Trang `http://localhost:8080/admin.html` liet ke site (ten, cong, trang thai, link) va co Them / Bat / Tat / Xoa.
5. Dau vao sai bi tu choi ro rang: ten khong hop le, cong ngoai 1024-65535 (400); trung ten / trung cong / cong cua
   server chinh / cong dang bi chuong trinh khac dung (409); site khong ton tai (404).
6. Hanh vi cu cua server `:8080` khong doi; `java -cp out client.CrossTestClient` van 0 FAIL.

### Ngoai pham vi (YAGNI)
Tach site theo Host header hoac duong dan, HTTPS, xac thuc nguoi dung, upload file qua web, sua noi dung site
tren trang quan tri, xoa thu muc file khi xoa site, chon thu muc tuy y ngoai `sites/`, proxy/reverse-proxy cho site.

## 2. Quyet dinh da chot voi nguoi dung
- Phan biet site bang **cong rieng** (khong dung Host header, khong dung duong dan).
- Quan ly bang **trang quan tri + REST API tren :8080**, luu **`sites.json`**.

Quyet dinh cua nguoi thiet ke (co the doi): site chi phuc vu **file tinh**; thu muc cua site **luon la `sites/<ten>/`**
(khong cho nhap duong dan tuy y, tranh lo file he thong qua API); chi cho phep goi API/trang quan tri tu **may local**;
moi POST bat buoc `Content-Type: application/json`; xoa site **giu lai thu muc** file.

## 3. Kien truc

```
Trinh duyet / curl --HTTP--> :8080 SimpleHttpServer
                               |-- /api/sites  -> SitesApiHandler --> SiteManager  (registry + vong doi + sites.json)
                               |-- /admin.html -> file tinh trong wwwroot (goi /api/sites bang fetch)
                               `-- (cu) /iis, /fetch, /
SiteManager --moi site--> HttpServer rieng :<cong> --> SiteFileHandler --> StaticFileHandler.tryServe(ex, path, sites/<ten>)
```

### 3.1 `server.SiteManager` (moi)
- `SiteManager(Path sitesRoot, Path configFile, int reservedPort)`; `load()`, `list()`, `listJson()`, `get(name)`,
  `add(name, port)`, `start(name)`, `stop(name)`, `remove(name)`, `stopAll()`; moi phuong thuc `synchronized`.
- `SiteException(status, message)` mang ma HTTP (400 / 404 / 409 / 500). `SiteView(name, port, enabled, running)` + `toJson()`.
- Ten hop le: `[a-z0-9][a-z0-9-]{0,31}`. Cong hop le: 1024-65535, khong trung site khac, khac `reservedPort` (cong server chinh).
- `add`: kiem tra -> mo cong (loi bind = 409, thu lai 3 lan x 100 ms) -> tao `sites/<ten>/` va `index.html` mac dinh -> ghi `sites.json`
  (ghi that bai thi hoan tac). `stop` dat `enabled=false` va ghi; `start` dat `enabled=true`; `remove` dong cong va bo khoi danh sach.
- `load()`: doc `sites.json` (muc sai / trung / cong khong bat duoc chi bi ghi log), bat cac site `enabled`.
- `sites.json`: `[{"name":"blog","port":9001,"enabled":true}, ...]`, ghi qua file tam + doi ten (atomic).

### 3.2 `server.SiteFileHandler` (moi) va `StaticFileHandler` (sua nho)
- `SiteFileHandler`: chi nhan `GET` (khac -> 405 + `Allow: GET`), dung `StaticFileHandler.tryServe(ex, path, root)`, khong co file -> 404 `text/plain`.
- `StaticFileHandler`: them overload `tryServe(ex, path, Path webRoot)` (ban cu goi lai voi `wwwroot`); `contentTypeFor` them `.js`, `.svg`, `.ico`.

### 3.3 `server.SitesApiHandler` (moi), gan vao `SimpleHttpServer` tai `/api/sites`
| Request | Ket qua |
|:---|:---|
| `GET /api/sites` | 200 mang JSON cac site |
| `POST /api/sites` `{"name","port"}` | 201 site vua tao; 415 sai Content-Type; 400 thieu truong / sai gia tri; 409 trung / cong ban |
| `GET /api/sites/{name}` | 200 / 404 |
| `POST /api/sites/{name}/start` , `/stop` | 200 site sau khi doi / 404 / 415 / 409 (khong mo duoc cong) |
| `DELETE /api/sites/{name}` | 200 `{"message":"Deleted"}` / 404 |
| phuong thuc khac | 405 + `Allow` |
| goi tu dia chi khong phai loopback | 403 |
Loi nghiep vu tra `text/plain` dang `<ma> <ten>: <thong bao>` giong cac route khac (`ServerLogger` them nhan 403, 409).

### 3.4 Trang quan tri `wwwroot/admin.html` (moi) + link tu `wwwroot/index.html`
HTML + JavaScript thuan (fetch toi `/api/sites`), dung DOM API (`textContent`), khong innerHTML. Bang site + form Them +
nut Bat/Tat/Xoa (xac nhan truoc khi xoa) + dong thong bao ket qua/loi.

### 3.5 Cau hinh va cleanup
- `.gitignore`: `sites/`, `sites.json`, `sites.json.tmp` (du lieu nguoi dung, khong commit).
- `SimpleHttpServer.startServer(port)`: tao `SiteManager(Paths.get("sites"), Paths.get("sites.json"), port)`, `load()`, gan `/api/sites`.

## 4. Kiem thu (them vao `CrossTestClient`)
- Muc "11. MULTI-SITE": test **S1-S19** thao tac truc tiep `SiteManager` tren thu muc tam (them, phuc vu file tinh, 404/405/chan traversal,
  luu file, tat/bat nhieu vong tren cung cong, validation 400/409, cong dang ban, nap lai tu file, xoa, file hong); test **S20-S37** goi
  REST API qua `:8080` (site thu `zz-api-test` tren cong trong ngau nhien, don dep thu muc sau khi xong; kem `isLoopback` cho 403); test
  **S38-S39** kiem tra `admin.html` va link tu `index.html`. Tong +39 test (baseline 103 -> 142).
- Ban nhap cac lop moi da duoc bien dich va chay thu tren Windows: 39 kiem tra dat, ke ca 6 vong tat/bat lai cung mot cong.

## 5. Rui ro da biet
- Windows co the hien hop thoai Firewall khi mo cong moi - khong anh huong chuc nang.
- Test REST chay toi server `:8080` dang chay (neu co) va tao / xoa site `zz-api-test` tren do; luon don dep trong `finally`.
- `sites.json` ghi lai toan bo trang thai trong bo nho cua server dang chay; khong nen sua tay trong luc server chay.
- Chua co xac thuc: chi dua vao gioi han loopback + yeu cau `Content-Type: application/json`.
