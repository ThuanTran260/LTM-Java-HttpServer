# Multi-site hosting (quan ly nhieu web) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho Java Server quan ly va them duoc nhieu web: moi web (site) co thu muc `sites/<ten>/` va chay tren cong rieng; them / bat / tat / xoa luc dang chay qua trang quan tri `/admin.html` va REST `/api/sites`; luu `sites.json`.

**Architecture:** `SiteManager` giu registry va vong doi: moi site la mot `HttpServer` rieng (phuc vu file tinh qua `SiteFileHandler` -> `StaticFileHandler.tryServe(ex, path, root)`), luu cau hinh vao `sites.json`. `SitesApiHandler` (gan o `/api/sites` tren server chinh, chi loopback) goi `SiteManager`. `wwwroot/admin.html` la giao dien (HTML + JS goi API). Test them vao `CrossTestClient` (S1-S39).

**Tech Stack:** Java 24 thuan (`com.sun.net.httpserver`, `java.nio.file`), HTML/JS thuan cho trang quan tri. Khong thu vien ngoai.

**Spec:** `docs/superpowers/specs/2026-10-10-multisite-design.md`

**Prerequisite:** thuc thi SAU KHI plan WebClient (`2026-10-10-webclient.md`) da commit xong (cung sua `CrossTestClient.java`). Lam tren nhanh moi `feature/multi-site` cat tu `feature/web-client`. Baseline khi bat dau: **103 PASS, 0 FAIL** (neu may khong headless).

## Global Constraints

- Java thuan 100%, JDK 24, **khong them thu vien ngoai**. Code server chi them trong `src/server/`; test trong `src/client/CrossTestClient.java`; trang quan tri trong `wwwroot/`.
- Chuoi trong code Java: tieng Viet **khong dau** (khop code hien co). **Khong viet chuoi backslash-u trong comment Java** (javac xu ly Unicode escape ca trong comment). File HTML/README duoc dung dau.
- Bien dich (tai thu muc goc repo):
  `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
- Chay test (tai thu muc goc repo, vi test doc `wwwroot/` va `sites/` theo duong dan tuong doi):
  `java -cp out client.CrossTestClient; echo "exit=$?"` - tu bat server :8080 / Mock :8081 neu chua co ai nghe cong do.
- **Neu cong 8080 hoac 8081 dang bi tien trinh khac giu (vi du server cua nguoi dung): KHONG kill, KHONG dung toi.** Dung lai va bao NEEDS_CONTEXT kem PID + command line. Ghi chu rieng cho Task 2 va 3: test S20+ goi API qua :8080, nen server tren :8080 phai la ban moi (do chinh runner khoi dong); mot server cu se tra 404 cho `/api/sites`.
- Khi sua file bang cac doan thay the, khop dung ky tu va **dung kieu xuong dong cua file** (file co the dung CRLF do `core.autocrlf=true`); neu cong cu bao khong khop, dung neo mot dong.
- Chi `git add` dung cac file ghi trong task. **Khong** `git add` `ORIGINAL_REQUEST.md`, `query`, `downloaded_source/`, `.superpowers/`, `sites/`, `sites.json`.
- Commit message dang `feat(server): ...`; ket thuc bang `Co-Authored-By: <ten model dang thuc hien> <noreply@anthropic.com>` (theo quy tac attribution cua chinh session thuc hien).

## Review Focus

Tinh huong spec ngu y nhung khong noi ro, de gap nhat khi dung that (moi dong deu co test trong task so huu):

1. Cong bi chuong trinh khac (hoac server chinh :8080) dung -> 409 va site KHONG duoc dang ky; khong lam hong site khac. (Task 1 S14, S15)
2. Tat roi bat lai nhieu lan tren cung mot cong (Windows) -> van mo lai duoc. (Task 1 S10)
3. Duong dan co `..` hoac `%2e%2e` ben trong mot site -> 404, khong lo file ngoai thu muc site. (Task 1 S6, S7)
4. `sites.json` bi sua tay / hong luc khoi dong -> server van len, muc sai bi bo qua. (Task 1 S19)
5. API quan tri goi tu dia chi khong phai loopback -> 403; POST khong co `Content-Type: application/json` (form tu trang khac) -> 415. (Task 2 S37, S21, S31)

---

### Task 1: `SiteManager` + `SiteFileHandler` + `StaticFileHandler` overload + test S1-S19

**Files:**
- Create: `src/server/SiteManager.java`
- Create: `src/server/SiteFileHandler.java`
- Modify: `src/server/StaticFileHandler.java`
- Modify: `src/client/CrossTestClient.java` (import, muc test 11, cac ham test moi)

**Interfaces:**
- Consumes: `RouterHandler.send(HttpExchange,int,String,String)`, `RouterHandler.sendNotFound(HttpExchange)`, `RouterHandler.urlDecodeOr`, `RouterHandler.parseJsonString(String,String)`, `RouterHandler.parseJsonInt(String,String)` (deu da co, `public static`); `ServerLogger.log`.
- Produces (Task 2 va 3 dung):
  - `public final class SiteManager` voi: `SiteManager(Path sitesRoot, Path configFile, int reservedPort)`, `void load()`, `List<SiteView> list()`, `String listJson()`, `SiteView get(String)`, `SiteView add(String name, int port)`, `SiteView start(String)`, `SiteView stop(String)`, `void remove(String)`, `void stopAll()`, `static boolean isValidName(String)`; hang `MIN_PORT=1024`, `MAX_PORT=65535`; tat ca (tru `isValidName`) `synchronized`, cac ham thao tac nem `SiteException`.
  - `public static final class SiteManager.SiteException extends Exception` voi `int status()` (400 / 404 / 409 / 500).
  - `public record SiteManager.SiteView(String name, int port, boolean enabled, boolean running)` voi `url()` va `toJson()` -> `{"name":"..","port":N,"enabled":b,"running":b,"url":"http://localhost:N/"}`.
  - `StaticFileHandler.tryServe(HttpExchange ex, String path, Path webRoot)` (ban 2 tham so cu giu nguyen, goi lai voi `wwwroot`).
  - package-private `SiteFileHandler(Path root)` (HttpHandler).
  - `CrossTestClient`: `private static void testMultiSite(String base) throws Exception` (Task 2, 3 them lenh goi vao day), `testSiteManager()`, cac helper `check` (da co), `freePort()`, `canConnect(int)`, `deleteTree(Path)`, `siteErrorStatus(SiteManager,String,int)`, `siteRemoveStatus(SiteManager,String)`.

- [ ] **Step 1: Viet test S1-S19 (se fail vi chua co `SiteManager`)**

Trong `src/client/CrossTestClient.java`:

(a) Sau dong `import server.SimpleHttpServer;` them dong:

```java
import server.SiteManager;
```

(b) Thay dong (neo mot dong, xuat hien dung 1 lan trong `runTests()`):

```java
        testWebClientApp(base);
```

bang:

```java
        testWebClientApp(base);

        System.out.println("\n--- 11. MULTI-SITE (server.SiteManager, /api/sites, admin.html) ---");
        try {
            testMultiSite(base);
        } catch (Exception e) {
            System.err.println("[FAIL] 11. MULTI-SITE -> Exception: " + e);
            fail++;
        }
```

(c) Chen **ngay truoc** dong `    private static void testBaseTagInComment(String testName) {` cac ham sau:

```java
    private static void testMultiSite(String base) throws Exception {
        testSiteManager();
    }

    private static int freePort() throws java.io.IOException {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static boolean canConnect(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("localhost", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteTree(java.nio.file.Path root) {
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // don dep tren co gang: bo qua
                }
            });
        } catch (Exception ignored) {
            // thu muc khong ton tai: bo qua
        }
    }

    private static int siteErrorStatus(SiteManager mgr, String name, int port) {
        try {
            mgr.add(name, port);
            return 0;
        } catch (SiteManager.SiteException e) {
            return e.status();
        }
    }

    private static int siteRemoveStatus(SiteManager mgr, String name) {
        try {
            mgr.remove(name);
            return 0;
        } catch (SiteManager.SiteException e) {
            return e.status();
        }
    }

    private static void testSiteManager() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("sites-test");
        java.nio.file.Path sitesDir = tmp.resolve("sites");
        java.nio.file.Path cfg = tmp.resolve("sites.json");
        SiteManager mgr = new SiteManager(sitesDir, cfg, 8080);
        try {
            int p1 = freePort();
            SiteManager.SiteView v = mgr.add("alpha", p1);
            check("S1. SiteManager.add -> chay tren cong rieng, tao thu muc + index.html mac dinh",
                v.running() && v.enabled() && v.port() == p1
                    && java.nio.file.Files.isRegularFile(sitesDir.resolve("alpha").resolve("index.html")),
                v.toString());

            java.nio.file.Files.writeString(sitesDir.resolve("alpha").resolve("app.js"), "var x = 1;");
            java.nio.file.Files.writeString(sitesDir.resolve("alpha").resolve("hello.txt"), "hi");
            String site = "http://localhost:" + p1;
            testRequest("S2. GET site alpha / -> index.html mac dinh", "GET", site + "/", null, null, 200, "text/html", "Site alpha");
            testRequest("S3. GET site alpha /hello.txt -> text/plain", "GET", site + "/hello.txt", null, null, 200, "text/plain", "hi");
            testRequest("S4. GET site alpha /app.js -> javascript", "GET", site + "/app.js", null, null, 200, "javascript", "var x");
            testRequest("S5. GET site alpha /missing.html -> 404 text/plain", "GET", site + "/missing.html", null, null, 404, "text/plain", "404");
            testRequest("S6. GET site alpha /../sites.json -> chan Directory Traversal 404", "GET", site + "/../sites.json", null, null, 404, "text/plain", "404");
            testRequest("S7. GET site alpha /%2e%2e/sites.json -> chan traversal ma hoa URL 404", "GET", site + "/%2e%2e/sites.json", null, null, 404, "text/plain", "404");
            testRequest("S8. POST site alpha -> 405 Allow: GET", "POST", site + "/", "text/plain", "x", 405, "text/plain", "405", "Allow", "GET");

            String saved = java.nio.file.Files.readString(cfg);
            check("S9. sites.json duoc ghi sau khi them site",
                saved.contains("\"name\":\"alpha\"") && saved.contains("\"port\":" + p1) && saved.contains("\"enabled\":true"),
                saved);

            boolean cyclesOk = true;
            String cycleDetail = "";
            for (int i = 0; i < 5 && cyclesOk; i++) {
                SiteManager.SiteView stopped = mgr.stop("alpha");
                boolean down = !stopped.running() && !canConnect(p1);
                SiteManager.SiteView started = mgr.start("alpha");
                boolean up = started.running() && canConnect(p1);
                if (!down || !up) {
                    cyclesOk = false;
                    cycleDetail = "vong " + i + ": down=" + down + ", up=" + up;
                }
            }
            check("S10. tat / bat lai site nhieu lan tren cung mot cong", cyclesOk, cycleDetail);

            mgr.stop("alpha");
            String afterStop = java.nio.file.Files.readString(cfg);
            check("S11. tat site -> sites.json ghi enabled=false", afterStop.contains("\"enabled\":false"), afterStop);
            mgr.start("alpha");

            boolean namesOk = true;
            for (String bad : new String[]{"", "A", "-x", "a b", "a/b", "..", "a".repeat(33)}) {
                namesOk &= siteErrorStatus(mgr, bad, freePort()) == 400;
            }
            check("S12. ten site khong hop le -> 400", namesOk, "mot ten sai khong bi tu choi bang 400");
            check("S13. cong ngoai khoang 1024-65535 -> 400",
                siteErrorStatus(mgr, "beta", 80) == 400 && siteErrorStatus(mgr, "beta", 70000) == 400,
                "cong sai khong bi tu choi bang 400");
            check("S14. trung ten / trung cong / cong cua server chinh -> 409",
                siteErrorStatus(mgr, "alpha", freePort()) == 409 && siteErrorStatus(mgr, "beta", p1) == 409
                    && siteErrorStatus(mgr, "beta", 8080) == 409,
                "trung lap khong bi tu choi bang 409");
            try (java.net.ServerSocket busy = new java.net.ServerSocket(0)) {
                int status = siteErrorStatus(mgr, "beta", busy.getLocalPort());
                check("S15. cong dang bi chuong trinh khac dung -> 409 va site khong duoc dang ky",
                    status == 409 && mgr.list().size() == 1, "status=" + status + ", so site=" + mgr.list().size());
            }

            mgr.stopAll();
            SiteManager reloaded = new SiteManager(sitesDir, cfg, 8080);
            reloaded.load();
            check("S16. khoi dong lai: load() doc sites.json va bat lai site da bat",
                reloaded.list().size() == 1 && reloaded.list().get(0).running() && canConnect(p1), reloaded.listJson());
            reloaded.remove("alpha");
            check("S17. remove -> dong cong, bo khoi danh sach, giu nguyen thu muc",
                reloaded.list().isEmpty() && !canConnect(p1) && java.nio.file.Files.isDirectory(sitesDir.resolve("alpha")),
                reloaded.listJson());
            check("S18. remove site khong ton tai -> 404", siteRemoveStatus(reloaded, "alpha") == 404, "khong tra 404");

            java.nio.file.Files.writeString(cfg, "{{ khong phai json [[ {\"name\":\"BAD\",\"port\":9} ]");
            SiteManager broken = new SiteManager(sitesDir, cfg, 8080);
            broken.load();
            check("S19. sites.json hong / muc sai -> bo qua, khong nem exception", broken.list().isEmpty(), broken.listJson());
        } finally {
            mgr.stopAll();
            deleteTree(tmp);
        }
    }

```

- [ ] **Step 2: Bien dich de xac nhan FAIL**

Run: `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
Expected: FAIL, `cannot find symbol` / `package server does not contain SiteManager` (hoac tuong tu) trong `CrossTestClient.java`.

- [ ] **Step 3: Sua `src/server/StaticFileHandler.java`** (ba thay the)

(a) Thay:

```java
    public static boolean tryServe(HttpExchange ex, String path) throws IOException {
        String rel;
```

bang:

```java
    public static boolean tryServe(HttpExchange ex, String path) throws IOException {
        return tryServe(ex, path, Paths.get("wwwroot"));
    }

    public static boolean tryServe(HttpExchange ex, String path, Path webRoot) throws IOException {
        String rel;
```

(b) Thay dong `            Path root = Paths.get("wwwroot").toAbsolutePath().normalize();` bang:

```java
            Path root = webRoot.toAbsolutePath().normalize();
```

(c) Thay dong `        if (n.endsWith(".json")) return "application/json";` bang:

```java
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
```

- [ ] **Step 4: Tao `src/server/SiteFileHandler.java`**

```java
package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Phuc vu file tinh cua mot site (mot thu muc). Dung lai StaticFileHandler de giu nguyen chan Directory Traversal
 * va quy tac Content-Type; chi nhan GET, route khong co file -> 404 text/plain.
 */
final class SiteFileHandler implements HttpHandler {
    private final Path root;

    SiteFileHandler(Path root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Allow", "GET");
                RouterHandler.send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (!StaticFileHandler.tryServe(ex, path, root)) {
                RouterHandler.sendNotFound(ex);
            }
        } catch (Exception e) {
            try {
                RouterHandler.send(ex, 500, "text/plain; charset=utf-8", "500 Internal Server Error");
            } catch (Exception ignored) {
                // phan hoi da gui mot phan: bo qua
            }
        }
    }
}
```

- [ ] **Step 5: Tao `src/server/SiteManager.java`**

```java
package server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quan ly nhieu web (site) tren cung mot Java Server: moi site co mot thu muc rieng va chay tren mot cong rieng
 * (giong IIS Sites/bindings). Them / bat / tat / xoa luc dang chay, luu vao sites.json de khoi dong lai van con.
 * Moi site la mot HttpServer rieng phuc vu file tinh cua thu muc <sitesRoot>/<ten>/.
 */
public final class SiteManager {
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern DISABLED_PATTERN = Pattern.compile("\"enabled\"\\s*:\\s*false");

    /** Loi nghiep vu kem ma HTTP: 400 dau vao sai, 404 khong co site, 409 trung ten/cong hoac cong dang ban, 500 loi ghi file. */
    public static final class SiteException extends Exception {
        private final int status;

        public SiteException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** Anh chup trang thai mot site tai thoi diem goi. */
    public record SiteView(String name, int port, boolean enabled, boolean running) {
        public String url() {
            return "http://localhost:" + port + "/";
        }

        public String toJson() {
            return "{\"name\":\"" + name + "\",\"port\":" + port + ",\"enabled\":" + enabled
                + ",\"running\":" + running + ",\"url\":\"" + url() + "\"}";
        }
    }

    private static final class Site {
        final String name;
        final int port;
        boolean enabled;
        HttpServer server;

        Site(String name, int port, boolean enabled) {
            this.name = name;
            this.port = port;
            this.enabled = enabled;
        }
    }

    private final Path sitesRoot;
    private final Path configFile;
    private final int reservedPort;
    private final Map<String, Site> sites = new LinkedHashMap<>();

    public SiteManager(Path sitesRoot, Path configFile, int reservedPort) {
        this.sitesRoot = sitesRoot;
        this.configFile = configFile;
        this.reservedPort = reservedPort;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /** Doc sites.json va bat cac site enabled. Muc sai / site khong bat duoc chi bi ghi log, khong lam hong ca server. */
    public synchronized void load() {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        String json;
        try {
            json = Files.readString(configFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[SITES] Khong doc duoc " + configFile + ": " + e.getMessage());
            return;
        }
        Matcher m = OBJECT_PATTERN.matcher(json);
        while (m.find()) {
            String obj = m.group();
            String name = RouterHandler.parseJsonString(obj, "name");
            Integer port = RouterHandler.parseJsonInt(obj, "port");
            if (!isValidName(name) || port == null || port < MIN_PORT || port > MAX_PORT
                    || port == reservedPort || sites.containsKey(name) || portTaken(port)) {
                System.err.println("[SITES] Bo qua muc khong hop le trong " + configFile + ": " + obj);
                continue;
            }
            Site s = new Site(name, port, !DISABLED_PATTERN.matcher(obj).find());
            sites.put(name, s);
            try {
                prepareFolder(s);
            } catch (IOException e) {
                System.err.println("[SITES] Khong tao duoc thu muc cua '" + name + "': " + e.getMessage());
            }
            if (s.enabled) {
                try {
                    startSite(s);
                } catch (SiteException e) {
                    System.err.println("[SITES] Khong bat duoc site '" + name + "': " + e.getMessage());
                }
            }
        }
    }

    public synchronized List<SiteView> list() {
        List<SiteView> out = new ArrayList<>();
        for (Site s : sites.values()) {
            out.add(view(s));
        }
        return out;
    }

    public synchronized String listJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Site s : sites.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(view(s).toJson());
        }
        return sb.append("]").toString();
    }

    public synchronized SiteView get(String name) throws SiteException {
        return view(require(name));
    }

    public synchronized SiteView add(String name, int port) throws SiteException {
        if (!isValidName(name)) {
            throw new SiteException(400, "Ten site khong hop le: chi dung a-z, 0-9, dau gach ngang, toi da 32 ky tu, bat dau bang chu hoac so");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new SiteException(400, "Cong phai nam trong khoang " + MIN_PORT + "-" + MAX_PORT);
        }
        if (sites.containsKey(name)) {
            throw new SiteException(409, "Site '" + name + "' da ton tai");
        }
        if (port == reservedPort || portTaken(port)) {
            throw new SiteException(409, "Cong " + port + " da duoc server chinh hoac site khac su dung");
        }
        Site s = new Site(name, port, true);
        startSite(s);
        try {
            prepareFolder(s);
        } catch (IOException e) {
            stopSite(s);
            throw new SiteException(500, "Khong tao duoc thu muc cua site: " + e.getMessage());
        }
        sites.put(name, s);
        try {
            save();
        } catch (SiteException e) {
            stopSite(s);
            sites.remove(name);
            throw e;
        }
        return view(s);
    }

    public synchronized SiteView start(String name) throws SiteException {
        Site s = require(name);
        if (s.server == null) {
            startSite(s);
        }
        s.enabled = true;
        save();
        return view(s);
    }

    public synchronized SiteView stop(String name) throws SiteException {
        Site s = require(name);
        stopSite(s);
        s.enabled = false;
        save();
        return view(s);
    }

    /** Xoa site khoi danh sach va dong cong. Thu muc file cua site duoc giu nguyen. */
    public synchronized void remove(String name) throws SiteException {
        Site s = require(name);
        stopSite(s);
        sites.remove(name);
        save();
    }

    /** Dong moi cong dang mo, khong sua sites.json (dung khi don dep / tat chuong trinh). */
    public synchronized void stopAll() {
        for (Site s : sites.values()) {
            stopSite(s);
        }
    }

    private Site require(String name) throws SiteException {
        Site s = name == null ? null : sites.get(name);
        if (s == null) {
            throw new SiteException(404, "Khong co site '" + name + "'");
        }
        return s;
    }

    private boolean portTaken(int port) {
        for (Site s : sites.values()) {
            if (s.port == port) {
                return true;
            }
        }
        return false;
    }

    private SiteView view(Site s) {
        return new SiteView(s.name, s.port, s.enabled, s.server != null);
    }

    private Path folderOf(Site s) {
        return sitesRoot.resolve(s.name);
    }

    private void prepareFolder(Site s) throws IOException {
        Path dir = folderOf(s);
        Files.createDirectories(dir);
        Path index = dir.resolve("index.html");
        if (!Files.exists(index)) {
            String html = "<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>" + s.name + "</title></head>\n"
                + "<body><h1>Site " + s.name + "</h1><p>Dang chay tai cong " + s.port
                + ". Sua file index.html trong thu muc cua site de doi noi dung.</p></body></html>\n";
            Files.writeString(index, html, StandardCharsets.UTF_8);
        }
    }

    private void startSite(Site s) throws SiteException {
        HttpServer srv = null;
        IOException last = null;
        // Thu lai ngan: cong vua dong (tat roi bat lai ngay) co the con tre vai chuc ms tren Windows
        for (int attempt = 0; attempt < 3 && srv == null; attempt++) {
            try {
                srv = HttpServer.create(new InetSocketAddress(s.port), 0);
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (srv == null) {
            throw new SiteException(409, "Khong mo duoc cong " + s.port + " (co the dang duoc chuong trinh khac su dung): "
                + (last != null ? last.getMessage() : "bi huy"));
        }
        srv.createContext("/", new SiteFileHandler(folderOf(s)));
        srv.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        srv.start();
        s.server = srv;
        System.out.println("[SITES] '" + s.name + "' dang chay tai http://localhost:" + s.port + "/ (thu muc " + folderOf(s) + ")");
    }

    private void stopSite(Site s) {
        if (s.server != null) {
            s.server.stop(0);
            s.server = null;
            System.out.println("[SITES] '" + s.name + "' da tat (cong " + s.port + ")");
        }
    }

    private void save() throws SiteException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Site s : sites.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\n  {\"name\":\"").append(s.name).append("\",\"port\":").append(s.port)
                .append(",\"enabled\":").append(s.enabled).append("}");
        }
        sb.append(first ? "]" : "\n]").append("\n");
        try {
            Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new SiteException(500, "Khong ghi duoc " + configFile + ": " + e.getMessage());
        }
    }
}
```

- [ ] **Step 6: Bien dich va chay toan bo test**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient; echo "exit=$?"
```
Expected: bien dich khong loi; `[PASS] S1.` ... `[PASS] S19.`; dong cuoi `KET QUA: 122 PASS, 0 FAIL` (103 + 19) va `exit=0`. Neu so baseline khac 103 (vi may headless), tong phai bang baseline + 19.
Neu mot test fail: sua `SiteManager` / `SiteFileHandler` (khong sua test cho khop code sai).

- [ ] **Step 7: Commit**

```bash
git add src/server/SiteManager.java src/server/SiteFileHandler.java src/server/StaticFileHandler.java src/client/CrossTestClient.java
git commit -m "feat(server): add SiteManager to host multiple websites on separate ports" -m "Co-Authored-By: <model> <noreply@anthropic.com>"
```

---

### Task 2: `SitesApiHandler` + noi vao `SimpleHttpServer` + test S20-S37

**Files:**
- Create: `src/server/SitesApiHandler.java`
- Modify: `src/server/SimpleHttpServer.java`
- Modify: `src/server/ServerLogger.java`
- Modify: `src/client/CrossTestClient.java`

**Interfaces:**
- Consumes (Task 1): `SiteManager` (`listJson`, `get`, `add`, `start`, `stop`, `remove`, `SiteException.status()`, `SiteView.toJson()`), `RouterHandler.send / sendNotFound / urlDecodeOr / parseJsonString / parseJsonInt`; trong `CrossTestClient`: `testMultiSite`, `freePort`, `canConnect`, `deleteTree`, `check`, `testRequest`.
- Produces (Task 3 dung): `public class SitesApiHandler implements HttpHandler` voi `SitesApiHandler(SiteManager)` va `public static boolean isLoopback(InetSocketAddress)`; `/api/sites` hoat dong tren server chinh; `CrossTestClient.testSitesApi(String base)`.

- [ ] **Step 1: Viet test S20-S37 (se fail vi chua co `SitesApiHandler`)**

Trong `src/client/CrossTestClient.java`:

(a) Sau dong `import server.SiteManager;` them:

```java
import server.SitesApiHandler;
```

(b) Thay dong (xuat hien dung 1 lan, ben trong `testMultiSite`):

```java
        testSiteManager();
```

bang:

```java
        testSiteManager();
        testSitesApi(base);
```

(c) Chen **ngay truoc** dong `    private static void testBaseTagInComment(String testName) {`:

```java
    private static void httpDeleteQuietly(String url) {
        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // don dep tren co gang: bo qua
        }
    }

    private static void testSitesApi(String base) throws Exception {
        check("S37. SitesApiHandler.isLoopback chi chap nhan dia chi loopback",
            SitesApiHandler.isLoopback(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 1234))
                && !SitesApiHandler.isLoopback(new java.net.InetSocketAddress("192.168.1.10", 1234))
                && !SitesApiHandler.isLoopback(null),
            "isLoopback tra ket qua sai");

        String api = base + "/api/sites";
        String name = "zz-api-test";
        int port = freePort();
        String siteApi = api + "/" + name;
        try {
            testRequest("S20. GET /api/sites -> mang JSON", "GET", api, null, null, 200, "application/json", "[");
            testRequest("S21. POST /api/sites sai Content-Type -> 415", "POST", api, "text/plain", "{}", 415, "text/plain", "415");
            testRequest("S22. POST /api/sites thieu port -> 400", "POST", api, "application/json",
                "{\"name\":\"" + name + "\"}", 400, "text/plain", "400");
            testRequest("S23. POST /api/sites ten khong hop le -> 400", "POST", api, "application/json",
                "{\"name\":\"A B\",\"port\":" + port + "}", 400, "text/plain", "400");
            testRequest("S24. POST /api/sites hop le -> 201, site dang chay", "POST", api, "application/json",
                "{\"name\":\"" + name + "\",\"port\":" + port + "}", 201, "application/json", "\"running\":true");
            testRequest("S25. GET site vua them tren cong rieng -> index.html mac dinh", "GET",
                "http://localhost:" + port + "/", null, null, 200, "text/html", "Site " + name);
            testRequest("S26. POST /api/sites trung ten -> 409", "POST", api, "application/json",
                "{\"name\":\"" + name + "\",\"port\":" + freePort() + "}", 409, "text/plain", "409 Conflict");
            testRequest("S27. POST /api/sites/{name}/stop -> 200, running=false", "POST", siteApi + "/stop",
                "application/json", "{}", 200, "application/json", "\"running\":false");
            check("S28. sau khi stop cong da dong", !canConnect(port), "cong " + port + " van mo");
            testRequest("S29. POST /api/sites/{name}/start -> 200, running=true", "POST", siteApi + "/start",
                "application/json", "{}", 200, "application/json", "\"running\":true");
            check("S30. sau khi start cong mo lai", canConnect(port), "cong " + port + " khong mo");
            testRequest("S31. POST /api/sites/{name}/start sai Content-Type -> 415", "POST", siteApi + "/start",
                "text/plain", "x", 415, "text/plain", "415");
            testRequest("S32. POST /api/sites/khong-co/start -> 404", "POST", api + "/khong-co/start",
                "application/json", "{}", 404, "text/plain", "404");
            testRequest("S33. PUT /api/sites -> 405 Allow co POST", "PUT", api, "application/json", "{}",
                405, "text/plain", "405", "Allow", "POST");
            testRequest("S34. DELETE /api/sites/{name} -> 200", "DELETE", siteApi, null, null, 200, "application/json", "Deleted");
            check("S35. sau khi DELETE cong da dong", !canConnect(port), "cong " + port + " van mo");
            testRequest("S36. DELETE lai site da xoa -> 404", "DELETE", siteApi, null, null, 404, "text/plain", "404");
        } finally {
            httpDeleteQuietly(siteApi);
            deleteTree(java.nio.file.Paths.get("sites", name));
        }
    }

```

- [ ] **Step 2: Bien dich de xac nhan FAIL**

Run: `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
Expected: FAIL, `cannot find symbol ... SitesApiHandler`.

- [ ] **Step 3: Tao `src/server/SitesApiHandler.java`**

```java
package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * REST quan ly cac web (site), chi cho phep goi tu may local (loopback):
 *   GET    /api/sites                danh sach
 *   POST   /api/sites                them {"name":"blog","port":9001} -> 201
 *   GET    /api/sites/{name}         chi tiet
 *   POST   /api/sites/{name}/start   bat -> 200
 *   POST   /api/sites/{name}/stop    tat -> 200
 *   DELETE /api/sites/{name}         xoa khoi danh sach (giu nguyen thu muc) -> 200
 * Moi POST bat buoc Content-Type: application/json (form POST tu trang khac se bi 415).
 */
public class SitesApiHandler implements HttpHandler {
    private static final String BASE = "/api/sites";
    private static final String JSON = "application/json";
    private static final String TEXT = "text/plain; charset=utf-8";

    private final SiteManager manager;

    public SitesApiHandler(SiteManager manager) {
        this.manager = manager;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!isLoopback(ex.getRemoteAddress())) {
                RouterHandler.send(ex, 403, TEXT, "403 Forbidden: chi cho phep goi tu may local");
                return;
            }
            String path = ex.getRequestURI().getPath();
            String sub = path.length() > BASE.length() ? path.substring(BASE.length()) : "";
            while (sub.endsWith("/")) {
                sub = sub.substring(0, sub.length() - 1);
            }
            String method = ex.getRequestMethod();

            if (sub.isEmpty()) {
                if (method.equals("GET")) {
                    RouterHandler.send(ex, 200, JSON, manager.listJson());
                } else if (method.equals("POST")) {
                    addSite(ex);
                } else {
                    methodNotAllowed(ex, "GET, POST");
                }
                return;
            }
            if (!sub.startsWith("/")) {
                RouterHandler.sendNotFound(ex);
                return;
            }

            String[] parts = sub.substring(1).split("/", -1);
            String name = RouterHandler.urlDecodeOr(parts[0], parts[0]);
            if (parts.length == 1) {
                if (method.equals("GET")) {
                    RouterHandler.send(ex, 200, JSON, manager.get(name).toJson());
                } else if (method.equals("DELETE")) {
                    manager.remove(name);
                    RouterHandler.send(ex, 200, JSON, "{\"message\":\"Deleted\"}");
                } else {
                    methodNotAllowed(ex, "GET, DELETE");
                }
                return;
            }
            if (parts.length == 2 && (parts[1].equals("start") || parts[1].equals("stop"))) {
                if (!method.equals("POST")) {
                    methodNotAllowed(ex, "POST");
                    return;
                }
                if (!requireJson(ex)) {
                    return;
                }
                SiteManager.SiteView v = parts[1].equals("start") ? manager.start(name) : manager.stop(name);
                RouterHandler.send(ex, 200, JSON, v.toJson());
                return;
            }
            RouterHandler.sendNotFound(ex);
        } catch (SiteManager.SiteException e) {
            RouterHandler.send(ex, e.status(), TEXT, e.status() + " " + reason(e.status()) + ": " + e.getMessage());
        } catch (Exception e) {
            try {
                RouterHandler.send(ex, 500, TEXT, "500 Internal Server Error");
            } catch (Exception ignored) {
                // phan hoi da gui mot phan: bo qua
            }
        }
    }

    private void addSite(HttpExchange ex) throws IOException, SiteManager.SiteException {
        if (!requireJson(ex)) {
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String name = RouterHandler.parseJsonString(body, "name");
        Integer port = RouterHandler.parseJsonInt(body, "port");
        if (name == null || port == null) {
            RouterHandler.send(ex, 400, TEXT, "400 Bad Request: can JSON {\"name\":\"ten-site\",\"port\":9001}");
            return;
        }
        RouterHandler.send(ex, 201, JSON, manager.add(name, port).toJson());
    }

    private static boolean requireJson(HttpExchange ex) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase().startsWith(JSON)) {
            RouterHandler.send(ex, 415, TEXT, "415 Unsupported Media Type");
            return false;
        }
        return true;
    }

    private static void methodNotAllowed(HttpExchange ex, String allow) throws IOException {
        ex.getResponseHeaders().set("Allow", allow);
        RouterHandler.send(ex, 405, TEXT, "405 Method Not Allowed");
    }

    /** Chi dia chi loopback (127.0.0.1 hoac ::1) moi duoc goi API quan ly. */
    public static boolean isLoopback(InetSocketAddress remote) {
        return remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
    }

    private static String reason(int status) {
        switch (status) {
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 409: return "Conflict";
            default: return "Internal Server Error";
        }
    }
}
```

- [ ] **Step 4: Sua `src/server/SimpleHttpServer.java`**

(a) Sau dong `import java.net.InetSocketAddress;` them:

```java
import java.nio.file.Paths;
```

(b) Thay dong `        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);` bang:

```java
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        SiteManager sites = new SiteManager(Paths.get("sites"), Paths.get("sites.json"), port);
        sites.load();
        server.createContext("/api/sites", new SitesApiHandler(sites));
```

(c) Thay dong `        System.out.println("Web Fetcher: http://localhost:" + port + "/fetch?url=...  (format=json)");` bang:

```java
        System.out.println("Web Fetcher: http://localhost:" + port + "/fetch?url=...  (format=json)");
        System.out.println("Quan ly nhieu web: http://localhost:" + port + "/admin.html  (API: /api/sites)");
```

- [ ] **Step 5: Sua `src/server/ServerLogger.java`** - thay dong `            case 404: return "Not Found";` bang:

```java
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
```

- [ ] **Step 6: Bien dich va chay toan bo test**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient; echo "exit=$?"
```
Expected: bien dich khong loi; `[PASS] S20.` ... `[PASS] S37.`; dong cuoi `KET QUA: 140 PASS, 0 FAIL` (baseline cua task + 18) va `exit=0`.
Luu y: runner chi tu khoi dong server :8080 khi chua co ai nghe cong do. Neu :8080 dang co mot server khac, xem rang buoc "Global Constraints" (khong kill; bao NEEDS_CONTEXT).
Sau khi chay xong, kiem tra khong con thu muc `sites/zz-api-test/` (test da don dep).

- [ ] **Step 7: Commit**

```bash
git add src/server/SitesApiHandler.java src/server/SimpleHttpServer.java src/server/ServerLogger.java src/client/CrossTestClient.java
git commit -m "feat(server): add /api/sites REST API to add, start, stop and remove websites" -m "Co-Authored-By: <model> <noreply@anthropic.com>"
```

---

### Task 3: Trang quan tri `admin.html` + link + `.gitignore` + README + test S38-S39

**Files:**
- Create: `wwwroot/admin.html`
- Modify: `wwwroot/index.html`
- Modify: `.gitignore`
- Modify: `README.md`
- Modify: `src/client/CrossTestClient.java`

**Interfaces:**
- Consumes (Task 2): REST `/api/sites` (GET list, POST add, POST `/{name}/start|stop`, DELETE `/{name}`) va JSON site `{name,port,enabled,running,url}`; trong `CrossTestClient`: `testMultiSite`, `testRequest`.
- Produces: trang `/admin.html`; `CrossTestClient.testAdminPage(String base)`.

- [ ] **Step 1: Viet test S38-S39 (se fail vi chua co trang)**

Trong `src/client/CrossTestClient.java`:

(a) Thay dong (xuat hien dung 1 lan trong `testMultiSite`):

```java
        testSitesApi(base);
```

bang:

```java
        testSitesApi(base);
        testAdminPage(base);
```

(b) Chen **ngay truoc** dong `    private static void testBaseTagInComment(String testName) {`:

```java
    private static void testAdminPage(String base) {
        testRequest("S38. GET /admin.html -> trang quan tri goi /api/sites", "GET", base + "/admin.html", null, null, 200, "text/html", "/api/sites");
        testRequest("S39. GET /index.html -> co link toi trang quan tri", "GET", base + "/index.html", null, null, 200, "text/html", "/admin.html");
    }

```

- [ ] **Step 2: Chay de xac nhan FAIL**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient; echo "exit=$?"
```
Expected: bien dich OK; `[FAIL] S38.` (status 404) va `[FAIL] S39.` (khong co link); exit=1.

- [ ] **Step 3: Tao `wwwroot/admin.html`**

```html
<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><title>Quản lý nhiều web (Sites)</title>
<style>
body{font-family:system-ui,"Segoe UI",Arial,sans-serif;margin:24px;color:#1e293b}
h1{margin-top:0}
form{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}
input{padding:8px 10px;border:1px solid #cbd5e1;border-radius:6px;font-size:14px}
button{padding:8px 14px;border:none;border-radius:6px;background:#0a6cff;color:#fff;font-weight:bold;cursor:pointer;margin-right:6px}
button.secondary{background:#64748b}
button.danger{background:#b01e1e}
table{border-collapse:collapse;width:100%;margin-top:12px}
th,td{border:1px solid #d0d7de;padding:8px 10px;text-align:left}
th{background:#f1f5f9}
.ok{color:#1b6e2e;font-weight:600}
.off{color:#b01e1e;font-weight:600}
#msg{margin:10px 0;min-height:1.4em}
.good{color:#1b6e2e}
.err{color:#b01e1e}
code{background:#f1f5f9;padding:1px 5px;border-radius:4px}
</style></head>
<body>
<h1>Quản lý nhiều web (Sites)</h1>
<p>Mỗi web chạy trên <b>một cổng riêng</b> và phục vụ thư mục <code>sites/&lt;tên&gt;/</code>. Trang này chỉ dùng được từ máy local. <a href="/">Về trang chủ</a></p>

<form id="add-form">
<input id="name" placeholder="tên-site (a-z, 0-9, -)" pattern="[a-z0-9][a-z0-9-]{0,31}" required>
<input id="port" type="number" min="1024" max="65535" placeholder="cổng, ví dụ 9001" required>
<button type="submit">Thêm web</button>
</form>
<div id="msg"></div>

<table>
<thead><tr><th>Tên</th><th>Cổng</th><th>Trạng thái</th><th>Địa chỉ</th><th>Thao tác</th></tr></thead>
<tbody id="rows"></tbody>
</table>

<script>
const msg = document.getElementById('msg');

function say(text, ok) {
  msg.textContent = text;
  msg.className = ok ? 'good' : 'err';
}

async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: {'Content-Type': 'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await res.text();
  if (!res.ok) throw new Error(text || ('HTTP ' + res.status));
  return text ? JSON.parse(text) : null;
}

function cell(tr, text) {
  const td = document.createElement('td');
  td.textContent = text;
  tr.appendChild(td);
  return td;
}

function button(label, cls, handler) {
  const b = document.createElement('button');
  b.textContent = label;
  if (cls) b.className = cls;
  b.addEventListener('click', handler);
  return b;
}

async function act(label, fn) {
  try {
    await fn();
    say(label + ' thành công', true);
  } catch (e) {
    say(e.message, false);
  }
  await refresh();
}

async function refresh() {
  const rows = document.getElementById('rows');
  let sites;
  try {
    sites = await api('GET', '/api/sites');
  } catch (e) {
    say('Không tải được danh sách: ' + e.message, false);
    return;
  }
  rows.replaceChildren();
  if (sites.length === 0) {
    const tr = document.createElement('tr');
    cell(tr, 'Chưa có web nào. Hãy thêm web đầu tiên ở phía trên.').colSpan = 5;
    rows.appendChild(tr);
    return;
  }
  for (const s of sites) {
    const base = '/api/sites/' + encodeURIComponent(s.name);
    const tr = document.createElement('tr');
    cell(tr, s.name);
    cell(tr, String(s.port));
    cell(tr, s.running ? 'Đang chạy' : 'Đã tắt').className = s.running ? 'ok' : 'off';
    const link = cell(tr, '');
    const a = document.createElement('a');
    a.href = s.url;
    a.target = '_blank';
    a.rel = 'noopener';
    a.textContent = s.url;
    link.appendChild(a);
    const actions = cell(tr, '');
    actions.appendChild(s.running
      ? button('Tắt', 'secondary', () => act('Tắt ' + s.name, () => api('POST', base + '/stop', {})))
      : button('Bật', '', () => act('Bật ' + s.name, () => api('POST', base + '/start', {}))));
    actions.appendChild(button('Xóa', 'danger', () => {
      if (confirm('Xóa web "' + s.name + '" khỏi danh sách? (thư mục file được giữ lại)')) {
        act('Xóa ' + s.name, () => api('DELETE', base));
      }
    }));
    rows.appendChild(tr);
  }
}

document.getElementById('add-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('name').value.trim();
  const port = parseInt(document.getElementById('port').value, 10);
  await act('Thêm ' + name, () => api('POST', '/api/sites', {name, port}));
});

refresh();
</script>
</body></html>
```

- [ ] **Step 4: Them link vao `wwwroot/index.html`** - thay dong (mot dong, duy nhat):

```html
<li><a href="/fetch?url=http://localhost:8081/">GET /fetch?url=http://localhost:8081/ (Web Fetcher / Proxy)</a></li>
```

bang:

```html
<li><a href="/fetch?url=http://localhost:8081/">GET /fetch?url=http://localhost:8081/ (Web Fetcher / Proxy)</a></li>
<li><a href="/admin.html">Quản lý nhiều web (Sites) - thêm / bật / tắt / xóa web chạy trên cổng riêng</a></li>
```

- [ ] **Step 5: Cap nhat `.gitignore`** - them vao **cuoi file**:

```
# Web do nguoi dung them qua trang quan tri (SiteManager)
sites/
sites.json
sites.json.tmp
```

- [ ] **Step 6: Cap nhat `README.md`**

(a) Thay dong:
`│   ├── server/       # SimpleHttpServer, RouterHandler, ProxyHandler, StaticFileHandler, ServerLogger`
bang:
`│   ├── server/       # SimpleHttpServer, RouterHandler, ProxyHandler, StaticFileHandler, ServerLogger, SiteManager, SiteFileHandler, SitesApiHandler`

(b) Them vao **cuoi file**:

````markdown

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
````

- [ ] **Step 7: Bien dich va chay toan bo test**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient; echo "exit=$?"
```
Expected: `[PASS] S38.`, `[PASS] S39.`; dong cuoi `KET QUA: 142 PASS, 0 FAIL` (baseline cua task + 2) va `exit=0`. Test 73 (index.html van co `action="/fetch"`) van PASS. Xac nhan `git status` khong hien `sites/` hay `sites.json` (da bi `.gitignore`).

- [ ] **Step 8: Commit**

```bash
git add wwwroot/admin.html wwwroot/index.html .gitignore README.md src/client/CrossTestClient.java
git commit -m "feat(server): add admin page for managing multiple websites" -m "Co-Authored-By: <model> <noreply@anthropic.com>"
```
