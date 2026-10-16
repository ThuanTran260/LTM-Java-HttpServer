# Graph Report - Http server  (2026-10-16)

## Corpus Check
- 44 files · ~559,898 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 16 file(s) not represented in the graph (top: .bat 13, (none) 1, .bin 1)

## Summary
- 713 nodes · 1647 edges · 32 communities (18 shown, 14 thin omitted)
- Extraction: 84% EXTRACTED · 16% INFERRED · 0% AMBIGUOUS · INFERRED: 259 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `5f1a2c2f`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- .handle
- FtpClient
- CrossTestClient.java
- BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)
- CrossTestClient
- FtpSessionHandler
- CrossTestFtpClient
- WebFetchHandler
- WebFetchService
- java.net.URI
- FtpClientApp.java
- Local/LAN Security Hardening Implementation Plan
- Phát hiện
- Project: Java FTP Server & Client System (RFC 959)
- Detailed Test Tiers (58 Test Cases)
- FtpFile
- Kế Hoạch Triển Khai: Java FTP Server & Java FTP Client (RFC 959)
- Cay file cua tung site tren trang quan tri - Design
- .handle
- SimpleFtpServer
- Original User Request
- ProxyHandler
- SafePaths
- .isLoopback
- .log
- .tryServe

## God Nodes (most connected - your core abstractions)
1. `FtpClient` - 46 edges
2. `CrossTestClient` - 43 edges
3. `FtpClientApp` - 35 edges
4. `SiteManager` - 34 edges
5. `CrossTestFtpClient` - 31 edges
6. `FtpSessionHandler` - 30 edges
7. `WebClientApp` - 29 edges
8. `WebFetchHandler` - 20 edges
9. `FtpRawSession` - 17 edges
10. `FtpFile` - 16 edges

## Surprising Connections (you probably didn't know these)
- `4. Kiem thu` --references--> `CrossTestClient`  [INFERRED]
  docs/superpowers/specs/2026-10-10-webclient-design.md → src/client/CrossTestClient.java
- `Kiểm thử độc lập (Programmatic Verification)` --references--> `CrossTestClient`  [INFERRED]
  ORIGINAL_REQUEST.md → src/client/CrossTestClient.java
- `4. Cross-Test Runner Contract (`client.CrossTestFtpClient`)` --references--> `CrossTestFtpClient`  [INFERRED]
  PROJECT.md → src/client/CrossTestFtpClient.java
- `Feature Inventory` --references--> `FtpCommandListener`  [INFERRED]
  PROJECT.md → src/client/FtpCommandListener.java
- `7. Rui ro da biet` --references--> `StaticFileHandler`  [INFERRED]
  docs/superpowers/specs/2026-10-10-file-tree-design.md → src/server/StaticFileHandler.java

## Import Cycles
- None detected.

## Communities (32 total, 14 thin omitted)

### Community 1 - ".handle"
Cohesion: 0.15
Nodes (3): Student, RouterHandler, StudentService

### Community 3 - "FtpClient"
Cohesion: 0.06
Nodes (6): B. Gói Máy Khách FTP (`src/client/ftp/`), DataConnection, FtpClient, FtpClientApp, FtpCommandListener, FtpReply

### Community 5 - "BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)"
Cohesion: 0.18
Nodes (10): ⚡ 1. Khởi Động Nhanh (1-Click), 🌐 2. Kiểm Tra Bằng Trình Duyệt (Firefox / Chrome), 🧪 3. Các File Kiểm Thử Riêng Lẻ (Chạy Bằng 1 Click), 🛑 4. Quản Lý IIS Thật Của Windows (w3svc) Bằng Lệnh, 🛠️ 5. Biên Dịch Lại Mã Nguồn (Khi Có Sửa Code), 📁 6. Cấu Trúc Mã Nguồn, 🖥️ 7. Web Client (Swing) — xem source code qua WebFetch, 🌐 8. Quản lý nhiều web (Sites) (+2 more)

### Community 7 - "CrossTestClient"
Cohesion: 0.09
Nodes (19): File tree on the admin page Implementation Plan, Global Constraints, Review Focus, Task 1: `SiteManager.filesJson` + test S40-S45, Task 2: Route `GET /api/sites/{ten}/files` + test S46-S48, Task 3: Trang quan tri hien cay file + README + test S49, Global Constraints, Multi-site hosting (quan ly nhieu web) Implementation Plan (+11 more)

### Community 8 - "FtpSessionHandler"
Cohesion: 0.09
Nodes (8): FtpDataChannel, FtpPathSandbox, FtpSessionHandler, FtpUserAuth, Role, ADMIN, GUEST, USER

### Community 9 - "CrossTestFtpClient"
Cohesion: 0.11
Nodes (10): CrossTestFtpClient, FtpRawSession, 1. Autonomous Server Lifecycle Management, 2. Comprehensive 8-Tier Test Matrix (58 Cases), 3. How to Run the Tests, 4. Verification Evidence, Executive Summary, Manual CLI Execution: (+2 more)

### Community 10 - "WebFetchHandler"
Cohesion: 0.12
Nodes (9): 1. Muc tieu, 2. Quyet dinh da chot voi nguoi dung, 4. Kiem thu, 5. Rui ro da biet, Ngoai pham vi (YAGNI), Tieu chi thanh cong, Web Client (Swing) - Design, FetchParams (+1 more)

### Community 11 - "WebFetchService"
Cohesion: 0.12
Nodes (12): Global Constraints, Review Focus, Task 1: `WebFetchService` + test logic trong `CrossTestClient`, Task 2: `WebClientApp` (Swing) + `run_webclient.bat` + smoke test, Task 3: Tab Rendered + README, Web Client (Swing) Implementation Plan, 3.1 `src/client/WebFetchService.java` (khong dung Swing), 3.2 `src/client/WebClientApp.java` (Swing) (+4 more)

### Community 12 - "java.net.URI"
Cohesion: 0.16
Nodes (4): R2. Bảo đảm đầy đủ các Route và Bài tập mở rộng của Slide, HttpClientErrorDemo, HttpClientGetDemo, HttpClientPostDemo

### Community 14 - "Local/LAN Security Hardening Implementation Plan"
Cohesion: 0.11
Nodes (18): Fetch/proxy, File map, Filesystem, Giới hạn ban đầu, Global Constraints, Local/LAN Security Hardening Implementation Plan, Mạng và quyền truy cập, Quyết định thiết kế (+10 more)

### Community 15 - "Phát hiện"
Cohesion: 0.11
Nodes (17): Kết luận, Lựa chọn và đề xuất, Những bảo vệ đã có, Phát hiện, Phạm vi và bằng chứng, SEC-01 — Cao: sandbox HTTP/FTP theo đường dẫn chuỗi, không theo đích filesystem, SEC-02 — Cao: fetch vượt hạn chế localhost của API quản trị, SEC-03 — Cao, suy luận từ response: HTML không tin cậy được phục vụ cùng origin quản trị (+9 more)

### Community 16 - "Project: Java FTP Server & Client System (RFC 959)"
Cohesion: 0.18
Nodes (10): 1. Control Wire Interface (RFC 959 Client <-> Server), 2. Data Wire Interface (Passive & Active), 3. Client Library Interface (`client.FtpClient`), 4. Cross-Test Runner Contract (`client.CrossTestFtpClient`), Architecture, Code Layout, Feature Inventory, Interface Contracts (+2 more)

### Community 17 - "Detailed Test Tiers (58 Test Cases)"
Cohesion: 0.13
Nodes (14): Coverage Thresholds, Detailed Test Tiers (58 Test Cases), E2E Test Infra: Java FTP Server & Client System, Feature Inventory & Test Mapping, Test Architecture, Test Philosophy, Tier 1: Connection & Protocol Handshake (7 Cases), Tier 2: Authentication & Authorization (6 Cases) (+6 more)

### Community 20 - "Kế Hoạch Triển Khai: Java FTP Server & Java FTP Client (RFC 959)"
Cohesion: 0.18
Nodes (10): 1. Tổng Quan Kiến Trúc & Bối Cảnh, 2. Các Quyết Định Thiết Kế Quan Trọng, 3. Danh Sách Các Tập Tin Cần Tạo & Sửa Đổi, 4. Kế Hoạch Triển Khai Chi Tiết Từng Bước (Tasks), 5. Kế Hoạch Kiểm Thử & Nghiệm Thu (Verification Plan), A. Gói Máy Chủ FTP (`src/server/ftp/`), A. Kiểm thử tự động (Automated Verification), B. Kiểm thử thủ công với Giao diện (Manual GUI Verification) (+2 more)

### Community 21 - "Cay file cua tung site tren trang quan tri - Design"
Cohesion: 0.20
Nodes (9): 1. Muc tieu, 2. Quyet dinh da chot, 3.1 API, 3.2 Trang quan tri, 3. Thiet ke, 4. Ngoai pham vi, 6. Kiem thu, 7. Rui ro da biet (+1 more)

### Community 25 - "Original User Request"
Cohesion: 0.22
Nodes (8): Acceptance Criteria, Initial Request — 2026-10-10T02:30:55Z, Kiểm thử độc lập (Programmatic Verification), Original User Request, R1. Đối chiếu và Chuẩn hóa 100% Java, R3. Bộ kịch bản kiểm thử tự động độc lập, Requirements, Tính chính xác về HTTP Protocol & Slide Specs

### Community 29 - ".isLoopback"
Cohesion: 0.20
Nodes (8): 5. Rang buoc chung, 1. Muc tieu, 2. Quyet dinh da chot voi nguoi dung, 4. Kiem thu (them vao `CrossTestClient`), 5. Rui ro da biet, Multi-site hosting (quan ly nhieu web) - Design, Ngoai pham vi (YAGNI), Tieu chi thanh cong

### Community 30 - ".log"
Cohesion: 0.29
Nodes (5): 3.3 `server.SitesApiHandler` (moi), gan vao `SimpleHttpServer` tai `/api/sites`, 3.4 Trang quan tri `wwwroot/admin.html` (moi) + link tu `wwwroot/index.html`, 3.5 Cau hinh va cleanup, 3. Kien truc, ServerLogger

## Knowledge Gaps
- **89 isolated node(s):** `GUEST`, `USER`, `ADMIN`, `Initial Request — 2026-10-10T02:30:55Z`, `R1. Đối chiếu và Chuẩn hóa 100% Java` (+84 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 205 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `FtpClient` connect `FtpClient` to `FtpSessionHandler`, `FtpClient.java`?**
  _High betweenness centrality (0.146) - this node is a cross-community bridge._
- **Why does `CrossTestClient` connect `CrossTestClient` to `CrossTestClient.java`, `WebFetchHandler`, `WebFetchService`, `Original User Request`, `.isLoopback`?**
  _High betweenness centrality (0.117) - this node is a cross-community bridge._
- **Why does `CrossTestFtpClient` connect `CrossTestFtpClient` to `Project: Java FTP Server & Client System (RFC 959)`, `CrossTestFtpClient.java`?**
  _High betweenness centrality (0.116) - this node is a cross-community bridge._
- **Are the 13 inferred relationships involving `CrossTestClient` (e.g. with `Task 1: `SiteManager.filesJson` + test S40-S45` and `Task 2: Route `GET /api/sites/{ten}/files` + test S46-S48`) actually correct?**
  _`CrossTestClient` has 13 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `CrossTestFtpClient` (e.g. with `4. Cross-Test Runner Contract (`client.CrossTestFtpClient`)` and `1. Autonomous Server Lifecycle Management`) actually correct?**
  _`CrossTestFtpClient` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `GUEST`, `USER`, `ADMIN` to the rest of the system?**
  _89 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `.handle` be split into smaller, more focused modules?**
  _Cohesion score 0.1476923076923077 - nodes in this community are weakly interconnected._