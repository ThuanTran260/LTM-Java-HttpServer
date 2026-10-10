package client;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import server.RouterHandler;
import server.SimpleHttpServer;
import server.SiteManager;
import server.SitesApiHandler;
import mock.MockIisServer;

/**
 * Integration Test Runner tu dong xac thuc toan bo ma tran request:
 * - GET /, GET /api/time, POST /api/message (201, 415, 400)
 * - CRUD /api/students (GET, POST, PUT, DELETE, query filtering ?id=, ?name=, ?age=)
 * - Static files (HTML, CSS, TXT, PNG) va chan Directory Traversal
 * - Route khong ton tai (404 text/plain)
 * - Proxy /iis/ va kiem tra IIS / Mock IIS
 * - Demo Clients (HttpClientGetDemo, HttpClientPostDemo, HttpClientErrorDemo)
 * - Web Fetcher / Proxy: GET /fetch & GET /api/fetch (format=json, base tag injection, safety validation)
 * - Web Client: client.WebFetchService (logic) va client.WebClientApp (Swing, smoke test khong hien cua so)
 *
 * Co kha nang tu dong khoi dong / goi den Server neu chua chay.
 */
public class CrossTestClient {
    private static int pass = 0;
    private static int fail = 0;
    private static HttpServer autoJavaServer = null;
    private static HttpServer autoMockServer = null;

    public static void main(String[] args) {
        try {
            ensureServersRunning();
            runTests();
        } catch (Throwable t) {
            System.err.println("Fatal exception in test runner: " + t.getMessage());
            t.printStackTrace();
            fail++;
        } finally {
            cleanupServers();
        }

        System.out.println("==================================================");
        System.out.println("==== KET QUA: " + pass + " PASS, " + fail + " FAIL ====");
        System.out.println("==================================================");
        if (fail > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void ensureServersRunning() throws Exception {
        // 1. Kiem tra Mock IIS (:8081)
        boolean hasMock = probe("http://localhost:8081/");
        if (!hasMock) {
            System.out.println("[TEST RUNNER] Mock IIS chua chay, tu dong khoi dong MockIisServer tren cong 8081...");
            autoMockServer = MockIisServer.startServer(8081);
            Thread.sleep(200);
        }

        // 2. Kiem tra Java Server (:8080)
        boolean hasJava = probe("http://localhost:8080/");
        if (!hasJava) {
            System.out.println("[TEST RUNNER] Java Server chua chay, tu dong khoi dong SimpleHttpServer tren cong 8080...");
            autoJavaServer = SimpleHttpServer.startServer(8080);
            Thread.sleep(200);
        }

        // Reset danh sach sinh vien ve trang thai chuan truoc khi test
        RouterHandler.getStudentService().reset();
    }

    private static void cleanupServers() {
        if (autoJavaServer != null) {
            System.out.println("[TEST RUNNER] Dung SimpleHttpServer tu khoi dong...");
            try { autoJavaServer.stop(0); } catch (Exception ignored) {}
        }
        if (autoMockServer != null) {
            System.out.println("[TEST RUNNER] Dung MockIisServer tu khoi dong...");
            try { autoMockServer.stop(0); } catch (Exception ignored) {}
        }
    }

    private static boolean probe(String url) {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(600)).build();
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(600)).GET().build();
            c.send(r, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runTests() {
        String base = "http://localhost:8080";
        String iisTarget = probe("http://localhost:80/") ? "http://localhost:80" : "http://localhost:8081";

        System.out.println("\n--- 1. BASIC ROUTES & SLIDE EXAMPLES ---");
        testRequest("1. GET / (Trang chu HTML)", "GET", base + "/", null, null, 200, "text/html", null);
        testRequest("2. GET /api/time (JSON thoi gian)", "GET", base + "/api/time", null, null, 200, "application/json", "\"time\":");
        testRequest("3. POST /api/message (JSON hop le)", "POST", base + "/api/message", "application/json", "{\"text\":\"Xin chao\"}", 201, "application/json", "\"message\":\"Da nhan du lieu\"");
        testRequest("4. POST /api/message (Content-Type sai -> 415)", "POST", base + "/api/message", "text/plain", "Xin chao", 415, "text/plain", "415");
        testRequest("5. POST /api/message (Empty body -> 400)", "POST", base + "/api/message", "application/json", "   ", 400, "text/plain", "400");

        System.out.println("\n--- 2. CRUD SINH VIEN (/api/students) ---");
        testRequest("6. GET /api/students (Danh sach SV ban dau)", "GET", base + "/api/students", null, null, 200, "application/json", "\"name\":\"An\"");
        testRequest("7. GET /api/students?id=1 (Lay SV theo query ?id=1)", "GET", base + "/api/students?id=1", null, null, 200, "application/json", "\"id\":1");
        testRequest("8. GET /api/students?id=999 (SV khong ton tai -> 404)", "GET", base + "/api/students?id=999", null, null, 404, "text/plain", "404");
        testRequest("9. GET /api/students/1 (Lay SV theo path /1)", "GET", base + "/api/students/1", null, null, 200, "application/json", "\"id\":1");
        testRequest("10. GET /api/students?name=An (Loc theo name)", "GET", base + "/api/students?name=An", null, null, 200, "application/json", "\"name\":\"An\"");
        testRequest("11. GET /api/students?age=21 (Loc theo age)", "GET", base + "/api/students?age=21", null, null, 200, "application/json", "\"name\":\"Binh\"");
        testRequest("12. POST /api/students (Them sinh vien moi -> 201)", "POST", base + "/api/students", "application/json", "{\"name\":\"Cuong\",\"age\":22}", 201, "application/json", "\"name\":\"Cuong\"");
        testRequest("13. POST /api/students (Sai Content-Type -> 415)", "POST", base + "/api/students", "text/plain", "name=Cuong", 415, "text/plain", "415");
        testRequest("14. PUT /api/students/1 (Cap nhat SV qua path -> 200)", "PUT", base + "/api/students/1", "application/json", "{\"name\":\"An Updated\",\"age\":25}", 200, "application/json", "\"name\":\"An Updated\"");
        testRequest("15. PUT /api/students?id=1 (Cap nhat SV qua query ?id=1 -> 200)", "PUT", base + "/api/students?id=1", "application/json", "{\"name\":\"An Final\",\"age\":26}", 200, "application/json", "\"name\":\"An Final\"");
        testRequest("16. PUT /api/students/999 (Cap nhat SV khong ton tai -> 404)", "PUT", base + "/api/students/999", "application/json", "{\"name\":\"X\",\"age\":99}", 404, "text/plain", "404");
        testRequest("17. DELETE /api/students/1 (Xoa SV qua path -> 200)", "DELETE", base + "/api/students/1", null, null, 200, "application/json", "\"message\":\"Deleted\"");
        testRequest("18. DELETE /api/students/1 (Xoa lai SV da xoa -> 404)", "DELETE", base + "/api/students/1", null, null, 404, "text/plain", "404");

        System.out.println("\n--- 3. STATIC FILES & DIRECTORY TRAVERSAL ---");
        testRequest("19. GET /index.html (File HTML tinh)", "GET", base + "/index.html", null, null, 200, "text/html", "Welcome");
        testRequest("20. GET /style.css (File CSS)", "GET", base + "/style.css", null, null, 200, "text/css", "background");
        testRequest("21. GET /sample.txt (File van ban TXT)", "GET", base + "/sample.txt", null, null, 200, "text/plain", null);
        testRequest("22. GET /sample.png (File anh binary)", "GET", base + "/sample.png", null, null, 200, "image/png", null);
        testRequest("23. GET /../secret.txt (Chan Directory Traversal -> 404)", "GET", base + "/../secret.txt", null, null, 404, "text/plain", "404");
        testRequest("24. GET /%2e%2e/sample.txt (Chan Traversal URL-encoded -> 404)", "GET", base + "/%2e%2e/sample.txt", null, null, 404, "text/plain", "404");
        testRequest("25. GET /abc (Route khong hop le -> 404 text/plain)", "GET", base + "/abc", null, null, 404, "text/plain", "404 Not Found");

        System.out.println("\n--- 4. PROXY & IIS INTEGRATION ---");
        testRequest("26. GET /iis/ (Proxy sang IIS/Mock IIS -> 200 & Server Header)", "GET", base + "/iis/", null, null, 200, "text/html", null, "Microsoft-IIS");
        testRequest("27. GET / (Truc tiep IIS / Mock IIS " + iisTarget + ")", "GET", iisTarget + "/", null, null, 200, "text/html", null);
        try {
            HttpServer testMock = MockIisServer.startServer(8089);
            testRequest("27b. Mock IIS doc lap (:8089 Server Header)", "GET", "http://localhost:8089/", null, null, 200, "text/html", "IIS Windows Server", "Microsoft-IIS/10.0");
            testMock.stop(0);
        } catch (Exception e) {
            System.err.println("[FAIL] 27b. Mock IIS doc lap: " + e.getMessage());
            fail++;
        }

        System.out.println("\n--- 5. CLIENT DEMO CODE EXECUTION ---");
        testClientDemo("28. HttpClientGetDemo execution", "Status: 200", () -> HttpClientGetDemo.main(new String[]{base + "/api/time"}));
        testClientDemo("29. HttpClientPostDemo execution", "Status: 201", () -> HttpClientPostDemo.main(new String[]{base + "/api/message"}));
        testClientDemo("30. HttpClientErrorDemo execution (Connect error handling)", "Loi ket noi", () -> HttpClientErrorDemo.main(new String[]{"http://localhost:9099/api/time"}));
        testClientDemo("30b. HttpClientErrorDemo execution (2xx Success)", "Thanh cong", () -> HttpClientErrorDemo.main(new String[]{base + "/api/time"}));
        testClientDemo("30c. HttpClientErrorDemo execution (4xx HTTP Error)", "HTTP Error: 404", () -> HttpClientErrorDemo.main(new String[]{base + "/abc"}));

        System.out.println("\n--- 6. ADVERSARIAL EDGE CASES & SLIDE 10 COMPATIBILITY ---");
        testRequest("31. POST /api/students (Backslash escaping)", "POST", base + "/api/students", "application/json", "{\"name\":\"Nguyen\\\\\",\"age\":20}", 201, "application/json", "\"name\":\"Nguyen\\\\\"");
        testRequest("32. POST /api/students (Unicode escape \\uXXXX)", "POST", base + "/api/students", "application/json", "{\"name\":\"\\u0110\\u1ed7 \\u00c1nh\",\"age\":22}", 201, "application/json", "\"name\":\"Đỗ Ánh\"");
        testRequest("33. GET /api/students?name=Nguyen (Tim kiem SV ten Nguyen)", "GET", base + "/api/students?name=Nguyen", null, null, 200, "application/json", "\"name\":\"Nguyen");
        testRequest("34. GET /students (Slide 10 Route shorthand)", "GET", base + "/students", null, null, 200, "application/json", "\"name\":");
        testRequest("35. GET /students/2 (Slide 10 Route shorthand ?id / {id})", "GET", base + "/students/2", null, null, 200, "application/json", "\"id\":2");
        testRequest("36. POST /api/time (Method Not Allowed -> 405 Allow: GET)", "POST", base + "/api/time", null, null, 405, "text/plain", "405", "Allow", "GET");
        testRequest("37. POST /api/students/1 (Method Not Allowed on ID -> 405 Allow: GET, PUT, DELETE)", "POST", base + "/api/students/1", "application/json", "{}", 405, "text/plain", "405", "Allow", "GET");
        testRequest("38. POST /api/students (Empty name -> 400)", "POST", base + "/api/students", "application/json", "{\"name\":\"\",\"age\":20}", 400, "text/plain", "400");
        testRequest("39. POST /api/students (Negative age -> 400)", "POST", base + "/api/students", "application/json", "{\"name\":\"Test\",\"age\":-5}", 400, "text/plain", "400");
        testRequest("40. DELETE /api/students (Xoa SV qua JSON body -> 200)", "DELETE", base + "/api/students", "application/json", "{\"id\":2}", 200, "application/json", "\"message\":\"Deleted\"");
        testRequest("41. GET /api/students/3/ (Trailing slash on student ID -> 200)", "GET", base + "/api/students/3/", null, null, 200, "application/json", "\"id\":3");
        testRequest("42. GET /students/3/ (Slide 10 shorthand with trailing slash -> 200)", "GET", base + "/students/3/", null, null, 200, "application/json", "\"id\":3");
        testRequest("43. POST /api/students (Key string embedded inside earlier value)", "POST", base + "/api/students", "application/json", "{\"bio\":\"Likes \\\"name\\\": oops\", \"name\":\"Hoang\", \"age\":23}", 201, "application/json", "\"name\":\"Hoang\"");
        testRequest("44. GET /iis/?search=hello%20world (Proxy with percent-encoded query -> 200)", "GET", base + "/iis/?search=hello%20world", null, null, 200, "text/html", null);
        testRequest("45. GET /test%00.txt (Malformed static path -> 404 text/plain)", "GET", base + "/test%00.txt", null, null, 404, "text/plain", "404 Not Found");
        testRequest("46. GET /time (Shorthand alias -> 200)", "GET", base + "/time", null, null, 200, "application/json", "\"time\":");
        testRequest("47. POST /message (Shorthand alias -> 201)", "POST", base + "/message", "application/json", "{\"text\":\"Hello\"}", 201, "application/json", "\"message\":\"Da nhan du lieu\"");
        testRequest("48. POST /api/students (Non-string name type integer -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": 123, \"age\": 20}", 400, "text/plain", "400");
        testRequest("49. POST /api/students (Non-string name type boolean -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": true, \"age\": 20}", 400, "text/plain", "400");
        testRequest("50. POST /api/students (Invalid integer with suffix -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": \"David\", \"age\": \"20years\"}", 400, "text/plain", "400");
        testRequest("51. POST /api/students (Non-integer age float -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": \"David\", \"age\": 20.5}", 400, "text/plain", "400");
        testRequest("52. GET /api/students?age=abc (Invalid age query -> 400)", "GET", base + "/api/students?age=abc", null, null, 400, "text/plain", "400");
        testRequest("53. PUT /api/students/1 (Non-string name type -> 400)", "PUT", base + "/api/students/1", "application/json", "{\"name\": 999, \"age\": 20}", 400, "text/plain", "400");

        System.out.println("\n--- 7. CONCURRENCY & STREAMING STRESS ---");
        testConcurrency("54. Concurrency stress test (50 concurrent requests)", base + "/api/time", 50);
        testLargeStaticStreaming("55. Large static file streaming (>250KB without truncation)", base);
        testEmptyStaticFile("56. Empty static file (0 bytes without chunked transfer)", base);

        System.out.println("\n--- 8. WEB FETCHER / PROXY (/fetch & /api/fetch) ---");
        testRequest("57. GET /fetch?url=... (Raw HTML fetch tu Mock IIS)", "GET", base + "/fetch?url=" + iisTarget + "/", null, null, 200, "text/html", "IIS Windows");
        testRequest("58. GET /fetch?url=... (Tu dong chen the <base> vao Mock IIS)", "GET", base + "/fetch?url=" + iisTarget + "/", null, null, 200, "text/html", "<base href=\"" + iisTarget + "/\">");
        testRequest("59. GET /api/fetch?url=... (Route alias /api/fetch Raw HTML)", "GET", base + "/api/fetch?url=" + iisTarget + "/", null, null, 200, "text/html", "<base href=\"" + iisTarget + "/\">");
        testRequest("60. GET /fetch?url=...&format=json (JSON metadata status & body)", "GET", base + "/fetch?url=" + iisTarget + "/&format=json", null, null, 200, "application/json", "\"status\":200");
        testRequest("61. GET /api/fetch?url=...&format=json (Route alias /api/fetch format=json)", "GET", base + "/api/fetch?url=" + iisTarget + "/&format=json", null, null, 200, "application/json", "\"targetUrl\":\"" + iisTarget + "/\"");
        testRequest("62. GET /fetch?url=.../index.html (Chen <base> chinh xac vao trong <head>)", "GET", base + "/fetch?url=" + base + "/index.html", null, null, 200, "text/html", "<head><base href=\"" + base + "/index.html\">");
        testRequest("63. GET /fetch?url=.../api/time&format=json (Fetch noi bo API thoi gian qua format=json)", "GET", base + "/fetch?url=" + base + "/api/time&format=json", null, null, 200, "application/json", "time");
        testRequest("64. GET /fetch?url=.../style.css (Fetch asset tinh text/css giu nguyen raw)", "GET", base + "/fetch?url=" + base + "/style.css", null, null, 200, "text/css", "background");
        testRequest("65. GET /fetch?url=http%3A%2F%2F... (Fetch URL duoc percent-encode)", "GET", base + "/fetch?url=http%3A%2F%2Flocalhost%3A8081%2F", null, null, 200, "text/html", "<base href=\"http://localhost:8081/\">");
        testRequest("66. GET /fetch?url=file:///C:/Windows/win.ini (Chan scheme file:// -> 400)", "GET", base + "/fetch?url=file:///C:/Windows/win.ini", null, null, 400, "text/plain", "400 Bad Request");
        testRequest("67. GET /fetch?url=ftp://speedtest.tele2.net (Chan scheme ftp:// -> 400)", "GET", base + "/fetch?url=ftp://speedtest.tele2.net", null, null, 400, "text/plain", "400 Bad Request");
        testRequest("68. GET /fetch?url=javascript:alert(1) (Chan scheme javascript: -> 400)", "GET", base + "/fetch?url=javascript:alert(1)", null, null, 400, "text/plain", "400 Bad Request");
        testRequest("69. GET /fetch?url=data:text/plain,hello (Chan scheme data: -> 400)", "GET", base + "/fetch?url=data:text/plain,hello", null, null, 400, "text/plain", "400 Bad Request");
        testRequest("70. GET /fetch (Thieu url query param -> 400)", "GET", base + "/fetch", null, null, 400, "text/plain", "400 Bad Request");
        testRequest("71. GET /fetch?url= (Url rong -> 400)", "GET", base + "/fetch?url=", null, null, 400, "text/plain", "400 Bad Request");
        testRequest("72. POST /fetch?url=... (Method Not Allowed -> 405 Allow: GET)", "POST", base + "/fetch?url=" + iisTarget + "/", null, null, 405, "text/plain", "405", "Allow", "GET");
        testRequest("73. GET /index.html (Giao dien UI chua form Web Fetcher)", "GET", base + "/index.html", null, null, 200, "text/html", "action=\"/fetch\"");
        testRequest("74. GET /fetch?url=http://localhost:59999/ (Cong khong ton tai -> 502 Bad Gateway)", "GET", base + "/fetch?url=http://localhost:59999/", null, null, 502, "text/plain", "502 Bad Gateway");
        testRequest("75. GET /fetch?url=http://localhost:59999/&format=json (Cong khong ton tai format=json -> 502 JSON)", "GET", base + "/fetch?url=http://localhost:59999/&format=json", null, null, 502, "application/json", "502 Bad Gateway");
        testRequest("76. GET /fetch?url=...%3Fid%3D3&format=json (Fetch target URL co query param nested)", "GET", base + "/fetch?url=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fstudents%3Fid%3D3&format=json", null, null, 200, "application/json", "Cuong");
        testRequest("77. GET /fetch?url=.../index.html%23heading (Strip URL fragment for upstream request)", "GET", base + "/fetch?url=" + base + "/index.html%23heading", null, null, 200, "text/html", "<base href=\"" + base + "/index.html#heading\">");
        testRequest("78. GET /fetch?url=.../api/students?id=3&format=json (Unencoded target query with fetcher format=json)", "GET", base + "/fetch?url=" + base + "/api/students?id=3&format=json", null, null, 200, "application/json", "Cuong");
        testRequest("79. GET /fetch?format=json&url=... (Parameter order format=json before url)", "GET", base + "/fetch?format=json&url=" + base + "/api/students?id=3", null, null, 200, "application/json", "Cuong");
        testRequest("80. GET /fetch?url=...&FORMAT=JSON (Case-insensitive format parameter)", "GET", base + "/fetch?url=" + base + "/api/time&FORMAT=JSON", null, null, 200, "application/json", "\"status\":200");
        testRequest("81. GET /fetch?url=.../api/students?id=3&format=xml (Target URL format=xml query preserved)", "GET", base + "/fetch?url=" + base + "/api/students?id=3&format=xml", null, null, 200, "application/json", "Cuong");
        testBaseTagInComment("82. Base tag injection ignores <head> inside HTML comments");

        System.out.println("\n--- 9. WEB CLIENT (client.WebFetchService) ---");
        try {
            testWebFetchService(base);
        } catch (Exception e) {
            System.err.println("[FAIL] 83-94. WebFetchService -> Exception: " + e);
            fail++;
        }

        System.out.println("\n--- 10. WEB CLIENT (client.WebClientApp, Swing) ---");
        testWebClientApp(base);

        System.out.println("\n--- 11. MULTI-SITE (server.SiteManager, /api/sites, admin.html) ---");
        try {
            testMultiSite(base);
        } catch (Exception e) {
            System.err.println("[FAIL] 11. MULTI-SITE -> Exception: " + e);
            fail++;
        }
    }

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

        WebFetchService.Result pngJson = svc.fetch(base + "/sample.png", true);
        check("92b. fetch anh PNG (json) -> body la placeholder nhi phan, contentLength = so byte that, rawResponse la JSON",
            pngJson.ok() && pngJson.jsonMode() && pngJson.status() == 200 && pngJson.contentType().contains("image/png")
                && pngJson.body().contains("nhi phan") && pngJson.contentLength() == pngSize
                && pngJson.rawResponse().startsWith("{\"targetUrl\":"),
            pngJson.toString());

        WebFetchService.Result jsonOk =new WebFetchService.Result("u", true, 200, "text/html", 1, "b", "{\"a\":1}", null);
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

    private static void testWebClientApp(String base) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("[SKIP] 95-99. WebClientApp -> moi truong headless, khong co man hinh de tao cua so Swing");
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

            String[] h = fetchViaApp(base, base + "/index.html", false);
            check("98. WebClientApp tab Rendered hien HTML da render (<meta charset> va <link css> khong lam hong)",
                h[3].contains("Welcome to My Website"), h[3]);

            String[] c = fetchViaApp(base, base + "/style.css", false);
            check("99. WebClientApp tab Rendered voi noi dung khong phai HTML -> thong bao, khong loi",
                c[3].contains("Khong phai HTML") && c[1].contains("Status: 200"), c[3]);
        } catch (Exception e) {
            if (e instanceof java.awt.HeadlessException || e.getCause() instanceof java.awt.HeadlessException) {
                System.out.println("[SKIP] 95-99. WebClientApp -> HeadlessException, khong co man hinh");
            } else {
                System.err.println("[FAIL] 95-99. WebClientApp -> Exception: " + e);
                fail++;
            }
        }
    }

    /** Tao WebClientApp (khong hien), fetch 1 URL, doi giao dien cap nhat; tra ve {source, status, note, rendered}. */
    private static String[] fetchViaApp(String server, String url, boolean json) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String[] out = new String[4];
        WebClientApp[] app = new WebClientApp[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            app[0] = new WebClientApp(server);
            app[0].setJsonMode(json);
            app[0].fetch(url, () -> {
                out[0] = app[0].sourceText();
                out[1] = app[0].statusText();
                out[2] = app[0].noteText();
                out[3] = app[0].renderedPlainText();
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

    private static void testMultiSite(String base) throws Exception {
        testSiteManager();
        testFileTreeUnit();
        testSitesApi(base);
        testFileTreeApi(base);
        testAdminPage(base);
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

    private static void testAdminPage(String base) {
        testRequest("S38. GET /admin.html -> trang quan tri goi /api/sites", "GET", base + "/admin.html", null, null, 200, "text/html", "/api/sites");
        testRequest("S39. GET /index.html -> co link toi trang quan tri", "GET", base + "/index.html", null, null, 200, "text/html", "/admin.html");
    }

    private static void testFileTreeUnit() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("tree-test");
        java.nio.file.Path sitesDir = tmp.resolve("sites");
        java.nio.file.Path cfg = tmp.resolve("sites.json");
        SiteManager mgr = new SiteManager(sitesDir, cfg, 8080);
        try {
            mgr.add("alpha", freePort());
            java.nio.file.Path root = sitesDir.resolve("alpha");
            java.nio.file.Files.createDirectories(root.resolve("sub"));
            java.nio.file.Files.writeString(root.resolve("sub").resolve("a.html"), "<p>a</p>");
            java.nio.file.Files.writeString(root.resolve("my page.html"), "<p>b</p>");
            java.nio.file.Files.writeString(root.resolve("notes.md"), "x");
            java.nio.file.Files.writeString(root.resolve("skip.pdf"), "x");
            java.nio.file.Files.createDirectories(root.resolve("empty"));
            java.nio.file.Files.writeString(root.resolve("empty").resolve("readme.md"), "x");

            String json = mgr.filesJson("alpha");
            check("S40. files: thu muc con long nhau, duong dan dung '/' (sub/a.html)",
                json.contains("{\"name\":\"sub\",\"path\":\"sub\",\"dir\":true,\"children\":[{\"name\":\"a.html\",\"path\":\"sub/a.html\",\"dir\":false}]}"),
                json);
            check("S41. files: thu muc truoc file; ten co khoang trang giu nguyen (my page.html)",
                json.indexOf("\"name\":\"sub\"") < json.indexOf("\"name\":\"index.html\"")
                    && json.contains("{\"name\":\"my page.html\",\"path\":\"my page.html\",\"dir\":false}"),
                json);
            check("S42. files: chi file site phuc vu duoc; thu muc khong co file web bi an",
                !json.contains("notes.md") && !json.contains("skip.pdf") && !json.contains("\"empty\""),
                json);

            mgr.add("gamma", freePort());
            deleteTree(sitesDir.resolve("gamma"));
            check("S43. files: thu muc site bi xoa tay -> [] (khong loi)",
                mgr.filesJson("gamma").equals("[]"), mgr.filesJson("gamma"));

            mgr.stop("alpha");
            check("S44. files: site dang tat van liet ke duoc file",
                !mgr.get("alpha").running() && mgr.filesJson("alpha").contains("\"path\":\"sub/a.html\""),
                mgr.filesJson("alpha"));

            int missing = 0;
            try {
                mgr.filesJson("khong-co");
            } catch (SiteManager.SiteException e) {
                missing = e.status();
            }
            check("S45. files: site khong ton tai -> 404", missing == 404, "status=" + missing);
        } finally {
            mgr.stopAll();
            deleteTree(tmp);
        }
    }

    private static void testFileTreeApi(String base) throws Exception {
        String api = base + "/api/sites";
        String name = "zz-tree-test";
        String siteApi = api + "/" + name;
        java.nio.file.Path root = java.nio.file.Paths.get("sites", name);
        try {
            deleteTree(root);
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(api)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\",\"port\":" + freePort() + "}"))
                    .build(),
                HttpResponse.BodyHandlers.discarding());
            java.nio.file.Files.createDirectories(root.resolve("sub"));
            java.nio.file.Files.writeString(root.resolve("sub").resolve("a.html"), "<p>a</p>");
            java.nio.file.Files.writeString(root.resolve("notes.md"), "x");

            testRequest("S46. GET /api/sites/{name}/files -> 200 JSON, co sub/a.html", "GET", siteApi + "/files",
                null, null, 200, "application/json", "\"path\":\"sub/a.html\"");
            testRequest("S47. GET /api/sites/khong-co/files -> 404", "GET", api + "/khong-co/files",
                null, null, 404, "text/plain", "404");
            testRequest("S48. POST /api/sites/{name}/files -> 405 Allow: GET", "POST", siteApi + "/files",
                "application/json", "{}", 405, "text/plain", "405", "Allow", "GET");
        } finally {
            httpDeleteQuietly(siteApi);
            deleteTree(root);
        }
    }

    private static void testBaseTagInComment(String testName) {
        try {
            String html = "<!-- <head> inside comment --><html><head><title>Test</title></head><body><h1>Hello</h1></body></html>";
            String injected = server.WebFetchHandler.injectBaseTag(html, "<base href=\"http://test.com/\">");
            boolean ok = injected.contains("<head><base href=\"http://test.com/\">") && !injected.contains("<!-- <head><base");
            if (ok) {
                System.out.println("[PASS] " + testName + " -> Injected into real <head>");
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + " -> Output: " + injected);
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> Exception: " + e.getMessage());
            fail++;
        }
    }

    private static void testRequest(String testName, String method, String url, String contentType, String reqBody,
                                    int expectedStatus, String expectedContentTypeSub, String expectedBodySub) {
        testRequest(testName, method, url, contentType, reqBody, expectedStatus, expectedContentTypeSub, expectedBodySub, null, null);
    }

    private static void testRequest(String testName, String method, String url, String contentType, String reqBody,
                                    int expectedStatus, String expectedContentTypeSub, String expectedBodySub, String expectedServerHeader) {
        testRequest(testName, method, url, contentType, reqBody, expectedStatus, expectedContentTypeSub, expectedBodySub, "Server", expectedServerHeader);
    }

    private static void testRequest(String testName, String method, String url, String contentType, String reqBody,
                                    int expectedStatus, String expectedContentTypeSub, String expectedBodySub,
                                    String expectedHeaderName, String expectedHeaderSub) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3));

            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }

            if (method.equals("GET")) {
                builder.GET();
            } else if (method.equals("DELETE")) {
                if (reqBody != null && !reqBody.isEmpty()) {
                    builder.method("DELETE", HttpRequest.BodyPublishers.ofString(reqBody));
                } else {
                    builder.DELETE();
                }
            } else if (method.equals("POST")) {
                builder.POST(HttpRequest.BodyPublishers.ofString(reqBody == null ? "" : reqBody));
            } else if (method.equals("PUT")) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(reqBody == null ? "" : reqBody));
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String resCt = response.headers().firstValue("Content-Type").orElse("");
            String body = response.body() == null ? "" : response.body();
            String hVal = expectedHeaderName != null ? response.headers().firstValue(expectedHeaderName).orElse("") : "";

            boolean statusOk = (status == expectedStatus);
            boolean ctOk = (expectedContentTypeSub == null || resCt.toLowerCase().contains(expectedContentTypeSub.toLowerCase()));
            boolean bodyOk = (expectedBodySub == null || body.contains(expectedBodySub));
            boolean headerOk = (expectedHeaderName == null || hVal.contains(expectedHeaderSub));

            if (statusOk && ctOk && bodyOk && headerOk) {
                String extra = expectedHeaderName != null ? " [" + expectedHeaderName + ": " + hVal + "]" : "";
                System.out.println("[PASS] " + testName + " -> Status " + status + " (" + resCt + ")" + extra);
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + ":");
                if (!statusOk) System.err.println("       Status mong doi: " + expectedStatus + ", thuc te: " + status);
                if (!ctOk) System.err.println("       Content-Type mong doi chua: " + expectedContentTypeSub + ", thuc te: " + resCt);
                if (!bodyOk) System.err.println("       Body mong doi chua: '" + expectedBodySub + "', thuc te: '" + body + "'");
                if (!headerOk) System.err.println("       " + expectedHeaderName + " Header mong doi chua: '" + expectedHeaderSub + "', thuc te: '" + hVal + "'");
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> Exception: " + e.getMessage());
            fail++;
        }
    }

    private static void testClientDemo(String testName, String expectedOutputSub, ThrowingRunnable action) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            PrintStream ps = new PrintStream(buffer, true);
            System.setOut(ps);
            System.setErr(ps);
            action.run();
            String captured = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (expectedOutputSub != null && !captured.contains(expectedOutputSub)) {
                origErr.println("[FAIL] " + testName + ": Output khong chua '" + expectedOutputSub + "':\n" + captured.trim());
                fail++;
            } else {
                origOut.println("[PASS] " + testName + " -> OK");
                pass++;
            }
        } catch (Throwable t) {
            origErr.println("[FAIL] " + testName + " -> " + t.getMessage());
            fail++;
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    private static void testConcurrency(String testName, String url, int threadCount) {
        try {
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger(0);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
                        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() == 200) {
                            successes.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean finished = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            if (finished && successes.get() == threadCount) {
                System.out.println("[PASS] " + testName + " (" + successes.get() + "/" + threadCount + " concurrent requests OK)");
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + ": thanh cong " + successes.get() + "/" + threadCount);
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> Exception: " + e.getMessage());
            fail++;
        }
    }

    private static void testLargeStaticStreaming(String testName, String base) {
        java.nio.file.Path largePath = java.nio.file.Paths.get("wwwroot", "large_test.txt");
        try {
            byte[] chunk = "A".repeat(1024).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(largePath)) {
                for (int i = 0; i < 256; i++) {
                    os.write(chunk);
                }
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/large_test.txt")).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<byte[]> response = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            int len = response.body() != null ? response.body().length : 0;
            if (status == 200 && len == 256 * 1024) {
                System.out.println("[PASS] " + testName + " -> Status 200 (" + len + " bytes streamed without truncation)");
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + ": status=" + status + ", length=" + len + " (mong doi 262144 bytes)");
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> " + e.getMessage());
            fail++;
        } finally {
            try { java.nio.file.Files.deleteIfExists(largePath); } catch (Exception ignored) {}
        }
    }

    private static void testEmptyStaticFile(String testName, String base) {
        java.nio.file.Path emptyPath = java.nio.file.Paths.get("wwwroot", "empty_test.txt");
        try {
            java.nio.file.Files.write(emptyPath, new byte[0]);
            testRequest(testName, "GET", base + "/empty_test.txt", null, null, 200, "text/plain", null);
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> " + e.getMessage());
            fail++;
        } finally {
            try { java.nio.file.Files.deleteIfExists(emptyPath); } catch (Exception ignored) {}
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
