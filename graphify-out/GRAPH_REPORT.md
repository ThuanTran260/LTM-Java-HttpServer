# Graph Report - Http server  (2026-10-17)

## Corpus Check
- 52 files · ~587,612 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: .bat 14, .css 2, (none) 1)

## Summary
- 961 nodes · 2272 edges · 41 communities (24 shown, 17 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 318 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fd0cacba`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- StudentService
- ioexception
- FtpClient
- MockHttpExchange
- BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)
- CrossTestClient
- FtpSessionHandler
- CrossTestFtpClient
- WebFetchHandler
- FlatJsonReader
- SiteManager
- Local/LAN Security Hardening Implementation Plan
- Phát hiện
- com.sun.net.httpserver.HttpExchange
- Detailed Test Tiers (58 Test Cases)
- FtpFile
- Kế Hoạch Triển Khai: Java FTP Server & Java FTP Client (RFC 959)
- Cay file cua tung site tren trang quan tri - Design
- WebClientApp
- SharedFileStore
- .handle
- files.js
- Web File Explorer — Option A Implementation Plan
- .tryServe
- FtpClientApp
- Multi-site hosting (quan ly nhieu web) - Design
- .startServer
- Thiết kế Web File Explorer — phương án A
- .handle
- FileStoreConfig
- 🚀 9. Java FTP Server (RFC 959) & Swing GUI Client
- ⚡ 1. Khởi Động Nhanh (1-Click)

## God Nodes (most connected - your core abstractions)
1. `FtpClient` - 46 edges
2. `CrossTestClient` - 45 edges
3. `SharedFileStore` - 36 edges
4. `FtpClientApp` - 35 edges
5. `SiteManager` - 34 edges
6. `FtpSessionHandler` - 34 edges
7. `CrossTestFtpClient` - 32 edges
8. `WebClientApp` - 29 edges
9. `FilesApiHandler` - 29 edges
10. `MockHttpExchange` - 26 edges

## Surprising Connections (you probably didn't know these)
- `4. Kiem thu` --references--> `CrossTestClient`  [INFERRED]
  docs/superpowers/specs/2026-10-10-webclient-design.md → src/client/CrossTestClient.java
- `4. Cross-Test Runner Contract (`client.CrossTestFtpClient`)` --references--> `CrossTestFtpClient`  [INFERRED]
  PROJECT.md → src/client/CrossTestFtpClient.java
- `3.1 Test Groups Breakdown` --references--> `FileStoreConfig`  [INFERRED]
  TEST_READY.md → src/server/FileStoreConfig.java
- `4. Kiem thu (them vao `CrossTestClient`)` --references--> `CrossTestClient`  [INFERRED]
  docs/superpowers/specs/2026-10-10-multisite-design.md → src/client/CrossTestClient.java
- `Kiểm thử độc lập (Programmatic Verification)` --references--> `CrossTestClient`  [INFERRED]
  ORIGINAL_REQUEST.md → src/client/CrossTestClient.java

## Import Cycles
- None detected.

## Communities (41 total, 17 thin omitted)

### Community 3 - "FtpClient"
Cohesion: 0.06
Nodes (14): 1. Control Wire Interface (RFC 959 Client <-> Server), 2. Data Wire Interface (Passive & Active), 3. Client Library Interface (`client.FtpClient`), 4. Cross-Test Runner Contract (`client.CrossTestFtpClient`), Architecture, Code Layout, Feature Inventory, Interface Contracts (+6 more)

### Community 4 - "MockHttpExchange"
Cohesion: 0.06
Nodes (7): CrossTestFilesClient, FtpWireClient, MockHttpExchange, MutableClock, ProcessFixture, RawResponse, ServerInstance

### Community 5 - "BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)"
Cohesion: 0.20
Nodes (9): 🌐 2. Kiểm Tra Bằng Trình Duyệt (Firefox / Chrome), 🧪 3. Các File Kiểm Thử Riêng Lẻ (Chạy Bằng 1 Click), 🛑 4. Quản Lý IIS Thật Của Windows (w3svc) Bằng Lệnh, 🛠️ 5. Biên Dịch Lại Mã Nguồn (Khi Có Sửa Code), 📁 6. Cấu Trúc Mã Nguồn, 🖥️ 7. Web Client (Swing) — xem source code qua WebFetch, 🌐 8. Quản lý nhiều web (Sites), BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA) (+1 more)

### Community 7 - "CrossTestClient"
Cohesion: 0.06
Nodes (35): Review Focus, Task 1: `SiteManager.filesJson` + test S40-S45, Task 2: Route `GET /api/sites/{ten}/files` + test S46-S48, Task 3: Trang quan tri hien cay file + README + test S49, Global Constraints, Multi-site hosting (quan ly nhieu web) Implementation Plan, Review Focus, Task 1: `SiteManager` + `SiteFileHandler` + `StaticFileHandler` overload + test S1-S19 (+27 more)

### Community 8 - "FtpSessionHandler"
Cohesion: 0.09
Nodes (10): Hợp đồng nội bộ dùng xuyên suốt, SessionRecord, FtpDataChannel, FtpPathSandbox, FtpSessionHandler, FtpUserAuth, Role, ADMIN (+2 more)

### Community 9 - "CrossTestFtpClient"
Cohesion: 0.09
Nodes (14): B. Gói Máy Khách FTP (`src/client/ftp/`), CrossTestFtpClient, FtpRawSession, 1. Autonomous Server Lifecycle Management, 2. Comprehensive 8-Tier Test Matrix (58 Cases), 3.1 Test Groups Breakdown, 3.2 Regression Verification Summary, 3. How to Run the Tests (+6 more)

### Community 10 - "WebFetchHandler"
Cohesion: 0.12
Nodes (9): 1. Muc tieu, 2. Quyet dinh da chot voi nguoi dung, 4. Kiem thu, 5. Rui ro da biet, Ngoai pham vi (YAGNI), Tieu chi thanh cong, Web Client (Swing) - Design, FetchParams (+1 more)

### Community 12 - "SiteManager"
Cohesion: 0.21
Nodes (5): 3.1 `server.SiteManager` (moi), Site, SiteException, SiteManager, SiteView

### Community 14 - "Local/LAN Security Hardening Implementation Plan"
Cohesion: 0.11
Nodes (18): Fetch/proxy, File map, Filesystem, Giới hạn ban đầu, Global Constraints, Local/LAN Security Hardening Implementation Plan, Mạng và quyền truy cập, Quyết định thiết kế (+10 more)

### Community 15 - "Phát hiện"
Cohesion: 0.11
Nodes (17): Kết luận, Lựa chọn và đề xuất, Những bảo vệ đã có, Phát hiện, Phạm vi và bằng chứng, SEC-01 — Cao: sandbox HTTP/FTP theo đường dẫn chuỗi, không theo đích filesystem, SEC-02 — Cao: fetch vượt hạn chế localhost của API quản trị, SEC-03 — Cao, suy luận từ response: HTML không tin cậy được phục vụ cùng origin quản trị (+9 more)

### Community 16 - "com.sun.net.httpserver.HttpExchange"
Cohesion: 0.09
Nodes (7): FilesApiHandler, WatchdogInputStream, AuthException, FileSessionAuth, LoginThrottle, SessionView, UpstreamContentPolicy

### Community 17 - "Detailed Test Tiers (58 Test Cases)"
Cohesion: 0.12
Nodes (15): Coverage Thresholds, Detailed Test Tiers (58 Test Cases), E2E Test Infra: Java FTP Server & Client System, Feature Inventory & Test Mapping, Test Architecture, Test Philosophy, Tier 1: Connection & Protocol Handshake (7 Cases), Tier 2: Authentication & Authorization (6 Cases) (+7 more)

### Community 20 - "Kế Hoạch Triển Khai: Java FTP Server & Java FTP Client (RFC 959)"
Cohesion: 0.18
Nodes (10): 1. Tổng Quan Kiến Trúc & Bối Cảnh, 2. Các Quyết Định Thiết Kế Quan Trọng, 3. Danh Sách Các Tập Tin Cần Tạo & Sửa Đổi, 4. Kế Hoạch Triển Khai Chi Tiết Từng Bước (Tasks), 5. Kế Hoạch Kiểm Thử & Nghiệm Thu (Verification Plan), A. Gói Máy Chủ FTP (`src/server/ftp/`), A. Kiểm thử tự động (Automated Verification), B. Kiểm thử thủ công với Giao diện (Manual GUI Verification) (+2 more)

### Community 21 - "Cay file cua tung site tren trang quan tri - Design"
Cohesion: 0.20
Nodes (9): 1. Muc tieu, 2. Quyet dinh da chot, 3.1 API, 3.2 Trang quan tri, 3. Thiet ke, 4. Ngoai pham vi, 5. Rang buoc chung, 6. Kiem thu (+1 more)

### Community 23 - "SharedFileStore"
Cohesion: 0.09
Nodes (9): SimpleFtpServer, SafePaths, DirectoryPage, Download, Entry, MutationAction, RenameTicket, SharedFileStore (+1 more)

### Community 26 - ".handle"
Cohesion: 0.21
Nodes (3): File tree on the admin page Implementation Plan, Global Constraints, SitesApiHandler

### Community 27 - "files.js"
Cohesion: 0.26
Nodes (17): checkSession(), clearStatus(), enqueueFiles(), formatBytes(), formatIsoDate(), loadDirectory(), onLoginSuccess(), processUploadQueue() (+9 more)

### Community 28 - "Web File Explorer — Option A Implementation Plan"
Cohesion: 0.15
Nodes (12): Baseline và file map, Dependencies, rollback và thứ tự thực hiện, Global Constraints, Self-review của bản kế hoạch, Task 2 — SharedFileStore và atomic mutation, Task 3 — FTP dùng shared store, giữ tương thích client, Task 4 — Session local và cô lập Fetch/IIS upstream, Task 5 — HTTP file API hoàn chỉnh (+4 more)

### Community 29 - ".tryServe"
Cohesion: 0.17
Nodes (8): 7. Rui ro da biet, 3.2 `server.SiteFileHandler` (moi) va `StaticFileHandler` (sua nho), 3.3 `server.SitesApiHandler` (moi), gan vao `SimpleHttpServer` tai `/api/sites`, 3.4 Trang quan tri `wwwroot/admin.html` (moi) + link tu `wwwroot/index.html`, 3.5 Cau hinh va cleanup, 3. Kien truc, ServerLogger, StaticFileHandler

### Community 32 - "Multi-site hosting (quan ly nhieu web) - Design"
Cohesion: 0.25
Nodes (7): 1. Muc tieu, 2. Quyet dinh da chot voi nguoi dung, 4. Kiem thu (them vao `CrossTestClient`), 5. Rui ro da biet, Multi-site hosting (quan ly nhieu web) - Design, Ngoai pham vi (YAGNI), Tieu chi thanh cong

### Community 34 - "Thiết kế Web File Explorer — phương án A"
Cohesion: 0.20
Nodes (9): 1. Mục tiêu và giới hạn, 2. Phân vai và dữ liệu, 4. Ranh giới filesystem và đồng thời giữa hai JVM, 5. Đăng nhập, session và cô lập nội dung không tin cậy, 6. Hợp đồng HTTP API, 7. Giao diện, 8. Kiểm thử và nghiệm thu, 9. Ngoài phạm vi (+1 more)

### Community 36 - "FileStoreConfig"
Cohesion: 0.22
Nodes (8): Task 1 — Test harness, cấu hình dùng chung và startup, 3. Cấu hình dùng chung, 10.1 Khởi động và cấu hình chung, 10.2 Xác thực, phiên làm việc và bảo mật, 10.3 Giới hạn kỹ thuật và an toàn dữ liệu, 10.4 Chạy kiểm thử tự động toàn diện, 🗄️ 10. Quản Lý Tệp Qua Web (Web File Explorer & Shared Storage), FileStoreConfig

### Community 37 - "🚀 9. Java FTP Server (RFC 959) & Swing GUI Client"
Cohesion: 0.33
Nodes (6): 9.1 Khởi động nhanh (1-Click), 9.2 Phân quyền tài khoản (Role-Based Authorization), 9.3 Hướng dẫn kết nối bằng FileZilla Client, 9.4 Các tính năng bảo mật nâng cao (Security Hardening), 9.5 Dừng dịch vụ an toàn sau buổi thực hành, 🚀 9. Java FTP Server (RFC 959) & Swing GUI Client

### Community 39 - "⚡ 1. Khởi Động Nhanh (1-Click)"
Cohesion: 0.67
Nodes (3): ⚡ 1. Khởi Động Nhanh (1-Click), Dịch vụ FTP Server & Client (RFC 959), Dịch vụ HTTP & Web

## Knowledge Gaps
- **117 isolated node(s):** `GUEST`, `USER`, `ADMIN`, `Initial Request — 2026-10-10T02:30:55Z`, `R1. Đối chiếu và Chuẩn hóa 100% Java` (+112 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 251 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **17 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CrossTestFilesClient` connect `MockHttpExchange` to `CrossTestFilesClient.java`, `Detailed Test Tiers (58 Test Cases)`, `FileStoreConfig`, `SharedFileStore`?**
  _High betweenness centrality (0.123) - this node is a cross-community bridge._
- **Why does `CrossTestClient` connect `CrossTestClient` to `Multi-site hosting (quan ly nhieu web) - Design`, `CrossTestFtpClient`, `WebFetchHandler`, `SiteManager`, `WebFetchHandler.java`?**
  _High betweenness centrality (0.113) - this node is a cross-community bridge._
- **Why does `FtpClient` connect `FtpClient` to `CrossTestFtpClient`, `FtpClientApp`, `FtpClient.java`, `.initListeners`?**
  _High betweenness centrality (0.107) - this node is a cross-community bridge._
- **Are the 14 inferred relationships involving `CrossTestClient` (e.g. with `Task 1: `SiteManager.filesJson` + test S40-S45` and `Task 2: Route `GET /api/sites/{ten}/files` + test S46-S48`) actually correct?**
  _`CrossTestClient` has 14 INFERRED edges - model-reasoned connections that need verification._
- **What connects `GUEST`, `USER`, `ADMIN` to the rest of the system?**
  _117 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `FtpClient` be split into smaller, more focused modules?**
  _Cohesion score 0.0578386605783866 - nodes in this community are weakly interconnected._
- **Should `MockHttpExchange` be split into smaller, more focused modules?**
  _Cohesion score 0.06142410015649452 - nodes in this community are weakly interconnected._