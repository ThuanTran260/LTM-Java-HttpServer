# Graph Report - Http server  (2026-10-10)

## Corpus Check
- 13 files · ~498,541 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 11 file(s) not represented in the graph (top: .bat 9, (none) 1, .css 1)

## Summary
- 91 nodes · 146 edges · 8 communities (3 shown, 5 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 9 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fc715034`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- HttpClientErrorDemo.java
- StudentService
- RouterHandler.java
- .handle
- .handle
- BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)
- .tryServe
- CrossTestClient

## God Nodes (most connected - your core abstractions)
1. `RouterHandler` - 12 edges
2. `StudentService` - 11 edges
3. `Student` - 9 edges
4. `CrossTestClient` - 7 edges
5. `BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)` - 7 edges
6. `ProxyHandler` - 5 edges
7. `StaticFileHandler` - 4 edges
8. `ServerLogger` - 3 edges
9. `HttpClientErrorDemo` - 2 edges
10. `HttpClientGetDemo` - 2 edges

## Surprising Connections (you probably didn't know these)
- `RouterHandler` --references--> `StudentService`  [EXTRACTED]
  src/server/RouterHandler.java → src/service/StudentService.java
- `StudentService` --references--> `Student`  [EXTRACTED]
  src/service/StudentService.java → src/model/Student.java

## Import Cycles
- None detected.

## Communities (8 total, 5 thin omitted)

### Community 0 - "HttpClientErrorDemo.java"
Cohesion: 0.16
Nodes (3): HttpClientErrorDemo, HttpClientGetDemo, HttpClientPostDemo

### Community 4 - ".handle"
Cohesion: 0.24
Nodes (3): ProxyHandler, ServerLogger, SimpleHttpServer

### Community 5 - "BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA)"
Cohesion: 0.22
Nodes (8): ⚡ 1. Khởi Động Nhanh (1-Click), 🌐 2. Kiểm Tra Bằng Trình Duyệt (Firefox / Chrome), 🧪 3. Các File Kiểm Thử Riêng Lẻ (Chạy Bằng 1 Click), 🛑 4. Quản Lý IIS Thật Của Windows (w3svc) Bằng Lệnh, 🛠️ 5. Biên Dịch Lại Mã Nguồn (Khi Có Sửa Code), 📁 6. Cấu Trúc Mã Nguồn, BÀI 12: HTTP SERVER VÀ GIAO TIẾP MẠNG CHÉO (JAVA), Các API có sẵn trên `http://localhost:8080/`

## Knowledge Gaps
- **6 isolated node(s):** `⚡ 1. Khởi Động Nhanh (1-Click)`, `🌐 2. Kiểm Tra Bằng Trình Duyệt (Firefox / Chrome)`, `Các API có sẵn trên `http://localhost:8080/``, `🛑 4. Quản Lý IIS Thật Của Windows (w3svc) Bằng Lệnh`, `🛠️ 5. Biên Dịch Lại Mã Nguồn (Khi Có Sửa Code)` (+1 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 24 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RouterHandler` connect `.handle` to `StudentService`, `RouterHandler.java`, `.handle`?**
  _High betweenness centrality (0.166) - this node is a cross-community bridge._
- **Why does `ProxyHandler` connect `.handle` to `HttpClientErrorDemo.java`, `.handle`?**
  _High betweenness centrality (0.125) - this node is a cross-community bridge._
- **Why does `CrossTestClient` connect `CrossTestClient` to `HttpClientErrorDemo.java`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **What connects `⚡ 1. Khởi Động Nhanh (1-Click)`, `🌐 2. Kiểm Tra Bằng Trình Duyệt (Firefox / Chrome)`, `Các API có sẵn trên `http://localhost:8080/`` to the rest of the system?**
  _6 weakly-connected nodes found - possible documentation gaps or missing edges._