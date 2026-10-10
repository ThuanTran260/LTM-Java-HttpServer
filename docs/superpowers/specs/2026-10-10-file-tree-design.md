# Cay file cua tung site tren trang quan tri - Design

Ngay: 2026-10-10 - Nhanh lam tiep: `feature/multi-site` (sau khi multi-site da xong, commit `65cadaa`)

## 1. Muc tieu

Trang quan tri `http://localhost:8080/admin.html` hien tai chi liet ke cac site (ten, cong, trang thai, dia chi, nut Bat/Tat/Xoa).
Them: moi site co nut **Xem file** hien cay cac file web trong thu muc `sites/<ten>/` theo kieu File Explorer.
Bam vao file de mo tren cong cua site, trong tab moi: `http://localhost:<cong>/<duong dan>`.

## 2. Quyet dinh da chot

- **Chi doc:** khong sua, doi ten, xoa hay upload file. Spec multi-site da loai tru viec sua noi dung tren trang quan tri.
- **Chi liet ke file ma site phuc vu duoc:** trung voi `StaticFileHandler.contentTypeFor(ten) != null` (html, htm, css, js, svg, ico, png, jpg, jpeg, gif, txt, json). Thu muc khong con file nhu vay thi an.
- **Thu muc truoc, file sau.** Moi nhom sap xep theo ten, khong phan biet hoa thuong.
- **Thu muc mo san.** Bam **Xem file** lan nua de dong.
- **API chi goi tu may local**, cung quy tac voi `/api/sites`.

## 3. Thiet ke

### 3.1 API

`GET /api/sites/{ten}/files` tra `200 application/json` gom cac node:
- file: `{"name":"a.html","path":"sub/a.html","dir":false}`
- thu muc: `{"name":"sub","path":"sub","dir":true,"children":[...]}`

- `path` la duong dan tuong doi trong site, luon dung `/`, khong co `/` o dau.
- `404` neu site khong ton tai. `405` kem `Allow: GET` neu dung method khac.
- Hoat dong ca khi site dang tat, vi chi doc thu muc tren dia.
- Khong di theo symlink thu muc (`LinkOption.NOFOLLOW_LINKS`).

### 3.2 Trang quan tri

- Moi dong site co nut **Xem file**. Bam vao se mo mot dong phu ben duoi, goi API va ve cay bang `<details>`/`<summary>`.
- Link file: `http://localhost:<cong>/` cong tung doan duong dan da ma hoa (`encodeURIComponent`), mo tab moi.
- Site dang tat: hien ghi chu "Web dang tat: bam Bat de mo cac file bang link ben duoi".
- Ve cay bang DOM API (`createElement`, `textContent`), khong dung `innerHTML`.

## 4. Ngoai pham vi

- Sua, xoa, doi ten, upload file; xem noi dung file trong trang quan tri.
- Phan trang; tu cap nhat cay (phai bam Xem file lai de tai danh sach).
- Hien file bi an, file bat dau bang dau cham, va thu muc rong.

## 5. Rang buoc chung

- Java thuan, khong them thu vien ngoai (JDK 24).
- Chuoi trong code Java: tieng Viet khong dau. Khong viet chuoi backslash-u trong comment Java.
- Code server chi them trong `src/server/`; test trong `src/client/CrossTestClient.java`; giao dien trong `wwwroot/`.
- API moi chi phuc vu loopback (`SitesApiHandler.isLoopback`), giong cac API khac.
- Trang quan tri dung DOM API, khong dung `innerHTML`.
- Duong dan trong JSON luon dung `/`, ke ca tren Windows.

## 6. Kiem thu

Test S40-S49, chi tiet trong plan `docs/superpowers/plans/2026-10-10-file-tree.md`.
Tong: 152 PASS, 0 FAIL (142 hien tai + 10).
Nut Xem file va link mo file tren trinh duyet la kiem tra tay, vi test tu dong khong dieu khien trinh duyet.

## 7. Rui ro da biet

- Symlink tro file ra ngoai site van duoc liet ke va phuc vu. Day la hanh vi san co cua `StaticFileHandler`, tinh nang nay khong thay doi viec do.
- Thu muc rat lon: khong phan trang, danh sach co the dai.
- Chuoi JSON chi escape `"`, dau gach cheo nguoc va ky tu dieu khien. Tren Windows, ten file khong chua cac ky tu nay.
