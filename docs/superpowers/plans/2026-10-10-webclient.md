# Web Client (Swing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Them app Swing `WebClientApp` goi WebFetch cua Java Server (`/fetch?url=...`) va hien thi ma nguon trang web, kem logic goi mang tach rieng `WebFetchService` duoc test boi `CrossTestClient`.

**Architecture:** `WebFetchService` (khong Swing) dung `java.net.http.HttpClient` goi `GET <server>/fetch?[format=json&]url=<encoded>` va tra ve `Result` (khong nem exception). `WebClientApp` (Swing) goi service trong `SwingWorker`, hien Source / Rendered / thanh trang thai. Test them vao `CrossTestClient` (logic: test 83-94; Swing smoke: test 95-99).

**Tech Stack:** Java 24 thuan (`java.net.http`, Swing `javax.swing`), khong thu vien ngoai. Windows, build bang `javac`.

**Spec:** `docs/superpowers/specs/2026-10-10-webclient-design.md`

## Global Constraints

- Java thuan 100%, JDK 24, **khong them thu vien ngoai**; chi dung JDK (`java.net.http`, `javax.swing`).
- Code moi chi nam trong package `client` (`src/client/`) + `run_webclient.bat` + `README.md` + `docs/`. **Khong sua `src/server/*`, `src/mock/*`, `src/service/*`, `src/model/*`.**
- Chuoi trong code Java: tieng Viet **khong dau** (khop code hien co). **Khong viet chuoi backslash-u (vi du `\u2028`) trong comment** - javac xu ly Unicode escape ca trong comment va se bao loi. Trong chuoi/ky tu literal thi duoc dung.
- Bien dich (chay tai thu muc goc repo, Git Bash hoac PowerShell):
  `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
  (khop voi `compile.bat`; `compile.bat` da bao gom `src\client\*.java` nen khong can sua).
- Chay test (phai chay tu thu muc goc vi test doc `wwwroot/` theo duong dan tuong doi):
  `java -cp out client.CrossTestClient` - tu khoi dong server :8080 / Mock :8081 neu chua chay. Exit code 0 = 0 FAIL.
- **Baseline hien tai: 85 PASS, 0 FAIL.** Moi task phai giu 0 FAIL.
- `out/` bi git-ignore; khong commit. **Khong `git add` cac file khong lien quan:** `ORIGINAL_REQUEST.md`, `query`, `downloaded_source/` (chi `git add` dung cac file ghi trong task).
- Commit message dang `feat(client): ...`, ket thuc bang dong:
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`

## Review Focus

Cac dau vao / tinh huong spec ngu y nhung khong noi ro, de gap nhat khi dung that (moi dong deu co test trong task so huu):

1. Server WebFetch chua bat / sai cong -> thong bao loi ro rang, nut Fetch bat lai, cua so khong treo. (Task 1 test 91; Task 2 test 97)
2. Noi dung nhi phan (vi du `sample.png`) -> khong in rac ra Source, chi hien placeholder, `contentLength` la so byte that. (Task 1 test 92)
3. Trang khong UTF-8 hoac chi khai bao charset trong the `<meta>` -> giai ma dung charset. (Task 1 test 94)
4. Body chua ky tu dac biet (dau nhay kep, xuong dong, tab, ky tu line-separator U+2028, ky tu dieu khien, chu Viet) -> parse JSON tra lai nguyen ven. (Task 1 test 84)
5. Tab Rendered voi trang co `<meta charset>` va `<link rel=stylesheet>` (chinh la `wwwroot/index.html`) -> render du noi dung, khong bi cat, khong chan EDT. (Task 3 test 98)

---

### Task 1: `WebFetchService` + test logic trong `CrossTestClient`

**Files:**
- Create: `src/client/WebFetchService.java`
- Modify: `src/client/CrossTestClient.java` (them muc test 9: sau dong goi `testBaseTagInComment("82. ...")`, va them 2 ham truoc `private static void testBaseTagInComment`)

**Interfaces:**
- Consumes: `server.WebFetchHandler.buildJsonMetadata(String targetUrl, int status, String contentType, long contentLength, String body)` (da co, public static) - chi dung trong test.
- Produces (Task 2 va 3 dung):
  - `public final class WebFetchService`
  - `public static final String DEFAULT_SERVER = "http://localhost:8080"`
  - `public WebFetchService()` / `public WebFetchService(String serverBase)` / `public String getServerBase()`
  - `public String buildRequestUrl(String targetUrl, boolean json)`
  - `public Result fetch(String targetUrl, boolean json)` (khong nem exception)
  - `public record Result(String requestUrl, boolean jsonMode, int status, String contentType, long contentLength, String body, String rawResponse, String error)` voi `public boolean ok()`
  - `public static boolean isHtml(String contentType)`, `public static boolean isTextual(String contentType)`
  - `public static String suggestFileName(Result r)` -> `"response.json"` | `"source.html"` | `"source.txt"`
  - package-private: `static Charset charsetOf(String contentType, byte[] bytes)`, `static Map<String,String> parseFlatJson(String json)`

- [ ] **Step 1: Viet test (se fail vi chua co `WebFetchService`)**

Trong `src/client/CrossTestClient.java`, thay doan:

```java
        testBaseTagInComment("82. Base tag injection ignores <head> inside HTML comments");
    }
```

bang:

```java
        testBaseTagInComment("82. Base tag injection ignores <head> inside HTML comments");

        System.out.println("\n--- 9. WEB CLIENT (client.WebFetchService) ---");
        try {
            testWebFetchService(base);
        } catch (Exception e) {
            System.err.println("[FAIL] 83-94. WebFetchService -> Exception: " + e);
            fail++;
        }
    }
```

Roi chen **ngay truoc** dong `    private static void testBaseTagInComment(String testName) {` hai ham sau:

```java
    private static void check(String testName, boolean ok, String detail) {
        if (ok) {
            System.out.println("[PASS] " + testName);
            pass++;
        } else {
            System.err.println("[FAIL] " + testName + " -> " + detail);
            fail++;
        }
    }

    private static void testWebFetchService(String base) throws Exception {
        WebFetchService svc = new WebFetchService(base);
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;

        String built = svc.buildRequestUrl("http://a.com/x?y=1&z=2", false);
        String builtJson = svc.buildRequestUrl("http://a.com/", true);
        check("83. buildRequestUrl ma hoa URL dich va them format=json",
            built.equals(base + "/fetch?url=http%3A%2F%2Fa.com%2Fx%3Fy%3D1%26z%3D2")
                && builtJson.equals(base + "/fetch?format=json&url=http%3A%2F%2Fa.com%2F"),
            built + " | " + builtJson);

        String nasty = "<p class=\"a\">C:\\dir\nline2\ttab\r\n\u2028 ctrl\u0001 d\u0111\u1ea1</p>";
        String json = server.WebFetchHandler.buildJsonMetadata("http://a.com/", 200, "text/html; charset=utf-8", 123, nasty);
        java.util.Map<String, String> parsed = WebFetchService.parseFlatJson(json);
        check("84. parseFlatJson doc nguyen ven JSON do WebFetchHandler sinh ra (ky tu dac biet)",
            nasty.equals(parsed.get("body")) && "200".equals(parsed.get("status"))
                && "123".equals(parsed.get("contentLength")) && "http://a.com/".equals(parsed.get("targetUrl"))
                && "text/html; charset=utf-8".equals(parsed.get("contentType")),
            String.valueOf(parsed));

        WebFetchService.Result raw = svc.fetch(base + "/index.html", false);
        check("85. fetch raw /index.html -> 200, HTML, co <base> do server chen, contentLength dung",
            raw.ok() && raw.status() == 200 && !raw.jsonMode() && WebFetchService.isHtml(raw.contentType())
                && raw.body().contains("<head><base href=\"" + base + "/index.html\">")
                && raw.rawResponse().equals(raw.body())
                && raw.contentLength() == raw.body().getBytes(utf8).length,
            raw.toString());

        WebFetchService.Result js = svc.fetch(base + "/index.html", true);
        check("86. fetch json /index.html -> tach dung metadata, body/contentLength trung che do raw",
            js.ok() && js.jsonMode() && js.status() == 200 && js.rawResponse().startsWith("{\"targetUrl\":")
                && js.body().equals(raw.body()) && js.contentLength() == raw.contentLength()
                && !js.rawResponse().equals(js.body()),
            js.toString());

        WebFetchService.Result ftp = svc.fetch("ftp://speedtest.tele2.net", false);
        check("87. fetch ftp:// (raw) -> server tu choi 400, client hien thong bao cua server",
            ftp.ok() && ftp.status() == 400 && ftp.body().contains("unsupported scheme"), ftp.toString());

        WebFetchService.Result ftpJson = svc.fetch("ftp://speedtest.tele2.net", true);
        check("88. fetch ftp:// (json) -> 400 la text/plain, khong bi coi la loi parse JSON",
            ftpJson.ok() && ftpJson.status() == 400 && ftpJson.body().contains("unsupported scheme")
                && ftpJson.body().equals(ftpJson.rawResponse()),
            ftpJson.toString());

        WebFetchService.Result blank = svc.fetch("   ", false);
        check("89. fetch URL rong -> loi phia client, khong goi mang",
            !blank.ok() && blank.status() == -1 && blank.error().contains("trong"), blank.toString());

        WebFetchService.Result bad = svc.fetch("http://localhost:59999/", false);
        WebFetchService.Result badJson = svc.fetch("http://localhost:59999/", true);
        check("90. fetch trang dich khong ton tai -> server tra 502 (raw va json)",
            bad.ok() && bad.status() == 502 && bad.body().contains("502 Bad Gateway")
                && badJson.ok() && badJson.status() == 502 && badJson.rawResponse().startsWith("{")
                && badJson.body().contains("502 Bad Gateway"),
            bad + " | " + badJson);

        WebFetchService.Result down = new WebFetchService("http://localhost:59998").fetch("http://localhost:8081/", false);
        check("91. WebFetch server tat -> Result.error ro rang (khong nem exception)",
            !down.ok() && down.status() == -1 && down.error().contains("Khong ket noi"), down.toString());

        WebFetchService.Result png = svc.fetch(base + "/sample.png", false);
        long pngSize = java.nio.file.Files.size(java.nio.file.Paths.get("wwwroot", "sample.png"));
        check("92. fetch anh PNG (raw) -> placeholder nhi phan, contentLength = so byte that",
            png.ok() && png.status() == 200 && png.contentType().contains("image/png")
                && png.body().contains("nhi phan") && png.contentLength() == pngSize,
            png.toString());

        WebFetchService.Result jsonOk = new WebFetchService.Result("u", true, 200, "text/html", 1, "b", "{\"a\":1}", null);
        WebFetchService.Result htmlOk = new WebFetchService.Result("u", false, 200, "text/html; charset=utf-8", 1, "b", "b", null);
        WebFetchService.Result cssOk = new WebFetchService.Result("u", false, 200, "text/css", 1, "b", "b", null);
        WebFetchService.Result jsonErr = new WebFetchService.Result("u", true, 400, "text/plain", 5, "400 x", "400 x", null);
        check("93. isHtml / isTextual / suggestFileName",
            WebFetchService.isHtml("text/html; charset=utf-8") && !WebFetchService.isHtml("application/json")
                && !WebFetchService.isHtml(null)
                && WebFetchService.isTextual("application/json") && WebFetchService.isTextual("")
                && !WebFetchService.isTextual("image/png")
                && "response.json".equals(WebFetchService.suggestFileName(jsonOk))
                && "source.html".equals(WebFetchService.suggestFileName(htmlOk))
                && "source.txt".equals(WebFetchService.suggestFileName(cssOk))
                && "source.txt".equals(WebFetchService.suggestFileName(jsonErr)),
            "isHtml/isTextual/suggestFileName tra ket qua sai");

        check("94. charsetOf uu tien header, roi <meta charset>, mac dinh UTF-8",
            WebFetchService.charsetOf("text/html; charset=ISO-8859-1", null).equals(java.nio.charset.StandardCharsets.ISO_8859_1)
                && WebFetchService.charsetOf("text/html", "<meta charset=\"windows-1252\">".getBytes(utf8))
                    .equals(java.nio.charset.Charset.forName("windows-1252"))
                && WebFetchService.charsetOf(null, null).equals(utf8)
                && WebFetchService.charsetOf("text/html; charset=bogus-xyz", null).equals(utf8),
            "charsetOf tra ket qua sai");
    }

```

- [ ] **Step 2: Chay bien dich de xac nhan FAIL**

Run: `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
Expected: FAIL, loi `cannot find symbol` / `class WebFetchService` trong `CrossTestClient.java`.

- [ ] **Step 3: Viet `src/client/WebFetchService.java`**

```java
package client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logic goi WebFetch cua Java Server: GET <server>/fetch?url=<target>[&format=json].
 * Khong dung Swing de CrossTestClient test duoc ma khong can man hinh.
 * - Che do raw : status / Content-Type lay tu HTTP response cua server, body la noi dung trang dich.
 * - Che do json: server tra {targetUrl,status,contentType,contentLength,body}; lop nay tach cac truong do.
 * Moi loi mang duoc tra ve trong Result.error (khong nem exception) de giao dien chi viec hien thi.
 */
public final class WebFetchService {
    public static final String DEFAULT_SERVER = "http://localhost:8080";

    // Timeout request lon hon timeout 10s cua WebFetchHandler de nhan duoc 502 cua server thay vi tu ngat truoc
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    private static final Pattern CHARSET_IN_HEADER = Pattern.compile("(?i)charset\\s*=\\s*\"?([^\\s;\"]+)");
    private static final Pattern CHARSET_IN_META = Pattern.compile("(?i)<meta\\s+[^>]*charset=[\"']?([a-zA-Z0-9_-]+)");

    /** Ket qua mot lan fetch. status = -1 va error != null khi khong nhan duoc phan hoi tu server. */
    public record Result(String requestUrl, boolean jsonMode, int status, String contentType,
                         long contentLength, String body, String rawResponse, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private final String serverBase;

    public WebFetchService() {
        this(DEFAULT_SERVER);
    }

    public WebFetchService(String serverBase) {
        String s = serverBase == null ? "" : serverBase.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        this.serverBase = s.isEmpty() ? DEFAULT_SERVER : s;
    }

    public String getServerBase() {
        return serverBase;
    }

    public String buildRequestUrl(String targetUrl, boolean json) {
        return serverBase + "/fetch?" + (json ? "format=json&" : "")
            + "url=" + URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
    }

    public Result fetch(String targetUrl, boolean json) {
        String target = targetUrl == null ? "" : targetUrl.trim();
        if (target.isEmpty()) {
            return error(null, json, "URL dang de trong. Hay nhap URL http:// hoac https://");
        }
        String requestUrl = buildRequestUrl(target, json);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(requestUrl)).timeout(REQUEST_TIMEOUT).GET().build();
        } catch (IllegalArgumentException e) {
            return error(requestUrl, json, "Dia chi WebFetch server khong hop le: " + serverBase);
        }

        HttpResponse<byte[]> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            return error(requestUrl, json, "Het thoi gian cho (" + REQUEST_TIMEOUT.toSeconds() + "s) khi goi " + serverBase);
        } catch (ConnectException e) {
            return error(requestUrl, json, "Khong ket noi duoc toi WebFetch server " + serverBase + " - hay chay run_server.bat");
        } catch (IOException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return error(requestUrl, json, "Loi mang khi goi " + serverBase + ": " + detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(requestUrl, json, "Yeu cau bi huy giua chung");
        }

        int httpStatus = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        byte[] bytes = response.body() == null ? new byte[0] : response.body();

        // Json mode: chi parse khi server that su tra JSON (loi 400 kieu text/plain duoc hien nhu raw)
        if (json && contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            String rawJson = new String(bytes, charsetOf(contentType, bytes));
            try {
                Map<String, String> m = parseFlatJson(rawJson);
                int status = Integer.parseInt(m.get("status"));
                long length = Long.parseLong(m.get("contentLength"));
                return new Result(requestUrl, true, status, m.getOrDefault("contentType", ""), length,
                    m.getOrDefault("body", ""), rawJson, null);
            } catch (RuntimeException e) {
                return error(requestUrl, true, "Phan hoi JSON cua server khong hop le: " + e.getMessage());
            }
        }

        String body;
        if (isTextual(contentType)) {
            body = new String(bytes, charsetOf(contentType, bytes));
        } else {
            body = "[Noi dung nhi phan: " + contentType + ", " + bytes.length + " bytes - khong hien thi duoc dang text]";
        }
        return new Result(requestUrl, json, httpStatus, contentType, bytes.length, body, body, null);
    }

    private static Result error(String requestUrl, boolean json, String message) {
        return new Result(requestUrl, json, -1, "", 0, "", "", message);
    }

    public static boolean isHtml(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("html");
    }

    public static boolean isTextual(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")
            || ct.contains("javascript") || ct.contains("html");
    }

    public static String suggestFileName(Result r) {
        if (r.jsonMode() && r.rawResponse().startsWith("{")) {
            return "response.json";
        }
        return isHtml(r.contentType()) ? "source.html" : "source.txt";
    }

    /** Charset: header Content-Type -> the meta charset (chi voi HTML) -> UTF-8. */
    static Charset charsetOf(String contentType, byte[] bytes) {
        if (contentType != null) {
            Matcher m = CHARSET_IN_HEADER.matcher(contentType);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException ignored) {
                    // charset la hoac khong ho tro -> thu cach tiep theo
                }
            }
        }
        if (bytes != null && bytes.length > 0 && isHtml(contentType)) {
            String head = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1);
            Matcher m = CHARSET_IN_META.matcher(head);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException ignored) {
                    // charset la hoac khong ho tro -> mac dinh UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Parse JSON phang {"key":"chuoi" | so, ...} - dung dinh dang cua WebFetchHandler.buildJsonMetadata.
     * Gia tri chuoi duoc giai ma escape; gia tri so tra ve dang chuoi. Nem IllegalArgumentException neu sai cu phap.
     */
    static Map<String, String> parseFlatJson(String json) {
        return new FlatJsonReader(json).read();
    }

    private static final class FlatJsonReader {
        private final String s;
        private int i = 0;

        FlatJsonReader(String s) {
            this.s = s == null ? "" : s;
        }

        Map<String, String> read() {
            Map<String, String> out = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                skipWs();
                String value;
                if (peek() == '"') {
                    value = readString();
                } else {
                    int start = i;
                    while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}') {
                        i++;
                    }
                    value = s.substring(start, i).trim();
                }
                out.put(key, value);
                skipWs();
                if (i >= s.length()) {
                    throw new IllegalArgumentException("JSON bi cat cut");
                }
                char c = s.charAt(i++);
                if (c == '}') {
                    return out;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Mong doi ',' hoac '}' tai vi tri " + (i - 1));
                }
            }
        }

        private char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("Mong doi '" + c + "' tai vi tri " + i);
            }
            i++;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    break;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw new IllegalArgumentException("Chuoi unicode escape bi cat cut");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Escape khong hop le: \\" + e);
                }
            }
            throw new IllegalArgumentException("Chuoi JSON khong dong");
        }
    }
}
```

- [ ] **Step 4: Bien dich va chay toan bo test**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient
echo "exit=$?"
```
Expected: bien dich khong loi; cac dong `[PASS] 83.` ... `[PASS] 94.`; dong cuoi `==== KET QUA: 97 PASS, 0 FAIL ====` va `exit=0`.
Neu mot test fail: doc dong `[FAIL]` (in kem noi dung `Result`), sua `WebFetchService` (khong sua test cho khop code sai), chay lai.

- [ ] **Step 5: Commit**

```bash
git add src/client/WebFetchService.java src/client/CrossTestClient.java
git commit -m "feat(client): add WebFetchService to call /fetch and parse raw/json results" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `WebClientApp` (Swing) + `run_webclient.bat` + smoke test

**Files:**
- Create: `src/client/WebClientApp.java`
- Create: `run_webclient.bat`
- Modify: `src/client/CrossTestClient.java` (comment dau lop; goi muc test 10; them 2 ham `testWebClientApp`, `fetchViaApp`)

**Interfaces:**
- Consumes (tu Task 1): `WebFetchService(String)`, `fetch(String, boolean)`, `Result` (cac accessor `ok()`, `error()`, `jsonMode()`, `rawResponse()`, `body()`, `status()`, `contentType()`, `contentLength()`), `WebFetchService.isHtml`, `WebFetchService.suggestFileName`, `WebFetchService.DEFAULT_SERVER`.
- Produces (Task 3 va test dung): `public class WebClientApp` voi
  - `public WebClientApp(String serverBase)` (tao cua so, chua hien), `public void open()` (hien cua so, dong cua so = thoat), `public static void main(String[] args)` (tham so tuy chon: dia chi server)
  - package-private: `void fetch(String target, Runnable onDone)` (goi tren EDT; `onDone` chay tren EDT sau khi giao dien cap nhat), `void setJsonMode(boolean)`, `String sourceText()`, `String statusText()`, `String noteText()`, `void dispose()`

- [ ] **Step 1: Viet smoke test (se fail vi chua co `WebClientApp`)**

Trong `src/client/CrossTestClient.java`:

(a) Trong comment dau lop, ngay sau dong ` * - Web Fetcher / Proxy: GET /fetch & GET /api/fetch (format=json, base tag injection, safety validation)` them dong:

```java
 * - Web Client: client.WebFetchService (logic) va client.WebClientApp (Swing, smoke test khong hien cua so)
```

(b) Thay doan (do Task 1 tao):

```java
        try {
            testWebFetchService(base);
        } catch (Exception e) {
            System.err.println("[FAIL] 83-94. WebFetchService -> Exception: " + e);
            fail++;
        }
    }
```

bang:

```java
        try {
            testWebFetchService(base);
        } catch (Exception e) {
            System.err.println("[FAIL] 83-94. WebFetchService -> Exception: " + e);
            fail++;
        }

        System.out.println("\n--- 10. WEB CLIENT (client.WebClientApp, Swing) ---");
        testWebClientApp(base);
    }
```

(c) Chen **ngay truoc** dong `    private static void testBaseTagInComment(String testName) {` hai ham:

```java
    private static void testWebClientApp(String base) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("[SKIP] 95-97. WebClientApp -> moi truong headless, khong co man hinh de tao cua so Swing");
            return;
        }
        try {
            String[] r = fetchViaApp(base, base + "/index.html", false);
            check("95. WebClientApp fetch raw -> Source co <base>, status 200, co chu thich <base>",
                r[0].contains("<head><base href=\"" + base + "/index.html\">") && r[1].contains("Status: 200")
                    && r[1].contains("Mode: raw") && r[2].contains("<base"),
                r[1] + " | " + r[2]);

            String[] j = fetchViaApp(base, base + "/index.html", true);
            check("96. WebClientApp fetch json -> Source la JSON metadata, status 200, Mode: json",
                j[0].startsWith("{\"targetUrl\":") && j[1].contains("Status: 200") && j[1].contains("Mode: json"),
                j[1]);

            String[] d = fetchViaApp("http://localhost:59998", base + "/index.html", false);
            check("97. WebClientApp khi WebFetch server tat -> bao loi tren status bar, khong treo",
                d[1].startsWith("Loi:") && d[1].contains("Khong ket noi"), d[1]);
        } catch (Exception e) {
            if (e instanceof java.awt.HeadlessException || e.getCause() instanceof java.awt.HeadlessException) {
                System.out.println("[SKIP] 95-97. WebClientApp -> HeadlessException, khong co man hinh");
            } else {
                System.err.println("[FAIL] 95-97. WebClientApp -> Exception: " + e);
                fail++;
            }
        }
    }

    /** Tao WebClientApp (khong hien), fetch 1 URL, doi giao dien cap nhat; tra ve {source, status, note}. */
    private static String[] fetchViaApp(String server, String url, boolean json) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String[] out = new String[3];
        WebClientApp[] app = new WebClientApp[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            app[0] = new WebClientApp(server);
            app[0].setJsonMode(json);
            app[0].fetch(url, () -> {
                out[0] = app[0].sourceText();
                out[1] = app[0].statusText();
                out[2] = app[0].noteText();
                done.countDown();
            });
        });
        boolean finished = done.await(20, java.util.concurrent.TimeUnit.SECONDS);
        javax.swing.SwingUtilities.invokeAndWait(app[0]::dispose);
        if (!finished) {
            throw new IllegalStateException("Qua 20s ma WebClientApp chua tra ket qua cho " + url);
        }
        return out;
    }

```

- [ ] **Step 2: Chay bien dich de xac nhan FAIL**

Run: `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
Expected: FAIL, `cannot find symbol ... class WebClientApp`.

- [ ] **Step 3: Viet `src/client/WebClientApp.java`**

```java
package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * Web Client (Swing): nhap URL -> goi WebFetch cua Java Server (GET <server>/fetch?url=...) -> hien source.
 * Logic mang nam o WebFetchService; lop nay chi lo giao dien. Moi lan fetch chay trong SwingWorker.
 * Chay: java -cp out client.WebClientApp [dia_chi_server]   (hoac run_webclient.bat)
 */
public class WebClientApp {
    private static final int MAX_HISTORY = 20;
    private static final Color OK_COLOR = new Color(0x1B, 0x6E, 0x2E);
    private static final Color ERROR_COLOR = new Color(0xB0, 0x1E, 0x1E);
    private static final String BASE_NOTE =
        "Luu y: server /fetch tu chen the <base href=\"...\"> vao <head> cua trang HTML, nen source nay chi khac ban goc dung 1 the do.";

    private final JFrame frame = new JFrame("Web Client - xem source qua WebFetch");
    private final JTextField serverField = new JTextField(22);
    private final JComboBox<String> urlCombo = new JComboBox<>();
    private final JCheckBox jsonCheck = new JCheckBox("format=json");
    private final JButton fetchButton = new JButton("Fetch");
    private final JButton saveButton = new JButton("Save...");
    private final JTextArea sourceArea = new JTextArea();
    private final JLabel noteLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("San sang. Nhap URL roi bam Fetch.");
    private final JTabbedPane tabs = new JTabbedPane();

    private String shownText = "";
    private String suggestedName = "source.txt";

    public WebClientApp(String serverBase) {
        serverField.setText(serverBase);
        urlCombo.setEditable(true);
        urlCombo.setSelectedItem(WebFetchService.DEFAULT_SERVER + "/index.html");
        jsonCheck.setToolTipText("Goi /fetch?format=json: tab Source hien metadata (status, contentType, contentLength, body)");

        sourceArea.setEditable(false);
        sourceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sourceArea.setTabSize(4);
        noteLabel.setForeground(new Color(0x8A, 0x5A, 0x00));
        noteLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel serverRow = new JPanel(new BorderLayout(6, 0));
        serverRow.add(new JLabel("Server WebFetch:"), BorderLayout.WEST);
        serverRow.add(serverField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(jsonCheck);
        buttons.add(fetchButton);
        buttons.add(saveButton);
        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        urlRow.add(new JLabel("URL can xem:"), BorderLayout.WEST);
        urlRow.add(urlCombo, BorderLayout.CENTER);
        urlRow.add(buttons, BorderLayout.EAST);

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(serverRow);
        top.add(urlRow);

        JPanel sourcePanel = new JPanel(new BorderLayout());
        sourcePanel.add(new JScrollPane(sourceArea), BorderLayout.CENTER);
        sourcePanel.add(noteLabel, BorderLayout.SOUTH);
        tabs.addTab("Source", sourcePanel);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(top, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.setSize(980, 660);
        frame.setLocationRelativeTo(null);

        fetchButton.addActionListener(e -> fetch(currentUrl(), null));
        saveButton.addActionListener(e -> save());
        urlCombo.addActionListener(e -> {
            if ("comboBoxEdited".equals(e.getActionCommand())) {
                fetch(currentUrl(), null);
            }
        });
    }

    public void open() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    void dispose() {
        frame.dispose();
    }

    void setJsonMode(boolean json) {
        jsonCheck.setSelected(json);
    }

    String sourceText() {
        return sourceArea.getText();
    }

    String statusText() {
        return statusLabel.getText();
    }

    String noteText() {
        return noteLabel.getText();
    }

    private String currentUrl() {
        Object item = urlCombo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    /** Goi tren EDT. onDone (co the null) chay tren EDT sau khi giao dien da cap nhat. */
    void fetch(String target, Runnable onDone) {
        if (!fetchButton.isEnabled()) {
            return;
        }
        fetchButton.setEnabled(false);
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Dang tai " + target + " ...");
        final boolean json = jsonCheck.isSelected();
        final String server = serverField.getText();
        new SwingWorker<WebFetchService.Result, Void>() {
            @Override
            protected WebFetchService.Result doInBackground() {
                return new WebFetchService(server).fetch(target, json);
            }

            @Override
            protected void done() {
                try {
                    display(get(), target);
                } catch (Exception ex) {
                    showFailure("Loi noi bo: " + ex);
                } finally {
                    fetchButton.setEnabled(true);
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            }
        }.execute();
    }

    private void display(WebFetchService.Result r, String target) {
        if (!r.ok()) {
            showFailure(r.error());
            return;
        }
        shownText = r.jsonMode() ? r.rawResponse() : r.body();
        suggestedName = WebFetchService.suggestFileName(r);
        sourceArea.setLineWrap(r.jsonMode());
        sourceArea.setText(shownText.replace("\r\n", "\n"));
        sourceArea.setCaretPosition(0);
        noteLabel.setText(WebFetchService.isHtml(r.contentType()) ? BASE_NOTE : " ");
        String ct = r.contentType().isBlank() ? "(khong co)" : r.contentType();
        statusLabel.setForeground(r.status() >= 400 ? ERROR_COLOR : OK_COLOR);
        statusLabel.setText("Status: " + r.status() + "  |  Content-Type: " + ct
            + "  |  Length: " + r.contentLength() + " bytes  |  Mode: " + (r.jsonMode() ? "json" : "raw"));
        remember(target);
    }

    private void showFailure(String message) {
        shownText = "";
        suggestedName = "source.txt";
        sourceArea.setLineWrap(true);
        sourceArea.setText(message);
        sourceArea.setCaretPosition(0);
        noteLabel.setText(" ");
        statusLabel.setForeground(ERROR_COLOR);
        statusLabel.setText("Loi: " + message);
    }

    /** Lich su: moi nhat len dau, khong trung, toi da MAX_HISTORY muc. */
    private void remember(String url) {
        for (int i = urlCombo.getItemCount() - 1; i >= 0; i--) {
            if (url.equals(urlCombo.getItemAt(i))) {
                urlCombo.removeItemAt(i);
            }
        }
        urlCombo.insertItemAt(url, 0);
        while (urlCombo.getItemCount() > MAX_HISTORY) {
            urlCombo.removeItemAt(urlCombo.getItemCount() - 1);
        }
        urlCombo.setSelectedIndex(0);
    }

    private void save() {
        if (shownText.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Chua co noi dung de luu. Hay Fetch truoc.", "Save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(frame, file.getName() + " da ton tai. Ghi de?", "Save",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.writeString(file.toPath(), shownText, StandardCharsets.UTF_8);
            statusLabel.setForeground(OK_COLOR);
            statusLabel.setText("Da luu " + shownText.length() + " ky tu vao " + file.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Khong luu duoc file: " + ex, "Save", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        String server = args.length > 0 ? args[0] : WebFetchService.DEFAULT_SERVER;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // dung giao dien Swing mac dinh
            }
            new WebClientApp(server).open();
        });
    }
}
```

- [ ] **Step 4: Viet `run_webclient.bat`** (cung thu muc voi cac file `.bat` khac)

```bat
@echo off
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
echo Dang mo Web Client (Swing). Nho bat Java Server truoc: run_server.bat
java -cp out client.WebClientApp %*
```

- [ ] **Step 5: Bien dich va chay toan bo test**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient
echo "exit=$?"
```
Expected: bien dich khong loi; `[PASS] 95.`, `[PASS] 96.`, `[PASS] 97.`; dong cuoi `==== KET QUA: 100 PASS, 0 FAIL ====`, `exit=0`.
Neu in `[SKIP] 95-97 ... headless` thi ket qua la 97 PASS - hay ghi ro trong report va chay lai tren may co man hinh (khong phai loi code).

- [ ] **Step 6: Commit**

```bash
git add src/client/WebClientApp.java src/client/CrossTestClient.java run_webclient.bat
git commit -m "feat(client): add Swing WebClientApp showing page source via WebFetch" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Tab Rendered + README

**Files:**
- Modify: `src/client/WebClientApp.java`
- Modify: `src/client/CrossTestClient.java`
- Modify: `README.md`

**Interfaces:**
- Consumes (tu Task 2): `WebClientApp` nhu mo ta o Task 2; `WebFetchService.isHtml`; ham test `fetchViaApp` trong `CrossTestClient`.
- Produces: tab **Rendered** (`JEditorPane`); package-private `String renderedPlainText()`; `static HTMLDocument buildRenderedDocument(String html)`.

- [ ] **Step 1: Viet test cho tab Rendered (se fail)**

Trong `src/client/CrossTestClient.java`:

(a) Trong `fetchViaApp`, thay `String[] out = new String[3];` bang `String[] out = new String[4];` va them dong sau dong `out[2] = app[0].noteText();`:

```java
                out[3] = app[0].renderedPlainText();
```
Cap nhat comment javadoc cua ham: `tra ve {source, status, note, rendered}`.

(b) Trong `testWebClientApp`, ngay sau lenh `check("97. ...` (ket thuc bang dong `d[1].startsWith("Loi:") && d[1].contains("Khong ket noi"), d[1]);`) them:

```java

            String[] h = fetchViaApp(base, base + "/index.html", false);
            check("98. WebClientApp tab Rendered hien HTML da render (<meta charset> va <link css> khong lam hong)",
                h[3].contains("Welcome to My Website"), h[3]);

            String[] c = fetchViaApp(base, base + "/style.css", false);
            check("99. WebClientApp tab Rendered voi noi dung khong phai HTML -> thong bao, khong loi",
                c[3].contains("Khong phai HTML") && c[1].contains("Status: 200"), c[3]);
```
Cap nhat 2 nhan `[SKIP] 95-97` va `[FAIL] 95-97` trong ham thanh `95-99`.

- [ ] **Step 2: Chay bien dich de xac nhan FAIL**

Run: `javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java`
Expected: FAIL, `cannot find symbol ... method renderedPlainText()`.

- [ ] **Step 3: Sua `src/client/WebClientApp.java`** (cac thay the sau deu la thay doan van ban chinh xac, moi doan xuat hien dung 1 lan)

(a) Import. Sau `import java.io.File;` them `import java.io.StringReader;`. Sau `import javax.swing.JComboBox;` them `import javax.swing.JEditorPane;`. Sau `import javax.swing.UIManager;` them:

```java
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
```

(b) Sau khai bao hang `BASE_NOTE` (ket thuc bang dong `... chi khac ban goc dung 1 the do.";`) them:

```java

    static {
        // Gioi han thoi gian URLConnection cu (Swing tai CSS/anh cua trang dich) de cua so khong bi treo
        System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
        System.setProperty("sun.net.client.defaultReadTimeout", "8000");
    }
```

(c) Sau dong `private final JTextArea sourceArea = new JTextArea();` them:

```java
    private final JEditorPane renderedPane = new JEditorPane();
```

(d) Sau dong `tabs.addTab("Source", sourcePanel);` them:

```java
        renderedPane.setContentType("text/html");
        renderedPane.setEditable(false);
        tabs.addTab("Rendered", new JScrollPane(renderedPane));
```

(e) Sau phuong thuc `noteText()` them:

```java

    String renderedPlainText() {
        try {
            Document d = renderedPane.getDocument();
            return d.getText(0, d.getLength());
        } catch (Exception e) {
            return "";
        }
    }
```

(f) Thay khoi worker trong `fetch`:

```java
        new SwingWorker<WebFetchService.Result, Void>() {
            @Override
            protected WebFetchService.Result doInBackground() {
                return new WebFetchService(server).fetch(target, json);
            }

            @Override
            protected void done() {
                try {
                    display(get(), target);
                } catch (Exception ex) {
```

bang:

```java
        new SwingWorker<Outcome, Void>() {
            @Override
            protected Outcome doInBackground() {
                WebFetchService.Result r = new WebFetchService(server).fetch(target, json);
                // Parse HTML (ke ca tai CSS ngoai) o thread nen de EDT khong bi chan
                Document doc = r.ok() && WebFetchService.isHtml(r.contentType()) ? buildRenderedDocument(r.body()) : null;
                return new Outcome(r, doc);
            }

            @Override
            protected void done() {
                try {
                    Outcome o = get();
                    display(o.result(), target);
                    if (o.result().ok()) {
                        showRendered(o.rendered());
                    }
                } catch (Exception ex) {
```

(g) Chen **ngay truoc** dong `    private void display(WebFetchService.Result r, String target) {`:

```java
    private record Outcome(WebFetchService.Result result, Document rendered) { }

    static HTMLDocument buildRenderedDocument(String html) {
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        // Bat buoc: neu khong, <meta charset> trong trang lam parser nem ChangedCharSetException va cat noi dung
        doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
        try {
            kit.read(new StringReader(html), doc, 0);
        } catch (Exception e) {
            // Giu phan da parse duoc: loi tai CSS ngoai / HTML hong khong duoc lam hong ca lan fetch
        }
        return doc;
    }

    private void showRendered(Document doc) {
        if (doc != null) {
            renderedPane.setDocument(doc);
            renderedPane.setCaretPosition(0);
        } else {
            renderedPane.setText("<html><body><p><i>Khong phai HTML (hoac chua co du lieu) nen khong render duoc.</i></p></body></html>");
        }
    }

```

(h) Trong `showFailure`, sau dong `statusLabel.setText("Loi: " + message);` them dong:

```java
        showRendered(null);
```

- [ ] **Step 4: Cap nhat `README.md`**

Thay dong:
`│   ├── client/       # HttpClientGetDemo, HttpClientPostDemo, HttpClientErrorDemo, CrossTestClient`
bang:
`│   ├── client/       # HttpClientGetDemo, HttpClientPostDemo, HttpClientErrorDemo, CrossTestClient, WebFetchService, WebClientApp (Swing)`

Them vao **cuoi file** (sau khoi code cua muc 6):

````markdown

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
````

- [ ] **Step 5: Bien dich va chay toan bo test**

Run:
```
javac -encoding UTF-8 -d out src/model/*.java src/service/*.java src/server/*.java src/mock/*.java src/client/*.java
java -cp out client.CrossTestClient
echo "exit=$?"
```
Expected: bien dich khong loi; `[PASS] 98.` va `[PASS] 99.`; dong cuoi `==== KET QUA: 102 PASS, 0 FAIL ====`, `exit=0`.
Neu test 98 fail va `h[3]` rong/cut: kiem tra `IgnoreCharsetDirective` va viec parse chay trong `doInBackground`.

- [ ] **Step 6: Commit**

```bash
git add src/client/WebClientApp.java src/client/CrossTestClient.java README.md
git commit -m "feat(client): add Rendered tab to WebClientApp and document the Web Client" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
